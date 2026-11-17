package io.github.colla69.makemytaxes.app.web;

import io.github.colla69.makemytaxes.app.storage.FormStore;
import io.github.colla69.makemytaxes.core.form.Answers;
import io.github.colla69.makemytaxes.core.form.Field;
import io.github.colla69.makemytaxes.core.form.FormModule;
import io.github.colla69.makemytaxes.core.form.Interview;
import io.github.colla69.makemytaxes.core.form.YearDefinition;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

/**
 * The parts that the questions of the interview and its step "Dokumente hochladen" share: the year
 * definition of a return, the attributes of each interview page, and the save of one field.
 */
@Component
class InterviewContext {

  /** The field of a form that a question or a certificate value writes into. */
  record FieldTarget(String formId, String fieldId) {}

  private final ReturnYears years;
  private final FormStore forms;

  InterviewContext(ReturnYears years, FormStore forms) {
    this.years = years;
    this.forms = forms;
  }

  void addCommon(long returnId, Model model) {
    YearDefinition definition = definitionOf(returnId);
    Interview interview = definition.interview();
    Answers answers = forms.answers(returnId, Interview.ID);
    model.addAttribute("returnId", returnId);
    model.addAttribute("tab", Interview.ID);
    model.addAttribute("taxYear", definition.pack().taxYear().value());
    model.addAttribute("forms", definition.forms());
    model.addAttribute("answeredCount", interview.answeredCount(answers));
    model.addAttribute("questionCount", interview.questions(answers).size());
  }

  /** Writes the value into the form, or returns the error. An empty value clears the field. */
  String saveField(long returnId, FieldTarget target, String value) {
    FormModule form = formOf(returnId, target.formId());
    Field field = form.field(target.fieldId()).orElseThrow();
    Map<String, String> values = new LinkedHashMap<>(forms.answers(returnId, form.id()).values());
    if (value.isEmpty()) {
      values.remove(field.id());
    } else {
      try {
        field.kind().validate(value);
      } catch (IllegalArgumentException e) {
        return e.getMessage();
      }
      values.put(field.id(), value);
    }
    List<String> formErrors = form.check(new Answers(values));
    if (!formErrors.isEmpty()) {
      return String.join(" ", formErrors);
    }
    forms.save(returnId, form.id(), new Answers(values));
    return null;
  }

  FormModule formOf(long returnId, String formId) {
    return years.formOf(returnId, formId);
  }

  YearDefinition definitionOf(long returnId) {
    return years.definitionOf(returnId);
  }

  static String interviewHref(long returnId) {
    return "/returns/" + returnId + "/interview";
  }
}
