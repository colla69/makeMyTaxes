package io.github.colla69.makemytaxes.app.storage;

import io.github.colla69.makemytaxes.core.document.DocumentKind;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
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
