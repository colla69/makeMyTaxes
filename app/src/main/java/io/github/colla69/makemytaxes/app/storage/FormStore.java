package io.github.colla69.makemytaxes.app.storage;

import io.github.colla69.makemytaxes.core.form.AnlageN;
import io.github.colla69.makemytaxes.core.form.AnlageVorsorgeaufwand;
import io.github.colla69.makemytaxes.core.form.Answers;
import io.github.colla69.makemytaxes.core.form.Interview;
import io.github.colla69.makemytaxes.core.form.MainForm;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class FormStore {

  /** The forms with values from the Lohnsteuerbescheinigung. */
  private static final List<String> CERTIFICATE_FORMS =
      List.of(AnlageN.ID, AnlageVorsorgeaufwand.ID);

  private record FormAnswer(long returnId, String formId, String fieldId, String value) {}

  private final JdbcClient jdbc;

  FormStore(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * The saved answers of the form. The main form starts with the personal details of the person,
   * and a saved answer that differs from a detail replaces it.
   */
  public Answers answers(long returnId, String formId) {
    Map<String, String> values = new LinkedHashMap<>(personalDetails(returnId, formId));
    jdbc.sql("select field_id, value from form_answer where tax_return_id = ? and form_id = ?")
        .params(returnId, formId)
        .query(row -> {
          values.put(row.getString("field_id"), row.getString("value"));
        });
    return new Answers(values);
  }

  /**
   * Replaces all answers of the form, so a field that the user cleared loses its old value. An
   * answer that is equal to a personal detail is not saved, so a later change of the detail
   * reaches this form. A new interview answer can change the certificate of an employer, so the
   * values of the forms take their source again.
   */
  @Transactional
  public void save(long returnId, String formId, Answers answers) {
    replace(returnId, formId, answers);
    if (formId.equals(Interview.ID)) {
      for (String form : CERTIFICATE_FORMS) {
        replace(returnId, form, answers(returnId, form));
      }
    }
  }

  private void replace(long returnId, String formId, Answers answers) {
    Map<String, String> details = personalDetails(returnId, formId);
    Answers interview = answers(returnId, Interview.ID);
    Map<String, AnswerSource> confirmed = confirmedSources(returnId, formId);
    jdbc.sql("delete from form_answer where tax_return_id = ? and form_id = ?")
        .params(returnId, formId)
        .update();
    answers.values().entrySet().stream()
        .filter(answer -> !answer.getValue().equals(details.get(answer.getKey())))
        .map(answer -> new FormAnswer(returnId, formId, answer.getKey(), answer.getValue()))
        .forEach(answer -> insert(answer, sourceOf(answer, interview, confirmed)));
  }

  /**
   * Replaces one answer of the form, with the same rules as {@link #save}. An empty value clears
   * the answer. The transaction keeps two saves of the same form from losing each other's value.
   */
  @Transactional
  public void saveAnswer(long returnId, String formId, String fieldId, String value) {
    Map<String, AnswerSource> confirmed = confirmedSources(returnId, formId);
    jdbc.sql("delete from form_answer where tax_return_id = ? and form_id = ? and field_id = ?")
        .params(returnId, formId, fieldId)
        .update();
    if (!value.isEmpty() && !value.equals(personalDetails(returnId, formId).get(fieldId))) {
      FormAnswer answer = new FormAnswer(returnId, formId, fieldId, value);
      insert(answer, sourceOf(answer, answers(returnId, Interview.ID), confirmed));
    }
  }

  /**
   * An answer with a known source.
   *
   * @param documentId the document that the value comes from, or null for the user's own statement
   */
  public record SourcedAnswer(String formId, String fieldId, String value, Long documentId) {}

  /**
   * Replaces one answer of the form with a value whose source the user confirmed, such as a value
   * from a proposal of the assistant. The value keeps this source while it stays the same, also
   * through a later save of the form or of the interview. A value that is equal to a personal
   * detail is not saved, as in {@link #saveAnswer}.
   */
  @Transactional
  public void saveSourcedAnswer(long returnId, SourcedAnswer answer) {
    jdbc.sql("delete from form_answer where tax_return_id = ? and form_id = ? and field_id = ?")
        .params(returnId, answer.formId(), answer.fieldId())
        .update();
    if (!answer.value().equals(personalDetails(returnId, answer.formId()).get(answer.fieldId()))) {
      insert(
          new FormAnswer(returnId, answer.formId(), answer.fieldId(), answer.value()),
          new AnswerSource(answer.value(), Proof.of(answer.documentId()), true));
    }
  }

  /**
   * The evidence of a saved answer: a document, the user's own statement, or none. An answer has no
   * evidence after the user deleted its document anyway.
   *
   * @param documentId the document, or null
   * @param ownStatement true if the user stated the value
   */
  private record Proof(Long documentId, boolean ownStatement) {

    /** A document, or else the user's own statement. */
    static Proof of(Long documentId) {
      return new Proof(documentId, documentId == null);
    }

    /** True if the value has no evidence, because the user deleted its document. */
    boolean missing() {
      return documentId == null && !ownStatement;
    }
  }

  /**
   * The source of a saved answer.
   *
   * @param value the value that the source belongs to
   * @param proof the evidence of the value
   * @param confirmed true if the user confirmed this source for this value
   */
  private record AnswerSource(String value, Proof proof, boolean confirmed) {}

  /**
   * A value keeps the source that the user confirmed for it. Otherwise the source of a value of a
   * certificate is the certificate of its employer, if it has one. A value that lost its document
   * keeps that state until the user names a new certificate for it.
   */
  private static AnswerSource sourceOf(
      FormAnswer answer, Answers interview, Map<String, AnswerSource> confirmed) {
    OptionalLong document =
        CERTIFICATE_FORMS.contains(answer.formId())
            ? Interview.certificateDocument(interview, answer.fieldId())
            : OptionalLong.empty();
    AnswerSource kept = confirmed.get(answer.fieldId());
    boolean replacedByCertificate = kept != null && kept.proof().missing() && document.isPresent();
    if (kept != null && kept.value().equals(answer.value()) && !replacedByCertificate) {
      return kept;
    }
    return new AnswerSource(
        answer.value(), Proof.of(document.isPresent() ? document.getAsLong() : null), false);
  }

  /** The confirmed sources of the saved answers of the form, by field. */
  private Map<String, AnswerSource> confirmedSources(long returnId, String formId) {
    Map<String, AnswerSource> sources = new LinkedHashMap<>();
    jdbc.sql(
            "select field_id, value, document_id, own_statement from form_answer"
                + " where tax_return_id = ? and form_id = ? and confirmed_source = 1")
        .params(returnId, formId)
        .query(row -> {
          long document = row.getLong("document_id");
          var proof =
              new Proof(row.wasNull() ? null : document, row.getInt("own_statement") == 1);
          sources.put(
              row.getString("field_id"), new AnswerSource(row.getString("value"), proof, true));
        });
    return sources;
  }

  private void insert(FormAnswer answer, AnswerSource source) {
    jdbc.sql(
            "insert into form_answer"
                + "(tax_return_id, form_id, field_id, value, document_id, own_statement,"
                + " confirmed_source) values (?, ?, ?, ?, ?, ?, ?)")
        .params(
            answer.returnId(),
            answer.formId(),
            answer.fieldId(),
            answer.value(),
            source.proof().documentId(),
            source.proof().ownStatement() ? 1 : 0,
            source.confirmed() ? 1 : 0)
        .update();
  }

  /**
   * The document of each saved answer of the form that comes from a document. An answer that is
   * not in the map is the user's own statement.
   */
  public Map<String, Long> answerDocuments(long returnId, String formId) {
    Map<String, Long> documents = new LinkedHashMap<>();
    jdbc.sql(
            "select field_id, document_id from form_answer"
                + " where tax_return_id = ? and form_id = ? and document_id is not null")
        .params(returnId, formId)
        .query(row -> {
          documents.put(row.getString("field_id"), row.getLong("document_id"));
        });
    return documents;
  }

  /**
   * The fields of the saved answers of the form that have no evidence, because the user deleted
   * their document anyway. Each keeps its value and this state while the value stays the same.
   */
  public Set<String> answersWithoutEvidence(long returnId, String formId) {
    return new LinkedHashSet<>(
        jdbc.sql(
                "select field_id from form_answer where tax_return_id = ? and form_id = ?"
                    + " and document_id is null and own_statement = 0")
            .params(returnId, formId)
            .query(String.class)
            .list());
  }

  /** The personal details of the person of the return. Only the main form has them. */
  private Map<String, String> personalDetails(long returnId, String formId) {
    Map<String, String> details = new LinkedHashMap<>();
    if (formId.equals(MainForm.ID)) {
      jdbc.sql(
              "select d.field_id, d.value from person_detail d"
                  + " join tax_return r on r.person_id = d.person_id where r.id = ?")
          .param(returnId)
          .query(row -> {
            details.put(row.getString("field_id"), row.getString("value"));
          });
    }
    return details;
  }
}
