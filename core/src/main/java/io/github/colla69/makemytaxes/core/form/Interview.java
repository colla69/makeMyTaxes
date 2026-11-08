package io.github.colla69.makemytaxes.core.form;

import io.github.colla69.makemytaxes.core.expense.ExpenseTopic;
import io.github.colla69.makemytaxes.core.form.HouseholdServicesForm.Category;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
    FIELD,
    CONFIRM
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
   * @param step the page that a yes needs, for a yes/no question only. It is null for a question
   *     that adds no page.
   * @param formId the form of the field, for a field question only. The ID of the question is the
   *     ID of the field.
   */
  public record Question(
      String id, Topic topic, Type type, String text, Step step, String formId) {

    static Question yesNo(String id, Topic topic, String text, Step step) {
      return new Question(id, topic, Type.YES_NO, text, step, null);
    }

    static Question field(String formId, String fieldId, Topic topic, String text) {
      return new Question(fieldId, topic, Type.FIELD, text, null, formId);
    }

    static Question confirm(String id, Topic topic, String text) {
      return new Question(id, topic, Type.CONFIRM, text, null, null);
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

  /** One question for each line of the Lohnsteuerbescheinigung, in the order of the form. */
  private static final List<EmployerQuestion> EMPLOYER_QUESTIONS =
      List.of(
          new EmployerQuestion("employerName", "Wie heißt der Arbeitgeber?"),
          new EmployerQuestion(
              "employedFrom", "Nr. 1 der Lohnsteuerbescheinigung: Ab welchem Tag dauerte das"
                  + " Dienstverhältnis?"),
          new EmployerQuestion(
              "employedTo", "Nr. 1 der Lohnsteuerbescheinigung: Bis zu welchem Tag dauerte das"
                  + " Dienstverhältnis?"),
          new EmployerQuestion(
              "taxClass", "Welche Steuerklasse steht auf der Lohnsteuerbescheinigung?"),
          new EmployerQuestion(
              "grossWage", "Welcher Betrag steht in Nr. 3, Bruttoarbeitslohn?"),
          new EmployerQuestion("wageTax", "Welcher Betrag steht in Nr. 4, Lohnsteuer?"),
          new EmployerQuestion(
              "solidarity", "Welcher Betrag steht in Nr. 5, Solidaritätszuschlag?"),
          new EmployerQuestion(
              "churchTax", "Welcher Betrag steht in Nr. 6, Kirchensteuer des Arbeitnehmers?"),
          new EmployerQuestion(
              "shortTimeWork", "Welcher Betrag steht in Nr. 15, Kurzarbeitergeld?"),
          new EmployerQuestion(
              "employerCommute",
              "Wie hoch ist die Summe von Nr. 17 und Nr. 18, Arbeitgeberleistungen für Fahrten"
                  + " zur Arbeit?"));

  private static final List<Question> REST =
      List.of(
          Question.field(MainForm.ID, "occupation", Topic.JOB, "Welchen Beruf üben Sie aus?"),
          Question.field(
              AnlageN.ID,
              "commuteKm",
              Topic.COMMUTE,
              "Wie weit ist Ihr Weg von der Wohnung zur ersten Tätigkeitsstätte, in einer"
                  + " Richtung?"),
          Question.yesNo(
              "workEquipment",
              Topic.COMMUTE,
              "Haben Sie selbst etwas für Ihre Arbeit gekauft, zum Beispiel einen Computer oder"
                  + " Fachbücher?",
              Step.expenses(ExpenseTopic.WORK_EQUIPMENT)),
          Question.yesNo(
              "workTraining",
              Topic.COMMUTE,
              "Haben Sie selbst für eine Fortbildung für Ihren Beruf bezahlt?",
              Step.expenses(ExpenseTopic.WORK_TRAINING)),
          Question.yesNo(
              "insurance",
              Topic.INSURANCE,
              "Haben Sie selbst Beiträge zu Versicherungen bezahlt?",
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

  private Interview() {}

  /**
   * The questions for these interview answers. A yes to {@link #EMPLOYED} adds the questions of
   * the first employer, and each yes to {@link #ANOTHER_EMPLOYER} adds one more employer.
   */
  public static List<Question> questions(Answers answers) {
    List<Question> questions = new ArrayList<>(START);
    if (YES.equals(answers.text(EMPLOYED))) {
      int employer = 0;
      do {
        employer++;
        questions.addAll(employerQuestions(employer));
      } while (YES.equals(answers.text(anotherEmployerId(employer))));
    }
    questions.addAll(REST);
    for (Category category : Category.values()) {
      questions.addAll(householdQuestions(answers, category));
    }
    questions.addAll(HOUSEHOLD_REST);
    return questions;
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

  /** The ID of the question after invoice {@code entry} of a category. */
  public static String anotherEntryId(Category category, int entry) {
    return category.fieldId(entry, ANOTHER_ENTRY);
  }

  /** The ID of the question after employer {@code employer}. */
  public static String anotherEmployerId(int employer) {
    return AnlageN.employerFieldId(employer, ANOTHER_EMPLOYER);
  }

  private static List<Question> employerQuestions(int employer) {
    List<Question> questions = new ArrayList<>();
    for (EmployerQuestion question : EMPLOYER_QUESTIONS) {
      questions.add(
          Question.field(
              AnlageN.ID,
              AnlageN.employerFieldId(employer, question.fieldId()),
              Topic.JOB,
              "Arbeitgeber " + employer + ": " + question.text()));
    }
    questions.add(
        Question.yesNo(
            anotherEmployerId(employer),
            Topic.JOB,
            "Hatten Sie einen weiteren Arbeitgeber?",
            null));
    return questions;
  }

  public static Optional<Question> find(Answers answers, String id) {
    return questions(answers).stream().filter(question -> question.id().equals(id)).findFirst();
  }

  public static List<Question> questionsOf(Answers answers, Topic topic) {
    return questions(answers).stream().filter(question -> question.topic() == topic).toList();
  }

  /** The first question without an answer, or none when the interview is done. */
  public static Optional<Question> next(Answers answers) {
    return questions(answers).stream().filter(question -> !answers.has(question.id())).findFirst();
  }

  /** The question after this one in the same topic, or none after the last one of the topic. */
  public static Optional<Question> nextInTopic(Answers answers, Question question) {
    List<Question> topic = questionsOf(answers, question.topic());
    int index = topic.indexOf(question);
    return index + 1 < topic.size() ? Optional.of(topic.get(index + 1)) : Optional.empty();
  }

  /** The question before this one in the same topic, or none before the first one. */
  public static Optional<Question> previousInTopic(Answers answers, Question question) {
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

  public static long answeredCount(Answers answers) {
    return questions(answers).stream().filter(question -> answers.has(question.id())).count();
  }

  public static boolean isDone(Answers answers) {
    return next(answers).isEmpty();
  }

  /** The pages that the return needs, in the order of the questions. */
  public static List<Step> steps(Answers answers) {
    List<Step> steps = new ArrayList<>(ALWAYS);
    for (Question question : questions(answers)) {
      if (question.type() == Type.YES_NO
          && question.step() != null
          && YES.equals(answers.text(question.id()))
          && !steps.contains(question.step())) {
        steps.add(question.step());
      }
    }
    return steps;
  }
}
