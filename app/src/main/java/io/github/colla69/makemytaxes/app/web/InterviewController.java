package io.github.colla69.makemytaxes.app.web;

import io.github.colla69.makemytaxes.app.storage.FormStore;
import io.github.colla69.makemytaxes.app.storage.HouseholdStore;
import io.github.colla69.makemytaxes.core.TaxYear;
import io.github.colla69.makemytaxes.core.form.Answers;
import io.github.colla69.makemytaxes.core.form.FormModule;
import io.github.colla69.makemytaxes.core.form.Forms;
import io.github.colla69.makemytaxes.core.form.Interview;
import io.github.colla69.makemytaxes.core.form.Interview.Step;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

/** The interview tab: yes/no questions, one at a time, and then the pages that the return needs. */
@Controller
class InterviewController {

  record AnsweredView(String id, String text, String answer) {}

  /** A link to a page of the return. The hint says what to do there. */
  record StepView(String label, String href, String hint) {}

  private final HouseholdStore household;
  private final FormStore forms;

  InterviewController(HouseholdStore household, FormStore forms) {
    this.household = household;
    this.forms = forms;
  }

  @GetMapping("/returns/{returnId}/interview")
  String page(@PathVariable long returnId, Model model) {
    TaxYear taxYear = taxYearWithForms(returnId);
    Answers answers = forms.answers(returnId, Interview.ID);
    model.addAttribute("returnId", returnId);
    model.addAttribute("tab", Interview.ID);
    model.addAttribute("taxYear", taxYear.value());
    model.addAttribute("forms", Forms.of(taxYear));
    model.addAttribute("answered", answered(answers));
    model.addAttribute("next", Interview.next(answers).orElse(null));
    model.addAttribute("steps", steps(returnId, taxYear, answers));
    return "interview";
  }

  /** Saves one answer. An empty answer removes it, so that the question comes again. */
  @PostMapping("/returns/{returnId}/interview")
  String answer(
      @PathVariable long returnId,
      @RequestParam String question,
      @RequestParam(defaultValue = "") String answer) {
    taxYearWithForms(returnId);
    boolean known = Interview.questions().stream().anyMatch(q -> q.id().equals(question));
    if (!known || !List.of(Interview.YES, Interview.NO, "").contains(answer)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    }
    Map<String, String> values =
        new LinkedHashMap<>(forms.answers(returnId, Interview.ID).values());
    if (answer.isEmpty()) {
      values.remove(question);
    } else {
      values.put(question, answer);
    }
    forms.save(returnId, Interview.ID, new Answers(values));
    return "redirect:/returns/" + returnId + "/interview";
  }

  @PostMapping("/returns/{returnId}/interview/restart")
  String restart(@PathVariable long returnId) {
    taxYearWithForms(returnId);
    forms.save(returnId, Interview.ID, new Answers(Map.of()));
    return "redirect:/returns/" + returnId + "/interview";
  }

  private static List<AnsweredView> answered(Answers answers) {
    return Interview.questions().stream()
        .filter(question -> answers.has(question.id()))
        .map(
            question ->
                new AnsweredView(
                    question.id(),
                    question.text(),
                    Interview.YES.equals(answers.text(question.id())) ? "Ja" : "Nein"))
        .toList();
  }

  private static List<StepView> steps(long returnId, TaxYear taxYear, Answers answers) {
    return Interview.steps(answers).stream().map(step -> view(returnId, taxYear, step)).toList();
  }

  private static StepView view(long returnId, TaxYear taxYear, Step step) {
    if (step.isForm()) {
      FormModule form = Forms.find(taxYear, step.formId()).orElseThrow();
      return new StepView(
          form.title(),
          "/returns/" + returnId + "/forms/" + form.id(),
          "Füllen Sie die Felder aus und speichern Sie.");
    }
    return new StepView(
        "Ausgaben",
        "/returns/" + returnId,
        "Erfassen Sie jede Zahlung mit der Kategorie „" + step.topic().label() + "“.");
  }

  /** A year without a year pack has no forms, so the interview could point nowhere. */
  private TaxYear taxYearWithForms(long returnId) {
    TaxYear taxYear =
        household
            .taxYearOf(returnId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    if (Forms.of(taxYear).isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
    return taxYear;
  }
}
