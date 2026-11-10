package io.github.colla69.makemytaxes.app.storage;

import io.github.colla69.makemytaxes.core.form.AnlageN;
import io.github.colla69.makemytaxes.core.form.AnlageVorsorgeaufwand;
import io.github.colla69.makemytaxes.core.form.Answers;
import io.github.colla69.makemytaxes.core.form.Interview;
import io.github.colla69.makemytaxes.core.form.MainForm;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
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
    jdbc.sql("delete from form_answer where tax_return_id = ? and form_id = ?")
        .params(returnId, formId)
        .update();
    answers.values().entrySet().stream()
        .filter(answer -> !answer.getValue().equals(details.get(answer.getKey())))
        .forEach(
            answer ->
                insert(
                    new FormAnswer(returnId, formId, answer.getKey(), answer.getValue()),
                    interview));
  }

  /**
   * Replaces one answer of the form, with the same rules as {@link #save}. An empty value clears
   * the answer. The transaction keeps two saves of the same form from losing each other's value.
   */
  @Transactional
  public void saveAnswer(long returnId, String formId, String fieldId, String value) {
    jdbc.sql("delete from form_answer where tax_return_id = ? and form_id = ? and field_id = ?")
        .params(returnId, formId, fieldId)
        .update();
    if (!value.isEmpty() && !value.equals(personalDetails(returnId, formId).get(fieldId))) {
      insert(new FormAnswer(returnId, formId, fieldId, value), answers(returnId, Interview.ID));
    }
  }

  /** The source of a value of a certificate is the certificate of its employer, if it has one. */
  private void insert(FormAnswer answer, Answers interview) {
    OptionalLong document =
        CERTIFICATE_FORMS.contains(answer.formId())
            ? Interview.certificateDocument(interview, answer.fieldId())
            : OptionalLong.empty();
    jdbc.sql(
            "insert into form_answer"
                + "(tax_return_id, form_id, field_id, value, document_id, own_statement)"
                + " values (?, ?, ?, ?, ?, ?)")
        .params(
            answer.returnId(),
            answer.formId(),
            answer.fieldId(),
            answer.value(),
            document.isPresent() ? document.getAsLong() : null,
            document.isPresent() ? 0 : 1)
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
