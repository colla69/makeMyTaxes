package io.github.colla69.makemytaxes.app.storage;

import io.github.colla69.makemytaxes.core.form.Answers;
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

  public Answers answers(long returnId, String formId) {
    Map<String, String> values = new LinkedHashMap<>();
    jdbc.sql("select field_id, value from form_answer where tax_return_id = ? and form_id = ?")
        .params(returnId, formId)
        .query(row -> {
          values.put(row.getString("field_id"), row.getString("value"));
        });
    return new Answers(values);
  }

  /** Replaces all answers of the form, so a field that the user cleared loses its old value. */
  @Transactional
  public void save(long returnId, String formId, Answers answers) {
    jdbc.sql("delete from form_answer where tax_return_id = ? and form_id = ?")
        .params(returnId, formId)
        .update();
    answers
        .values()
        .forEach(
            (fieldId, value) ->
                jdbc.sql(
                        "insert into form_answer(tax_return_id, form_id, field_id, value)"
                            + " values (?, ?, ?, ?)")
                    .params(returnId, formId, fieldId, value)
                    .update());
  }
}
