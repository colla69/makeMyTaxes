package io.github.colla69.makemytaxes.app.storage;

import io.github.colla69.makemytaxes.core.form.Answers;
import io.github.colla69.makemytaxes.core.form.MainForm;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class FormStore {

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
   * reaches this form.
   */
  @Transactional
  public void save(long returnId, String formId, Answers answers) {
    Map<String, String> details = personalDetails(returnId, formId);
    jdbc.sql("delete from form_answer where tax_return_id = ? and form_id = ?")
        .params(returnId, formId)
        .update();
    answers.values().entrySet().stream()
        .filter(answer -> !answer.getValue().equals(details.get(answer.getKey())))
        .forEach(
            answer ->
                jdbc.sql(
                        "insert into form_answer(tax_return_id, form_id, field_id, value)"
                            + " values (?, ?, ?, ?)")
                    .params(returnId, formId, answer.getKey(), answer.getValue())
                    .update());
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
