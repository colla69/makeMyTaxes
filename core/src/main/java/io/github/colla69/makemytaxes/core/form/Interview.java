package io.github.colla69.makemytaxes.core.form;

import io.github.colla69.makemytaxes.core.expense.ExpenseTopic;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Yes/no questions that tell the user which pages of the app the return needs. The interview
 * only points to a page. It does not decide an amount or a tax rule.
 */
public final class Interview {

  /** The interview keeps its answers like a form, under this ID. */
  public static final String ID = "interview";

  public static final String YES = "YES";
  public static final String NO = "NO";

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

  public record Question(String id, String text, Step step) {}

  /** Each return needs these pages, whatever the answers are. */
  private static final List<Step> ALWAYS =
      List.of(Step.form(FilingCheckForm.ID), Step.form(MainForm.ID));

  private static final List<Question> QUESTIONS =
      List.of(
          new Question(
              "employed",
              "Haben Sie in diesem Jahr Arbeitslohn als Arbeitnehmer bekommen?",
              Step.form(AnlageN.ID)),
          new Question(
              "workEquipment",
              "Haben Sie selbst etwas für Ihre Arbeit gekauft, zum Beispiel einen Computer oder"
                  + " Fachbücher?",
              Step.expenses(ExpenseTopic.WORK_EQUIPMENT)),
          new Question(
              "workTraining",
              "Haben Sie selbst für eine Fortbildung für Ihren Beruf bezahlt?",
              Step.expenses(ExpenseTopic.WORK_TRAINING)),
          new Question(
              "householdServices",
              "Haben Sie für Handwerker, eine Reinigungskraft oder einen Gärtner in Ihrem"
                  + " Haushalt bezahlt?",
              Step.expenses(ExpenseTopic.HOUSEHOLD_SERVICES)),
          new Question(
              "insurance",
              "Haben Sie selbst Beiträge zu Versicherungen bezahlt?",
              Step.expenses(ExpenseTopic.INSURANCE)),
          new Question(
              "donations",
              "Haben Sie Geld oder Sachen gespendet, zum Beispiel an einen Verein oder eine"
                  + " Partei?",
              Step.expenses(ExpenseTopic.DONATIONS_AND_PENSIONS)),
          new Question(
              "medical",
              "Haben Sie selbst Kosten für Ärzte, Medikamente oder Brillen bezahlt?",
              Step.expenses(ExpenseTopic.MEDICAL)),
          new Question(
              "childcare",
              "Haben Sie für die Betreuung eines Kindes bezahlt, zum Beispiel für eine Kita?",
              Step.expenses(ExpenseTopic.CHILDCARE)));

  private Interview() {}

  public static List<Question> questions() {
    return QUESTIONS;
  }

  /** The first question without an answer, or none when the interview is done. */
  public static Optional<Question> next(Answers answers) {
    return QUESTIONS.stream().filter(question -> !answers.has(question.id())).findFirst();
  }

  public static boolean isDone(Answers answers) {
    return next(answers).isEmpty();
  }

  /** The pages that the return needs, in the order of the questions. */
  public static List<Step> steps(Answers answers) {
    List<Step> steps = new ArrayList<>(ALWAYS);
    for (Question question : QUESTIONS) {
      if (YES.equals(answers.text(question.id())) && !steps.contains(question.step())) {
        steps.add(question.step());
      }
    }
    return steps;
  }
}
