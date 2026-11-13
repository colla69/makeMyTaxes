package io.github.colla69.makemytaxes.app.storage;

import io.github.colla69.makemytaxes.core.document.DocumentKind;
import io.github.colla69.makemytaxes.core.form.Interview;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stores the documents of a return. The bytes are in their own table: a list reads only the
 * details, and only the view of one document reads its bytes.
 */
@Repository
public class DocumentStore {

  public record NewDocument(DocumentKind kind, String fileName, String mediaType, byte[] bytes) {}

  public record StoredDocument(
      long id,
      DocumentKind kind,
      String fileName,
      String mediaType,
      long sizeBytes,
      String sha256,
      LocalDate uploadedOn) {}

  private static final String DETAILS =
      "select id, kind, file_name, media_type, size_bytes, sha256, uploaded_on from document";

  private final JdbcClient jdbc;

  DocumentStore(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional
  public long add(long returnId, NewDocument document, LocalDate uploadedOn) {
    jdbc.sql(
            """
            insert into document(
              tax_return_id, kind, file_name, media_type, size_bytes, sha256, uploaded_on)
            values (?, ?, ?, ?, ?, ?, ?)
            """)
        .params(
            returnId,
            document.kind().name(),
            document.fileName(),
            document.mediaType(),
            document.bytes().length,
            sha256(document.bytes()),
            uploadedOn.toString())
        .update();
    long documentId = jdbc.sql("select last_insert_rowid()").query(Long.class).single();
    jdbc.sql("insert into document_content(document_id, bytes) values (?, ?)")
        .params(documentId, document.bytes())
        .update();
    return documentId;
  }

  /** A payslip and the month that it is for. */
  public record NewPayslip(Month month, NewDocument document) {}

  /** A stored payslip: its month, and the ID and the file name of its document. */
  public record StoredPayslip(Month month, long documentId, String fileName) {}

  /**
   * Stores a payslip as a document of the return. A new payslip for a month replaces the old one
   * as the payslip of that month. The old document stays, because an expense can link to it.
   */
  @Transactional
  public long addPayslip(long returnId, NewPayslip payslip, LocalDate uploadedOn) {
    jdbc.sql("delete from payslip where tax_return_id = ? and month = ?")
        .params(returnId, payslip.month().getValue())
        .update();
    long documentId = add(returnId, payslip.document(), uploadedOn);
    jdbc.sql("insert into payslip(document_id, tax_return_id, month) values (?, ?, ?)")
        .params(documentId, returnId, payslip.month().getValue())
        .update();
    return documentId;
  }

  public List<StoredPayslip> payslipsOf(long returnId) {
    return jdbc.sql(
            """
            select p.month, d.id, d.file_name
            from payslip p join document d on d.id = p.document_id
            where p.tax_return_id = ?
            order by p.month
            """)
        .param(returnId)
        .query(
            (row, rowNumber) ->
                new StoredPayslip(
                    Month.of(row.getInt("month")), row.getLong("id"), row.getString("file_name")))
        .list();
  }

  public static String monthName(Month month) {
    return month.getDisplayName(TextStyle.FULL, Locale.GERMAN);
  }

  public List<StoredDocument> documentsOf(long returnId) {
    return jdbc.sql(DETAILS + " where tax_return_id = ? order by uploaded_on desc, id desc")
        .param(returnId)
        .query((row, rowNumber) -> detailsOf(row))
        .list();
  }

  /** The return id is part of the condition, so one return cannot open a document of another. */
  public Optional<StoredDocument> find(long returnId, long documentId) {
    return jdbc.sql(DETAILS + " where id = ? and tax_return_id = ?")
        .params(documentId, returnId)
        .query((row, rowNumber) -> detailsOf(row))
        .optional();
  }

  /**
   * The bytes of one document. The driver reads a BLOB into memory in any case. The caller writes
   * the bytes after this method returns, so a slow browser does not hold the one connection.
   */
  public byte[] content(long documentId) {
    return jdbc.sql("select bytes from document_content where document_id = ?")
        .param(documentId)
        .query(byte[].class)
        .single();
  }

  /** An expense that names the document as its evidence. */
  public record ExpenseUse(LocalDate paidOn, String description) {}

  /** A form answer whose value comes from the document. */
  public record AnswerUse(String formId, String fieldId) {}

  /** The entries of the return that use the document as their evidence. */
  public record Uses(List<ExpenseUse> expenses, List<AnswerUse> answers) {

    public boolean isEmpty() {
      return expenses.isEmpty() && answers.isEmpty();
    }
  }

  public Uses usesOf(long returnId, long documentId) {
    List<ExpenseUse> expenses =
        jdbc.sql(
                "select paid_on, description from expense"
                    + " where tax_return_id = ? and document_id = ? order by paid_on, id")
            .params(returnId, documentId)
            .query(
                (row, rowNumber) ->
                    new ExpenseUse(
                        LocalDate.parse(row.getString("paid_on")), row.getString("description")))
            .list();
    List<AnswerUse> answers =
        jdbc.sql(
                "select form_id, field_id from form_answer"
                    + " where tax_return_id = ? and document_id = ? order by form_id, field_id")
            .params(returnId, documentId)
            .query(
                (row, rowNumber) ->
                    new AnswerUse(row.getString("form_id"), row.getString("field_id")))
            .list();
    return new Uses(expenses, answers);
  }

  /**
   * Deletes the document of the return and its bytes. The caller asks the user first if an entry
   * uses it. Such an entry keeps its value and loses its evidence, so the app shows that its
   * evidence is missing. A form answer keeps this state while its value stays the same, so it is a
   * confirmed source. A payslip month and a waiting proposal of the assistant go with the
   * document, because they have no meaning without it. The return id is part of each condition, so
   * one return cannot delete a document of another.
   */
  @Transactional
  public void delete(long returnId, long documentId) {
    if (find(returnId, documentId).isEmpty()) {
      return;
    }
    jdbc.sql("update expense set document_id = null where tax_return_id = ? and document_id = ?")
        .params(returnId, documentId)
        .update();
    jdbc.sql(
            "update form_answer set document_id = null, own_statement = 0, confirmed_source = 1"
                + " where tax_return_id = ? and document_id = ?")
        .params(returnId, documentId)
        .update();
    // The interview names the certificate of an employer by its id, so it forgets the certificate.
    jdbc.sql(
            "delete from form_answer where tax_return_id = ? and form_id = ? and value = ?"
                + " and (field_id = ? or field_id like ?)")
        .params(
            returnId,
            Interview.ID,
            String.valueOf(documentId),
            Interview.WAGE_CERTIFICATE_DOCUMENT,
            "%-" + Interview.WAGE_CERTIFICATE_DOCUMENT)
        .update();
    jdbc.sql("delete from field_proposal where tax_return_id = ? and document_id = ?")
        .params(returnId, documentId)
        .update();
    jdbc.sql("delete from payslip where tax_return_id = ? and document_id = ?")
        .params(returnId, documentId)
        .update();
    jdbc.sql("delete from document_content where document_id = ?").param(documentId).update();
    jdbc.sql("delete from document where id = ? and tax_return_id = ?")
        .params(documentId, returnId)
        .update();
  }

  private static StoredDocument detailsOf(ResultSet row) throws SQLException {
    return new StoredDocument(
        row.getLong("id"),
        DocumentKind.valueOf(row.getString("kind")),
        row.getString("file_name"),
        row.getString("media_type"),
        row.getLong("size_bytes"),
        row.getString("sha256"),
        LocalDate.parse(row.getString("uploaded_on")));
  }

  private static String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("Every Java runtime has SHA-256.", e);
    }
  }
}
