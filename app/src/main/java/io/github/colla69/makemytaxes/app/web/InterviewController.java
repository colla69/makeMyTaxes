package io.github.colla69.makemytaxes.app.web;

import io.github.colla69.makemytaxes.app.storage.FormStore;
import io.github.colla69.makemytaxes.app.storage.HouseholdStore;
import io.github.colla69.makemytaxes.core.TaxYear;
import io.github.colla69.makemytaxes.core.form.Answers;
import io.github.colla69.makemytaxes.core.form.Field;
import io.github.colla69.makemytaxes.core.form.FormModule;
import io.github.colla69.makemytaxes.core.form.Forms;
import io.github.colla69.makemytaxes.core.form.Interview;
import io.github.colla69.makemytaxes.core.form.Interview.Question;
import io.github.colla69.makemytaxes.core.form.Interview.Step;
import io.github.colla69.makemytaxes.core.form.Interview.Topic;
import io.github.colla69.makemytaxes.core.form.Interview.Type;
import io.github.colla69.makemytaxes.core.form.MainForm;
import java.util.Arrays;
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

/**
 * The interview tab: questions by life topic, one per screen, with a summary after each topic.
 * A field question reads and writes the field of its form, so the form tab and the interview show
 * the same value.
 */
@Controller
class InterviewController {

  record AnsweredView(String id, String text, String answer) {}

  record TopicView(String name, String label, List<AnsweredView> answers) {}

  record DetailView(String label, String value) {}

  /** A link to a page of the return. The hint says what to do there. */
  record StepView(String label, String href, String hint) {}

  private final HouseholdStore household;
  private final FormStore forms;

  InterviewController(HouseholdStore household, FormStore forms) {
    this.household = household;
    this.forms = forms;
  }

  /** The first open question, or the summary of all topics when each question has an answer. */
  @GetMapping("/returns/{returnId}/interview")
  String page(@PathVariable long returnId, Model model) {
    TaxYear taxYear = taxYearWithForms(returnId);
    Answers answers = forms.answers(returnId, Interview.ID);
    var next = Interview.next(answers);
    if (next.isPresent()) {
      return questionScreen(returnId, next.get(), model);
    }
    addCommon(returnId, model);
    model.addAttribute(
        "topics", Arrays.stream(Topic.values()).map(topic -> topicView(returnId, topic)).toList());
    model.addAttribute("steps", steps(returnId, taxYear, answers));
    return "interview";
  }

  @GetMapping("/returns/{returnId}/interview/questions/{questionId}")
  String question(
      @PathVariable long returnId,
      @PathVariable String questionId,
      @RequestParam(defaultValue = "false") boolean saved,
      Model model) {
    taxYearWithForms(returnId);
    model.addAttribute("saved", saved);
    return questionScreen(returnId, questionOf(questionId), model);
  }

  @GetMapping("/returns/{returnId}/interview/topics/{topic}")
  String topic(
      @PathVariable long returnId,
      @PathVariable String topic,
      @RequestParam(defaultValue = "false") boolean saved,
      Model model) {
    taxYearWithForms(returnId);
    model.addAttribute("saved", saved);
    Topic shown = topicOf(topic);
    addCommon(returnId, model);
    model.addAttribute("summary", topicView(returnId, shown));
    model.addAttribute("back", questionHref(returnId, Interview.questionsOf(shown).getLast()));
    model.addAttribute(
        "forward",
        Interview.nextTopic(shown)
            .map(nextTopic -> questionHref(returnId, Interview.questionsOf(nextTopic).getFirst()))
            .orElse(interviewHref(returnId)));
    return "interview";
  }

  /**
   * Saves one answer and shows the next question of the topic, or the summary of the topic after
   * its last question. The answer to a field question goes into its form.
   */
  @PostMapping("/returns/{returnId}/interview/questions/{questionId}")
  String answer(
      @PathVariable long returnId,
      @PathVariable String questionId,
      @RequestParam(defaultValue = "") String answer,
      Model model) {
    taxYearWithForms(returnId);
    Question question = questionOf(questionId);
    if (question.type() == Type.YES_NO && !List.of(Interview.YES, Interview.NO).contains(answer)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    }
    if (question.type() == Type.FIELD) {
      String error = saveField(returnId, question, answer.strip());
      if (error != null) {
        model.addAttribute("value", answer);
        model.addAttribute("fieldError", error);
        return questionScreen(returnId, question, model);
      }
    }
    Map<String, String> values = new LinkedHashMap<>(forms.answers(returnId, Interview.ID).values());
    values.put(question.id(), question.type() == Type.YES_NO ? answer : Interview.DONE);
    forms.save(returnId, Interview.ID, new Answers(values));
    // The next page tells fields.js that a form changed, so the drafts of the form tabs are old.
    String saved = question.type() == Type.FIELD ? "?saved=true" : "";
    return "redirect:"
        + Interview.nextInTopic(question)
            .map(next -> questionHref(returnId, next))
            .orElse(topicHref(returnId, question.topic()))
        + saved;
  }

  /** Clears the interview answers. The values in the forms stay. */
  @PostMapping("/returns/{returnId}/interview/restart")
  String restart(@PathVariable long returnId) {
    taxYearWithForms(returnId);
    forms.save(returnId, Interview.ID, new Answers(Map.of()));
    return "redirect:" + interviewHref(returnId);
  }

  /** Writes the value into the form, or returns the error. An empty value clears the field. */
  private String saveField(long returnId, Question question, String value) {
    FormModule form = formOf(returnId, question.formId());
    Field field = fieldOf(form, question.id());
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

  private String questionScreen(long returnId, Question question, Model model) {
    addCommon(returnId, model);
    List<Question> inTopic = Interview.questionsOf(question.topic());
    model.addAttribute("question", question);
    model.addAttribute("topicLabel", question.topic().label());
    model.addAttribute("position", inTopic.indexOf(question) + 1);
    model.addAttribute("positions", inTopic.size());
    model.addAttribute("back", backHref(returnId, question));
    model.addAttribute("current", forms.answers(returnId, Interview.ID).text(question.id()));
    if (question.type() == Type.FIELD) {
      FormModule form = formOf(returnId, question.formId());
      model.addAttribute("field", fieldOf(form, question.id()));
      if (!model.containsAttribute("value")) {
        model.addAttribute("value", forms.answers(returnId, form.id()).text(question.id()));
      }
    }
    if (question.type() == Type.CONFIRM) {
      Answers main = forms.answers(returnId, MainForm.ID);
      model.addAttribute(
          "details",
          new MainForm()
              .personalFields().stream()
                  .map(field -> new DetailView(field.label(), main.text(field.id())))
                  .toList());
      model.addAttribute(
          "personHref",
          household.personOf(returnId).map(person -> "/persons/" + person).orElse("/"));
    }
    return "interview";
  }

  private void addCommon(long returnId, Model model) {
    TaxYear taxYear = taxYearWithForms(returnId);
    model.addAttribute("returnId", returnId);
    model.addAttribute("tab", Interview.ID);
    model.addAttribute("taxYear", taxYear.value());
    model.addAttribute("forms", Forms.of(taxYear));
    model.addAttribute(
        "answeredCount", Interview.answeredCount(forms.answers(returnId, Interview.ID)));
    model.addAttribute("questionCount", Interview.questions().size());
  }

  private TopicView topicView(long returnId, Topic topic) {
    Answers answers = forms.answers(returnId, Interview.ID);
    List<AnsweredView> views =
        Interview.questionsOf(topic).stream()
            .map(question -> new AnsweredView(question.id(), question.text(), shown(returnId, question, answers)))
            .toList();
    return new TopicView(topic.name(), topic.label(), views);
  }

  /** The answer as the summary shows it. A field shows the value that its form holds now. */
  private String shown(long returnId, Question question, Answers answers) {
    if (!answers.has(question.id())) {
      return "Noch offen";
    }
    return switch (question.type()) {
      case YES_NO -> Interview.YES.equals(answers.text(question.id())) ? "Ja" : "Nein";
      case CONFIRM -> "Bestätigt";
      case FIELD -> {
        String value = forms.answers(returnId, question.formId()).text(question.id());
        Field field = fieldOf(formOf(returnId, question.formId()), question.id());
        yield value.isEmpty() ? "Keine Angabe" : field.kind().display(value);
      }
    };
  }

  private static String backHref(long returnId, Question question) {
    return Interview.previousInTopic(question)
        .map(previous -> questionHref(returnId, previous))
        .or(() -> Interview.previousTopic(question.topic()).map(topic -> topicHref(returnId, topic)))
        .orElse(null);
  }

  private static String interviewHref(long returnId) {
    return "/returns/" + returnId + "/interview";
  }

  private static String questionHref(long returnId, Question question) {
    return interviewHref(returnId) + "/questions/" + question.id();
  }

  private static String topicHref(long returnId, Topic topic) {
    return interviewHref(returnId) + "/topics/" + topic.name();
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

  private static Question questionOf(String questionId) {
    return Interview.find(questionId).orElseThrow(InterviewController::notFound);
  }

  private static Topic topicOf(String name) {
    return Arrays.stream(Topic.values())
        .filter(topic -> topic.name().equals(name))
        .findFirst()
        .orElseThrow(InterviewController::notFound);
  }

  private static Field fieldOf(FormModule form, String fieldId) {
    return form.fields().stream()
        .filter(field -> field.id().equals(fieldId))
        .findFirst()
        .orElseThrow();
  }

  private FormModule formOf(long returnId, String formId) {
    return Forms.find(taxYearWithForms(returnId), formId).orElseThrow();
  }

  /** A year without a year pack has no forms, so the interview could point nowhere. */
  private TaxYear taxYearWithForms(long returnId) {
    TaxYear taxYear = household.taxYearOf(returnId).orElseThrow(InterviewController::notFound);
    if (Forms.of(taxYear).isEmpty()) {
      throw notFound();
    }
    return taxYear;
  }

  private static ResponseStatusException notFound() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND);
  }
}
