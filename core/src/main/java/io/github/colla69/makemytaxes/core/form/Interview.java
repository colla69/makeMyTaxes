package io.github.colla69.makemytaxes.core.form;

import io.github.colla69.makemytaxes.core.expense.ExpenseTopic;
import java.util.ArrayList;
import java.util.List;
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
   * @param step the page that a yes needs, for a yes/no question only
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
  }

  /** The ID of the confirmation of the personal details of the person. */
  public static final String PERSONAL_DETAILS = "personalDetails";

  /** Each return needs these pages, whatever the answers are. */
  private static final List<Step> ALWAYS =
      List.of(Step.form(FilingCheckForm.ID), Step.form(MainForm.ID));

  private static final List<Question> QUESTIONS =
      List.of(
          Question.confirm(
              PERSONAL_DETAILS, Topic.JOB, "Stimmen Ihre Angaben zur Person?"),
          Question.yesNo(
              "employed",
              Topic.JOB,
              "Haben Sie in diesem Jahr Arbeitslohn als Arbeitnehmer bekommen?",
              Step.form(AnlageN.ID)),
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
              Step.expenses(ExpenseTopic.MEDICAL)),
          Question.yesNo(
              "householdServices",
              Topic.HOUSEHOLD,
              "Haben Sie für Handwerker, eine Reinigungskraft oder einen Gärtner in Ihrem"
                  + " Haushalt bezahlt?",
              Step.expenses(ExpenseTopic.HOUSEHOLD_SERVICES)),
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

  public static List<Question> questions() {
    return QUESTIONS;
  }

  public static Optional<Question> find(String id) {
    return QUESTIONS.stream().filter(question -> question.id().equals(id)).findFirst();
  }

  public static List<Question> questionsOf(Topic topic) {
    return QUESTIONS.stream().filter(question -> question.topic() == topic).toList();
  }

  /** The first question without an answer, or none when the interview is done. */
  public static Optional<Question> next(Answers answers) {
    return QUESTIONS.stream().filter(question -> !answers.has(question.id())).findFirst();
  }

  /** The question after this one in the same topic, or none after the last one of the topic. */
  public static Optional<Question> nextInTopic(Question question) {
    List<Question> topic = questionsOf(question.topic());
    int index = topic.indexOf(question);
    return index + 1 < topic.size() ? Optional.of(topic.get(index + 1)) : Optional.empty();
  }

  /** The question before this one in the same topic, or none before the first one. */
  public static Optional<Question> previousInTopic(Question question) {
    List<Question> topic = questionsOf(question.topic());
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
    return QUESTIONS.stream().filter(question -> answers.has(question.id())).count();
  }

  public static boolean isDone(Answers answers) {
    return next(answers).isEmpty();
  }

  /** The pages that the return needs, in the order of the questions. */
  public static List<Step> steps(Answers answers) {
    List<Step> steps = new ArrayList<>(ALWAYS);
    for (Question question : QUESTIONS) {
      if (question.type() == Type.YES_NO
          && YES.equals(answers.text(question.id()))
          && !steps.contains(question.step())) {
        steps.add(question.step());
      }
    }
    return steps;
  }
}
