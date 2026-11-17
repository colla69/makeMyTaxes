package io.github.colla69.makemytaxes.app.web;

import static io.github.colla69.makemytaxes.app.web.InterviewContext.interviewHref;

import io.github.colla69.makemytaxes.app.storage.DocumentStore;
import io.github.colla69.makemytaxes.app.storage.DocumentStore.StoredDocument;
import io.github.colla69.makemytaxes.app.storage.FormStore;
import io.github.colla69.makemytaxes.app.storage.HouseholdStore;
import io.github.colla69.makemytaxes.app.web.InterviewContext.FieldTarget;
import io.github.colla69.makemytaxes.core.TaxYear;
import io.github.colla69.makemytaxes.core.document.DocumentKind;
import io.github.colla69.makemytaxes.core.form.AnlageVorsorgeaufwand;
import io.github.colla69.makemytaxes.core.form.Answers;
import io.github.colla69.makemytaxes.core.form.CertificateValues;
import io.github.colla69.makemytaxes.core.form.Field;
import io.github.colla69.makemytaxes.core.form.FormModule;
import io.github.colla69.makemytaxes.core.form.Forms;
import io.github.colla69.makemytaxes.core.form.Interview;
import io.github.colla69.makemytaxes.core.form.Interview.Question;
import io.github.colla69.makemytaxes.core.form.Interview.Step;
import io.github.colla69.makemytaxes.core.form.Interview.Topic;
import io.github.colla69.makemytaxes.core.form.Interview.Type;
import io.github.colla69.makemytaxes.core.form.Kind;
import io.github.colla69.makemytaxes.core.form.MainForm;
import io.github.colla69.makemytaxes.core.form.Source;
import io.github.colla69.makemytaxes.core.form.YearDefinition;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * The interview tab: questions by life topic, one per screen, with a summary after each topic.
 * A field question reads and writes the field of its form, so the form tab and the interview show
 * the same value.
 */
@Controller
class InterviewController {

  /** One answer of the summary. The source is null for an answer that is not a field value. */
  record AnsweredView(String id, String hintId, String text, String answer, String source) {}

  record TopicView(String name, String label, List<AnsweredView> answers) {}

  record DetailView(String label, String value) {}

  /** A link to a page of the return. The hint says what to do there. */
  record StepView(String label, String href, String hint) {}

  /** The answer to the question for the certificate: a new file, or a document of the return. */
  record CertificateChoice(Long documentId, MultipartFile file) {}

  private final InterviewContext context;
  private final InterviewDocuments certificates;
  private final HouseholdStore household;
  private final FormStore forms;
  private final DocumentStore documents;

  InterviewController(
      InterviewContext context,
      InterviewDocuments certificates,
      HouseholdStore household,
      FormStore forms,
      DocumentStore documents) {
    this.context = context;
    this.certificates = certificates;
    this.household = household;
    this.forms = forms;
    this.documents = documents;
  }

  /** The first open question, or the summary of all topics when each question has an answer. */
  @GetMapping("/returns/{returnId}/interview")
  String page(@PathVariable long returnId, Model model) {
    YearDefinition definition = context.definitionOf(returnId);
    TaxYear taxYear = definition.pack().taxYear();
    Interview interview = definition.interview();
    Answers answers = forms.answers(returnId, Interview.ID);
    // Only a new interview starts with the upload. One in progress goes on with its questions.
    if (answers.values().isEmpty()) {
      return certificates.documentsScreen(returnId, model);
    }
    var next = interview.next(answers);
    if (next.isPresent()) {
      return questionScreen(returnId, next.get(), model);
    }
    context.addCommon(returnId, model);
    model.addAttribute(
        "topics", Arrays.stream(Topic.values()).map(topic -> topicView(returnId, topic)).toList());
    model.addAttribute(
        "steps",
        interview.steps(answers).stream().map(step -> view(returnId, taxYear, step)).toList());
    return "interview";
  }

  @GetMapping("/returns/{returnId}/interview/questions/{questionId}")
  String question(
      @PathVariable long returnId,
      @PathVariable String questionId,
      @RequestParam(defaultValue = "false") boolean saved,
      Model model) {
    model.addAttribute("saved", saved);
    return questionScreen(returnId, questionOf(returnId, questionId), model);
  }

  @GetMapping("/returns/{returnId}/interview/topics/{topic}")
  String topic(
      @PathVariable long returnId,
      @PathVariable String topic,
      @RequestParam(defaultValue = "false") boolean saved,
      Model model) {
    Interview interview = interviewOf(returnId);
    model.addAttribute("saved", saved);
    Topic shown = topicOf(topic);
    Answers answers = forms.answers(returnId, Interview.ID);
    context.addCommon(returnId, model);
    model.addAttribute("summary", topicView(returnId, shown));
    model.addAttribute(
        "back", questionHref(returnId, interview.questionsOf(answers, shown).getLast()));
    model.addAttribute(
        "forward",
        Interview.nextTopic(shown)
            .map(
                nextTopic ->
                    questionHref(returnId, interview.questionsOf(answers, nextTopic).getFirst()))
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
    Question question = questionOf(returnId, questionId);
    boolean hasButtons = !question.options().isEmpty();
    if (question.type() == Type.DOCUMENT || (hasButtons && !question.allows(answer))) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    }
    String recorded = hasButtons ? answer : Interview.DONE;
    if (question.type() == Type.CONFIRM) {
      List<String> missing = missingDetails(returnId);
      if (!missing.isEmpty()) {
        model.addAttribute(
            "confirmError", "Bei der Person fehlen noch: " + String.join(", ", missing) + ".");
        return questionScreen(returnId, question, model);
      }
    }
    if (question.type() == Type.FIELD) {
      boolean changed = changes(returnId, question, answer.strip());
      String error = requiredError(returnId, question, answer.strip());
      if (error == null) {
        error = context.saveField(returnId, targetOf(question), answer.strip());
      }
      if (error != null) {
        model.addAttribute("value", answer);
        model.addAttribute("fieldError", error);
        return questionScreen(returnId, question, model);
      }
      recorded = fieldAnswer(returnId, question, changed);
    }
    Answers answers = record(returnId, question, recorded);
    if (question.id().equals(Interview.HEALTH_INSURANCE)) {
      Answers lines = forms.answers(returnId, AnlageVorsorgeaufwand.ID);
      forms.save(
          returnId,
          AnlageVorsorgeaufwand.ID,
          AnlageVorsorgeaufwand.forHealthInsurance(lines, Interview.PRIVATE.equals(answer)));
    }
    String saved = question.type() == Type.FIELD ? "?saved=true" : "";
    return "redirect:" + nextHref(returnId, answers, question) + saved;
  }

  /**
   * Saves the certificate of an employer: a new file wins over a chosen document. The file comes
   * as a request parameter, because only then Spring reports a file over the server limit as
   * {@link MaxUploadSizeExceededException}.
   */
  @PostMapping("/returns/{returnId}/interview/questions/{questionId}/document")
  String chooseCertificate(
      @PathVariable long returnId,
      @PathVariable String questionId,
      @RequestParam(required = false) MultipartFile file,
      @RequestParam(required = false) Long documentId,
      Model model)
      throws IOException {
    Question question = questionOf(returnId, questionId);
    if (question.type() != Type.DOCUMENT) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    }
    long certificate;
    try {
      certificate = certificateOf(returnId, new CertificateChoice(documentId, file));
    } catch (IllegalArgumentException e) {
      model.addAttribute("documentError", e.getMessage());
      return questionScreen(returnId, question, model);
    }
    Answers answers = record(returnId, question, Long.toString(certificate));
    return "redirect:" + nextHref(returnId, answers, question);
  }

  /** The server stops a file over its own limit before the method for the document runs. */
  @ExceptionHandler(MaxUploadSizeExceededException.class)
  String tooLarge(HttpServletRequest request, Model model) {
    long returnId = DocumentController.returnIdOf(request);
    Question question =
        questionOf(returnId, DocumentController.pathVariable(request, "questionId"));
    model.addAttribute("documentError", DocumentController.TOO_LARGE);
    return questionScreen(returnId, question, model);
  }

  private long certificateOf(long returnId, CertificateChoice choice) throws IOException {
    if (choice.file() != null && !choice.file().isEmpty()) {
      return documents.add(
          returnId,
          DocumentController.toNewDocument(DocumentKind.WAGE_TAX_CERTIFICATE, choice.file()),
          LocalDate.now());
    }
    return Optional.ofNullable(choice.documentId())
        .flatMap(id -> documents.find(returnId, id))
        .filter(document -> document.kind() == DocumentKind.WAGE_TAX_CERTIFICATE)
        .map(StoredDocument::id)
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Bitte wählen Sie eine Lohnsteuerbescheinigung, oder laden Sie eine hoch."));
  }

  private Answers record(long returnId, Question question, String value) {
    Map<String, String> values = new LinkedHashMap<>(forms.answers(returnId, Interview.ID).values());
    values.put(question.id(), value);
    Answers answers = new Answers(values);
    forms.save(returnId, Interview.ID, answers);
    return answers;
  }

  /**
   * The new answer can add questions, such as those of another employer. A question that a
   * document answered is skipped. The summary of the topic still shows it.
   */
  private String nextHref(long returnId, Answers answers, Question question) {
    Interview interview = interviewOf(returnId);
    Set<String> prefilled = Interview.prefilled(answers);
    Optional<Question> next = interview.nextInTopic(answers, question);
    while (next.isPresent() && prefilled.contains(next.get().id())) {
      next = interview.nextInTopic(answers, next.get());
    }
    return next.map(found -> questionHref(returnId, found))
        .orElse(topicHref(returnId, question.topic()));
  }

  /**
   * Saves the value of a field question when the user leaves the field. The question stays open
   * until the user goes on with "Weiter". The answer is empty, or it holds the error.
   */
  @PostMapping("/returns/{returnId}/interview/questions/{questionId}/field")
  @ResponseBody
  ResponseEntity<String> saveFieldAnswer(
      @PathVariable long returnId,
      @PathVariable String questionId,
      @RequestParam(defaultValue = "") String value) {
    Question question = questionOf(returnId, questionId);
    if (question.type() != Type.FIELD) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    }
    boolean changed = changes(returnId, question, value.strip());
    String error = context.saveField(returnId, targetOf(question), value.strip());
    if (error != null) {
      return ResponseEntity.status(422).body(error);
    }
    if (fieldAnswer(returnId, question, changed).equals(Interview.OWN)) {
      record(returnId, question, Interview.OWN);
    }
    return ResponseEntity.ok("");
  }

  /**
   * True if the value differs from the value that the form holds now. The comparison uses the
   * kind of the field, so 24000 and 24.000,00 are the same amount.
   */
  private boolean changes(long returnId, Question question, String value) {
    String saved = forms.answers(returnId, question.formId()).text(question.id());
    if (saved.isEmpty() || value.isEmpty()) {
      return !saved.equals(value);
    }
    Kind kind = context.formOf(returnId, question.formId()).field(question.id()).orElseThrow().kind();
    try {
      kind.validate(value);
    } catch (IllegalArgumentException e) {
      return true;
    }
    return !kind.display(saved).equals(kind.display(value));
  }

  /**
   * The interview answer of a field question. A change of a value that a document prefilled makes
   * the value the user's own statement, and it stays so.
   */
  private String fieldAnswer(long returnId, Question question, boolean changed) {
    Answers answers = forms.answers(returnId, Interview.ID);
    boolean own =
        Interview.OWN.equals(answers.text(question.id()))
            || (changed && Interview.prefilled(answers).contains(question.id()));
    return own ? Interview.OWN : Interview.DONE;
  }

  /** Clears the interview answers. The values in the forms stay. */
  @PostMapping("/returns/{returnId}/interview/restart")
  String restart(@PathVariable long returnId) {
    context.definitionOf(returnId);
    forms.save(returnId, Interview.ID, new Answers(Map.of()));
    return "redirect:" + interviewHref(returnId);
  }

  private static FieldTarget targetOf(Question question) {
    return new FieldTarget(question.formId(), question.id());
  }

  /**
   * The error for an empty answer to a required field, or null. "Weiter" needs the value, so a
   * done interview leaves no required field empty. A save on leaving the field may still clear it.
   */
  private String requiredError(long returnId, Question question, String value) {
    Field field = context.formOf(returnId, question.formId()).field(question.id()).orElseThrow();
    return field.required() && value.isEmpty()
        ? "Die Erklärung braucht diesen Wert. Bitte füllen Sie das Feld aus."
        : null;
  }

  /** The labels of the personal details that the person does not have yet. */
  private List<String> missingDetails(long returnId) {
    Answers main = forms.answers(returnId, MainForm.ID);
    return MainForm.personalFields().stream()
        .filter(field -> field.required() && !main.has(field.id()))
        .map(Field::label)
        .toList();
  }

  private String questionScreen(long returnId, Question question, Model model) {
    context.addCommon(returnId, model);
    Answers answers = forms.answers(returnId, Interview.ID);
    List<Question> inTopic = interviewOf(returnId).questionsOf(answers, question.topic());
    model.addAttribute("question", question);
    model.addAttribute("topicLabel", question.topic().label());
    model.addAttribute("position", inTopic.indexOf(question) + 1);
    model.addAttribute("positions", inTopic.size());
    model.addAttribute("back", backHref(returnId, answers, question));
    model.addAttribute("current", answers.text(question.id()));
    if (question.type() == Type.FIELD) {
      FormModule form = context.formOf(returnId, question.formId());
      Field field = form.field(question.id()).orElseThrow();
      model.addAttribute("field", field);
      if (!model.containsAttribute("value")) {
        model.addAttribute("value", forms.answers(returnId, form.id()).text(question.id()));
      }
      model.addAttribute("source", sourceOf(returnId, question));
      Interview.certificateDocument(answers, question.id())
          .ifPresent(
              document -> {
                model.addAttribute(
                    "certificateHref", "/returns/" + returnId + "/documents/" + document);
                Optional<CertificateValues> read =
                    certificates.readCertificate(returnId, document);
                model.addAttribute(
                    "certificateRead",
                    read.flatMap(values -> values.value(field.hintId())).orElse(""));
                model.addAttribute(
                    "certificateReadText",
                    read.map(values -> values.readText(field.hintId()))
                        .orElse(CertificateValues.NOT_FOUND));
              });
    }
    if (question.type() == Type.DOCUMENT) {
      model.addAttribute("certificateOptions", certificateOptions(returnId));
    }
    if (question.type() == Type.CONFIRM) {
      Answers main = forms.answers(returnId, MainForm.ID);
      model.addAttribute(
          "details",
          MainForm.personalFields().stream()
                  .map(field -> new DetailView(field.label(), main.text(field.id())))
                  .toList());
      model.addAttribute(
          "personHref",
          household.personOf(returnId).map(person -> "/persons/" + person).orElse("/"));
    }
    return "interview";
  }

  private List<Kind.Option> certificateOptions(long returnId) {
    return documents.documentsOf(returnId).stream()
        .filter(document -> document.kind() == DocumentKind.WAGE_TAX_CERTIFICATE)
        .map(document -> new Kind.Option(Long.toString(document.id()), document.fileName()))
        .toList();
  }

  private TopicView topicView(long returnId, Topic topic) {
    Answers answers = forms.answers(returnId, Interview.ID);
    List<AnsweredView> views =
        interviewOf(returnId).questionsOf(answers, topic).stream()
            .map(
                question ->
                    new AnsweredView(
                        question.id(),
                        question.hintId(),
                        question.text(),
                        shown(returnId, question, answers),
                        answers.has(question.id()) ? sourceOf(returnId, question) : null))
            .toList();
    return new TopicView(topic.name(), topic.label(), views);
  }

  /** The answer as the summary shows it. A field shows the value that its form holds now. */
  private String shown(long returnId, Question question, Answers answers) {
    if (!answers.has(question.id())) {
      return "Noch offen";
    }
    return switch (question.type()) {
      case YES_NO, CHOICE -> question.labelOf(answers.text(question.id()));
      case CONFIRM -> "Bestätigt";
      case DOCUMENT ->
          documents
              .find(returnId, Long.parseLong(answers.text(question.id())))
              .map(StoredDocument::fileName)
              .orElse("Noch offen");
      case FIELD -> {
        String value = forms.answers(returnId, question.formId()).text(question.id());
        yield value.isEmpty()
            ? "Keine Angabe"
            : context.formOf(returnId, question.formId())
                .field(question.id())
                .map(field -> field.kind().display(value))
                .orElse(value);
      }
    };
  }

  /**
   * The source of the saved value of a field question: "Beleg: " and the file name, "Beleg fehlt"
   * after the user deleted its document or when a new version of it has no value, or "eigene
   * Angabe". It is null for a question that is not a field, or for an empty field with evidence.
   */
  private String sourceOf(long returnId, Question question) {
    if (question.type() != Type.FIELD) {
      return null;
    }
    if (forms.answersWithoutEvidence(returnId, question.formId()).contains(question.id())) {
      return new Source.Missing(null).label();
    }
    if (forms.answers(returnId, question.formId()).text(question.id()).isEmpty()) {
      return null;
    }
    Long document = forms.answerDocuments(returnId, question.formId()).get(question.id());
    return Optional.ofNullable(document)
        .flatMap(id -> documents.find(returnId, id))
        .map(stored -> "Beleg: " + stored.fileName())
        .orElse("eigene Angabe");
  }

  private String backHref(long returnId, Answers answers, Question question) {
    return interviewOf(returnId)
        .previousInTopic(answers, question)
        .map(previous -> questionHref(returnId, previous))
        .or(() -> Interview.previousTopic(question.topic()).map(topic -> topicHref(returnId, topic)))
        .orElse(null);
  }

  private static String questionHref(long returnId, Question question) {
    return interviewHref(returnId) + "/questions/" + question.id();
  }

  private static String topicHref(long returnId, Topic topic) {
    return interviewHref(returnId) + "/topics/" + topic.name();
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

  private Question questionOf(long returnId, String questionId) {
    return interviewOf(returnId)
        .find(forms.answers(returnId, Interview.ID), questionId)
        .orElseThrow(ReturnYears::notFound);
  }

  private static Topic topicOf(String name) {
    return Arrays.stream(Topic.values())
        .filter(topic -> topic.name().equals(name))
        .findFirst()
        .orElseThrow(ReturnYears::notFound);
  }

  private Interview interviewOf(long returnId) {
    return context.definitionOf(returnId).interview();
  }

}
