package io.github.colla69.makemytaxes.core.form;

import io.github.colla69.makemytaxes.core.expense.ExpenseTopic;
import io.github.colla69.makemytaxes.core.form.HouseholdServicesForm.Category;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * The questionnaire: questions by life topic, one at a time. A yes/no question tells the user
 * which pages of the app the return needs. A field question writes its answer into the field of
 * a form. A confirmation shows values that the app already has. The interview does not decide an
 * amount or a tax rule.
 */
public final class Interview {

  /** The interview keeps its answers like a form, under this ID. */
  public static final String ID = "interview";

  public static final String YES = "YES";
  public static final String NO = "NO";

  /**
   * The interview answer of a field question or a confirmation. It only marks the question as
   * answered: the value of a field question stays in its form.
   */
  public static final String DONE = "DONE";

  /** The life topics, in the order of the interview. */
  public enum Topic {
    JOB("Beruf"),
    COMMUTE("Arbeitsweg und Arbeit"),
    INSURANCE("Versicherungen"),
    HOUSEHOLD("Haushalt");

    private final String label;

    Topic(String label) {
      this.label = label;
    }

    public String label() {
      return label;
    }
  }

  public enum Type {
    YES_NO,
    CHOICE,
    FIELD,
    CONFIRM,
    DOCUMENT
  }

  /** A page of the app: a form, or the expense page with one category. */
  public record Step(String formId, ExpenseTopic topic) {

    static Step form(String formId) {
      return new Step(formId, null);
    }

    static Step expenses(ExpenseTopic topic) {
      return new Step(null, topic);
    }

    public boolean isForm() {
      return formId != null;
    }
  }

  /**
   * One screen of the interview.
   *
   * @param step the page that a yes needs, for a yes/no question, or that any answer needs, for a
   *     choice. It is null for a question that adds no page.
   * @param formId the form of the field, for a field question only. The ID of the question is the
   *     ID of the field.
   * @param options the answers of a yes/no question or a choice, one button each
   */
  public record Question(
      String id,
      Topic topic,
      Type type,
      String text,
      Step step,
      String formId,
      List<Kind.Option> options) {

    private static final List<Kind.Option> YES_NO_OPTIONS =
        List.of(new Kind.Option(YES, "Ja"), new Kind.Option(NO, "Nein"));

    static Question yesNo(String id, Topic topic, String text, Step step) {
      return new Question(id, topic, Type.YES_NO, text, step, null, YES_NO_OPTIONS);
    }

    static Question choice(String id, Topic topic, String text, Step step) {
      return new Question(id, topic, Type.CHOICE, text, step, null, HEALTH_INSURANCE_OPTIONS);
    }

    static Question field(String formId, String fieldId, Topic topic, String text) {
      return new Question(fieldId, topic, Type.FIELD, text, null, formId, List.of());
    }

    static Question confirm(String id, Topic topic, String text) {
      return new Question(id, topic, Type.CONFIRM, text, null, null, List.of());
    }

    static Question document(String id, Topic topic, String text) {
      return new Question(id, topic, Type.DOCUMENT, text, null, null, List.of());
    }

    /** True if the answer is one of the buttons of this question. */
    public boolean allows(String answer) {
      return options.stream().anyMatch(option -> option.value().equals(answer));
    }

    /** The label of the button of this answer, or the answer itself. */
    public String labelOf(String answer) {
      return options.stream()
          .filter(option -> option.value().equals(answer))
          .map(Kind.Option::label)
          .findFirst()
          .orElse(answer);
    }

    /** The ID of the hint text. The questions of each employer share one hint. */
    public String hintId() {
      return id.substring(id.indexOf('-') + 1);
    }
  }

  /** The ID of the confirmation of the personal details of the person. */
  public static final String PERSONAL_DETAILS = "personalDetails";

  /** Each return needs these pages, whatever the answers are. */
  private static final List<Step> ALWAYS =
      List.of(Step.form(FilingCheckForm.ID), Step.form(MainForm.ID));

  /** The ID of the question that starts the employers. */
  public static final String EMPLOYED = "employed";

  /** The question after the values of one employer. A no ends the employers. */
  public static final String ANOTHER_EMPLOYER = "anotherEmployer";

  /** The question at the start of each employer: does the user have the certificate? */
  public static final String WAGE_CERTIFICATE = "wageCertificate";

  /** The question after a yes. Its answer is the ID of the document of the certificate. */
  public static final String WAGE_CERTIFICATE_DOCUMENT = "wageCertificateDocument";

  /** The question that decides which lines of the certificates the interview asks. */
  public static final String HEALTH_INSURANCE = "healthInsurance";

  public static final String STATUTORY = "STATUTORY";
  public static final String PRIVATE = "PRIVATE";

  private static final List<Kind.Option> HEALTH_INSURANCE_OPTIONS =
      List.of(new Kind.Option(STATUTORY, "Gesetzlich"), new Kind.Option(PRIVATE, "Privat"));

  private static final List<Question> START =
      List.of(
          Question.confirm(
              PERSONAL_DETAILS, Topic.JOB, "Stimmen Ihre Angaben zur Person?"),
          Question.yesNo(
              EMPLOYED,
              Topic.JOB,
              "Haben Sie in diesem Jahr Arbeitslohn als Arbeitnehmer bekommen?",
              Step.form(AnlageN.ID)));

  private record EmployerQuestion(String fieldId, String text) {}

  private EmployerQuestion amountQuestion(String fieldId, String name) {
    String place =
        certificate
            .lineOf(fieldId)
            .map(line -> "in " + line)
            .orElse("auf der Lohnsteuerbescheinigung");
    return new EmployerQuestion(fieldId, "Welcher Betrag steht %s, %s?".formatted(place, name));
  }

  /**
   * "Zeile 1 der Lohnsteuerbescheinigung", or only "Lohnsteuerbescheinigung" for a certificate
   * whose lines are not known yet.
   */
  private String certificatePlace(String fieldId) {
    return certificate
        .lineOf(fieldId)
        .map(line -> line + " der Lohnsteuerbescheinigung")
        .orElse("Lohnsteuerbescheinigung");
  }

  /** One question for each line of the Lohnsteuerbescheinigung, in the order of the form. */
  private List<EmployerQuestion> employerQuestions() {
    return List.of(
          new EmployerQuestion("employerName", "Wie heißt der Arbeitgeber?"),
          new EmployerQuestion(
              "employedFrom",
              certificatePlace("employedFrom")
                  + ": Ab welchem Tag dauerte das Dienstverhältnis?"),
          new EmployerQuestion(
              "employedTo",
              certificatePlace("employedTo")
                  + ": Bis zu welchem Tag dauerte das Dienstverhältnis?"),
          new EmployerQuestion(
              "taxClass", "Welche Steuerklasse steht auf der Lohnsteuerbescheinigung?"),
          amountQuestion("grossWage", "Bruttoarbeitslohn"),
          amountQuestion("wageTax", "Lohnsteuer"),
          amountQuestion("solidarity", "Solidaritätszuschlag"),
          amountQuestion("churchTax", "Kirchensteuer des Arbeitnehmers"),
          amountQuestion(
              "shortTimeWork",
              "Kurzarbeitergeld, Zuschuss zum Mutterschaftsgeld, Verdienstausfallentschädigung,"
                  + " Aufstockungsbetrag"),
          new EmployerQuestion(
              "employerCommute",
              "Wie hoch ist die Summe %s, Arbeitgeberleistungen für Fahrten zur Arbeit?"
                  .formatted(
                      certificate
                          .lineOf("employerCommute")
                          .map(line -> "von " + line)
                          .orElse("auf der Lohnsteuerbescheinigung"))));
  }

  private static final List<Question> JOB_AND_COMMUTE =
      List.of(
          Question.field(MainForm.ID, "occupation", Topic.JOB, "Welchen Beruf üben Sie aus?"),
          Question.field(
              AnlageN.ID,
              "commuteKm",
              Topic.COMMUTE,
              "Wie weit ist Ihr Weg von der Wohnung zur ersten Tätigkeitsstätte, in einer"
                  + " Richtung?"),
          // The transport comes before the days, because AnlageN.check needs it once days exist.
          Question.field(
              AnlageN.ID,
              "commuteTransport",
              Topic.COMMUTE,
              "Womit sind Sie zur ersten Tätigkeitsstätte gefahren?"),
          Question.field(
              AnlageN.ID,
              "commuteDays",
              Topic.COMMUTE,
              "An wie vielen Tagen sind Sie zur ersten Tätigkeitsstätte gefahren?"),
          Question.field(
              AnlageN.ID,
              "homeOfficeDays",
              Topic.COMMUTE,
              "An wie vielen Tagen haben Sie nur zu Hause gearbeitet?"),
          Question.yesNo(
              "workEquipment",
              Topic.COMMUTE,
              "Haben Sie selbst etwas für Ihre Arbeit gekauft, zum Beispiel einen Computer oder"
                  + " Fachbücher? Halten Sie die Belege bereit.",
              Step.expenses(ExpenseTopic.WORK_EQUIPMENT)),
          Question.yesNo(
              "workTraining",
              Topic.COMMUTE,
              "Haben Sie selbst für eine Fortbildung für Ihren Beruf bezahlt?",
              Step.expenses(ExpenseTopic.WORK_TRAINING)));

  private static final Question HEALTH_INSURANCE_QUESTION =
      Question.choice(
          HEALTH_INSURANCE,
          Topic.INSURANCE,
          "Sind Sie gesetzlich oder privat krankenversichert?",
          Step.form(AnlageVorsorgeaufwand.ID));

  /** The questions on the certificate of the private health insurer, by field. */
  private static final List<String> PRIVATE_TEXTS =
      List.of(
          "Bescheinigung Ihres Versicherers: Wie hoch sind die Beiträge zur Krankenversicherung"
              + " für die Basisabsicherung?",
          "Bescheinigung Ihres Versicherers: Wie hoch sind die Beiträge zur privaten"
              + " Pflege-Pflichtversicherung?",
          "Bescheinigung Ihres Versicherers: Wie hoch sind die erstatteten Beiträge?");

  /** The other insurances stay receipts on the expense page, so each amount keeps its evidence. */
  private static final List<Question> INSURANCE_REST =
      List.of(
          Question.yesNo(
              "insurance",
              Topic.INSURANCE,
              "Haben Sie weitere Versicherungen bezahlt, zum Beispiel Haftpflicht, Unfall,"
                  + " Berufsunfähigkeit oder Risikolebensversicherung?",
              Step.expenses(ExpenseTopic.INSURANCE)),
          Question.yesNo(
              "medical",
              Topic.INSURANCE,
              "Haben Sie selbst Kosten für Ärzte, Medikamente oder Brillen bezahlt?",
              Step.expenses(ExpenseTopic.MEDICAL)));

  /** The question that starts each category of household costs. A yes asks its invoices. */
  private static final Map<Category, String> CATEGORY_QUESTIONS =
      Map.of(
          Category.CLEANING,
          "Haben Sie eine Reinigungskraft oder eine Reinigungsfirma für Ihren Haushalt bezahlt?",
          Category.GARDENING,
          "Haben Sie jemanden für die Pflege Ihres Gartens bezahlt?",
          Category.CRAFTSMEN,
          "Haben Sie Handwerker für Arbeiten in Ihrem Haushalt bezahlt, zum Beispiel für eine"
              + " Reparatur?",
          Category.ANCILLARY_COSTS,
          "Haben Sie eine Nebenkostenabrechnung für Ihre Mietwohnung?");

  /** One question for each field of an invoice or a statement, by the field ID. */
  private static final Map<String, String> ENTRY_QUESTIONS =
      Map.of(
          "invoice", "Wer hat die Rechnung gestellt, mit welcher Nummer und welchem Datum?",
          "statement", "Wer hat die Abrechnung erstellt, und mit welchem Datum?",
          HouseholdServicesForm.PAID, "Welchen Betrag haben Sie bezahlt?",
          HouseholdServicesForm.LABOUR, "Wie hoch sind die Arbeitskosten auf der Rechnung?",
          HouseholdServicesForm.LABOUR_SERVICES,
              "Welche Arbeitskosten für haushaltsnahe Dienstleistungen nennt die Abrechnung?",
          HouseholdServicesForm.LABOUR_CRAFTSMEN,
              "Welche Arbeitskosten für Handwerkerleistungen nennt die Abrechnung?",
          HouseholdServicesForm.PAYMENT, "Wie haben Sie bezahlt?");

  /** The ID of the question after one invoice of a category. A no ends the category. */
  public static final String ANOTHER_ENTRY = "another";

  private static final List<Question> HOUSEHOLD_REST =
      List.of(
          Question.yesNo(
              "childcare",
              Topic.HOUSEHOLD,
              "Haben Sie für die Betreuung eines Kindes bezahlt, zum Beispiel für eine Kita?",
              Step.expenses(ExpenseTopic.CHILDCARE)),
          Question.yesNo(
              "donations",
              Topic.HOUSEHOLD,
              "Haben Sie Geld oder Sachen gespendet, zum Beispiel an einen Verein oder eine"
                  + " Partei?",
              Step.expenses(ExpenseTopic.DONATIONS_AND_PENSIONS)));

  private final WageTaxCertificate certificate;

  /** The interview of a year. The questions name the lines of the certificate of that year. */
  public Interview(WageTaxCertificate certificate) {
    this.certificate = certificate;
  }

  /**
   * The questions for these interview answers. A yes to {@link #EMPLOYED} adds the questions of
   * the first employer, and each yes to {@link #ANOTHER_EMPLOYER} adds one more employer. The
   * answer to {@link #HEALTH_INSURANCE} adds the social insurance lines of each employer.
   */
  public List<Question> questions(Answers answers) {
    List<Question> questions = new ArrayList<>(START);
    int employers = employerCount(answers);
    for (int employer = 1; employer <= employers; employer++) {
      questions.addAll(employerQuestions(answers, employer));
    }
    questions.addAll(JOB_AND_COMMUTE);
    questions.add(HEALTH_INSURANCE_QUESTION);
    questions.addAll(insuranceQuestions(answers.text(HEALTH_INSURANCE), employers));
    questions.addAll(INSURANCE_REST);
    for (Category category : Category.values()) {
      questions.addAll(householdQuestions(answers, category));
    }
    questions.addAll(HOUSEHOLD_REST);
    return questions;
  }

  /** The number of employers: none without employment, then one more for each yes. */
  private static int employerCount(Answers answers) {
    if (!YES.equals(answers.text(EMPLOYED))) {
      return 0;
    }
    int employer = 1;
    while (YES.equals(answers.text(anotherEmployerId(employer)))) {
      employer++;
    }
    return employer;
  }

  /**
   * The social insurance lines of each certificate, and for a private insurance the values of the
   * certificate of the insurer. Without an answer to the kind of health insurance, there are none.
   */
  private List<Question> insuranceQuestions(String healthInsurance, int employers) {
    if (!HEALTH_INSURANCE_QUESTION.allows(healthInsurance)) {
      return List.of();
    }
    boolean isPrivate = PRIVATE.equals(healthInsurance);
    List<String> lines =
        isPrivate
            ? AnlageVorsorgeaufwand.PRIVATE_EMPLOYER_FIELDS
            : AnlageVorsorgeaufwand.STATUTORY_EMPLOYER_FIELDS;
    List<Question> questions = new ArrayList<>();
    for (int employer = 1; employer <= employers; employer++) {
      for (String fieldId : lines) {
        questions.add(
            Question.field(
                AnlageVorsorgeaufwand.ID,
                AnlageN.employerFieldId(employer, fieldId),
                Topic.INSURANCE,
                "Arbeitgeber %d: Welcher Betrag steht auf der Lohnsteuerbescheinigung in %s?"
                    .formatted(employer, socialInsuranceLabel(fieldId).replace(" (EUR)", ""))));
      }
    }
    if (isPrivate) {
      for (int index = 0; index < PRIVATE_TEXTS.size(); index++) {
        questions.add(
            Question.field(
                AnlageVorsorgeaufwand.ID,
                Sections.PRIVATE_HEALTH.fields().get(index).id(),
                Topic.INSURANCE,
                PRIVATE_TEXTS.get(index)));
      }
    }
    return questions;
  }

  /** The label of a social insurance field, with its line on the certificate of the year. */
  private String socialInsuranceLabel(String fieldId) {
    Field field =
        Sections.SOCIAL_INSURANCE.fields().stream()
            .filter(candidate -> candidate.id().equals(fieldId))
            .findFirst()
            .orElseThrow();
    return certificate.label(fieldId, field.label());
  }

  /**
   * The question of a category, and after a yes the fields of each invoice. A yes to {@link
   * #anotherEntryId} adds one more invoice.
   */
  private static List<Question> householdQuestions(Answers answers, Category category) {
    List<Question> questions = new ArrayList<>();
    questions.add(
        Question.yesNo(
            category.id(),
            Topic.HOUSEHOLD,
            CATEGORY_QUESTIONS.get(category),
            Step.form(HouseholdServicesForm.ID)));
    if (!YES.equals(answers.text(category.id()))) {
      return questions;
    }
    int entry = 0;
    do {
      entry++;
      for (Field field : category.fields(entry)) {
        questions.add(
            Question.field(
                HouseholdServicesForm.ID,
                field.id(),
                Topic.HOUSEHOLD,
                category.label() + " " + entry + ": " + ENTRY_QUESTIONS.get(field.hintId())));
      }
      questions.add(
          Question.yesNo(
              anotherEntryId(category, entry),
              Topic.HOUSEHOLD,
              category.label() + ": Haben Sie eine weitere Rechnung?",
              null));
    } while (YES.equals(answers.text(anotherEntryId(category, entry))));
    return questions;
  }

  /**
   * The document that a field comes from: the certificate that the user named for the employer of
   * the field. There is none for a field that is not on the certificate, or after a no.
   */
  public static OptionalLong certificateDocument(Answers answers, String fieldId) {
    int employer = AnlageN.employerOf(fieldId);
    String document = answers.text(AnlageN.employerFieldId(employer, WAGE_CERTIFICATE_DOCUMENT));
    boolean fromCertificate =
        Sections.isOnCertificate(fieldId.substring(fieldId.indexOf('-') + 1))
            && YES.equals(answers.text(AnlageN.employerFieldId(employer, WAGE_CERTIFICATE)))
            && !document.isEmpty();
    return fromCertificate ? OptionalLong.of(Long.parseLong(document)) : OptionalLong.empty();
  }

  /** The ID of the question after invoice {@code entry} of a category. */
  public static String anotherEntryId(Category category, int entry) {
    return category.fieldId(entry, ANOTHER_ENTRY);
  }

  /** The ID of the question after employer {@code employer}. */
  public static String anotherEmployerId(int employer) {
    return AnlageN.employerFieldId(employer, ANOTHER_EMPLOYER);
  }

  private List<Question> employerQuestions(Answers answers, int employer) {
    List<Question> questions = new ArrayList<>();
    String prefix = "Arbeitgeber " + employer + ": ";
    String certificateId = AnlageN.employerFieldId(employer, WAGE_CERTIFICATE);
    questions.add(
        Question.yesNo(
            certificateId, Topic.JOB, prefix + "Hast du deine Lohnsteuerbescheinigung?", null));
    if (YES.equals(answers.text(certificateId))) {
      questions.add(
          Question.document(
              AnlageN.employerFieldId(employer, WAGE_CERTIFICATE_DOCUMENT),
              Topic.JOB,
              prefix + "Laden Sie die Lohnsteuerbescheinigung hoch, oder wählen Sie sie aus."));
    }
    for (EmployerQuestion question : employerQuestions()) {
      questions.add(
          Question.field(
              AnlageN.ID,
              AnlageN.employerFieldId(employer, question.fieldId()),
              Topic.JOB,
              prefix + question.text()));
    }
    questions.add(
        Question.yesNo(
            anotherEmployerId(employer),
            Topic.JOB,
            "Hatten Sie einen weiteren Arbeitgeber?",
            null));
    return questions;
  }

  public Optional<Question> find(Answers answers, String id) {
    return questions(answers).stream().filter(question -> question.id().equals(id)).findFirst();
  }

  public List<Question> questionsOf(Answers answers, Topic topic) {
    return questions(answers).stream().filter(question -> question.topic() == topic).toList();
  }

  /** The first question without an answer, or none when the interview is done. */
  public Optional<Question> next(Answers answers) {
    return questions(answers).stream().filter(question -> !answers.has(question.id())).findFirst();
  }

  /** The question after this one in the same topic, or none after the last one of the topic. */
  public Optional<Question> nextInTopic(Answers answers, Question question) {
    List<Question> topic = questionsOf(answers, question.topic());
    int index = topic.indexOf(question);
    return index + 1 < topic.size() ? Optional.of(topic.get(index + 1)) : Optional.empty();
  }

  /** The question before this one in the same topic, or none before the first one. */
  public Optional<Question> previousInTopic(Answers answers, Question question) {
    List<Question> topic = questionsOf(answers, question.topic());
    int index = topic.indexOf(question);
    return index > 0 ? Optional.of(topic.get(index - 1)) : Optional.empty();
  }

  /** The topic before this one, or none before the first topic. */
  public static Optional<Topic> previousTopic(Topic topic) {
    return topic.ordinal() > 0
        ? Optional.of(Topic.values()[topic.ordinal() - 1])
        : Optional.empty();
  }

  /** The topic after this one, or none after the last topic. */
  public static Optional<Topic> nextTopic(Topic topic) {
    Topic[] topics = Topic.values();
    return topic.ordinal() + 1 < topics.length
        ? Optional.of(topics[topic.ordinal() + 1])
        : Optional.empty();
  }

  public long answeredCount(Answers answers) {
    return questions(answers).stream().filter(question -> answers.has(question.id())).count();
  }

  public boolean isDone(Answers answers) {
    return next(answers).isEmpty();
  }

  /** A yes adds the page of a yes/no question. Any answer adds the page of a choice. */
  private static boolean needsStep(Question question, Answers answers) {
    return switch (question.type()) {
      case YES_NO -> YES.equals(answers.text(question.id()));
      case CHOICE -> question.allows(answers.text(question.id()));
      case FIELD, CONFIRM, DOCUMENT -> false;
    };
  }

  /** The pages that the return needs, in the order of the questions. */
  public List<Step> steps(Answers answers) {
    List<Step> steps = new ArrayList<>(ALWAYS);
    for (Question question : questions(answers)) {
      if (question.step() != null
          && needsStep(question, answers)
          && !steps.contains(question.step())) {
        steps.add(question.step());
      }
    }
    return steps;
  }
}
