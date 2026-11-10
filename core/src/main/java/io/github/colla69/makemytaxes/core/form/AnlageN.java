package io.github.colla69.makemytaxes.core.form;

import io.github.colla69.makemytaxes.core.GermanFormats;
import io.github.colla69.makemytaxes.core.expense.Expense;
import io.github.colla69.makemytaxes.core.expense.ExpenseSummary;
import io.github.colla69.makemytaxes.core.expense.ExpenseTopic;
import io.github.colla69.makemytaxes.core.expense.Money;
import io.github.colla69.makemytaxes.core.year.YearPack;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Anlage N, with one block for each employer, as Mein ELSTER has one entry for each
 * Lohnsteuerbescheinigung. The app does not add the employers together on the review.
 */
public final class AnlageN implements FormModule {

  public static final String ID = "anlage-n";

  private static final String WORK = "Werbungskosten";

  /** The line of an expense topic comes from the {@link FormLayout}, by the name of the topic. */
  private record ExpenseLine(ExpenseTopic topic, String label) {}

  private static final List<ExpenseLine> EXPENSE_LINES =
      List.of(
          new ExpenseLine(ExpenseTopic.WORK_ASSOCIATIONS, "Beiträge zu Berufsverbänden"),
          new ExpenseLine(ExpenseTopic.WORK_EQUIPMENT, "Aufwendungen für Arbeitsmittel"),
          new ExpenseLine(ExpenseTopic.WORK_TRAINING, "Fortbildungskosten"),
          new ExpenseLine(ExpenseTopic.WORK_OTHER, "Weitere Werbungskosten"));

  private static final Pattern EMPLOYER_ID = Pattern.compile("employer(\\d+)-(.+)");

  private final YearPack pack;
  private final FormLayout layout;

  public AnlageN(YearPack pack, FormLayout layout) {
    this.pack = pack;
    this.layout = layout;
  }

  /**
   * The ID of a field of one employer. The first employer keeps the plain ID, so the answers that
   * the app saved before the list of employers stay valid. Employer 2 has {@code
   * employer2-<id>}.
   */
  public static String employerFieldId(int employer, String fieldId) {
    return employer == 1 ? fieldId : "employer" + employer + "-" + fieldId;
  }

  /** The employer of a field ID. A plain ID belongs to the first employer. */
  public static int employerOf(String fieldId) {
    Matcher matcher = EMPLOYER_ID.matcher(fieldId);
    return matcher.matches() ? Integer.parseInt(matcher.group(1)) : 1;
  }

  /** The number of employers in the answers. The form always has the first employer. */
  public static int employerCount(Answers answers) {
    int count = 1;
    for (String fieldId : answers.values().keySet()) {
      Matcher matcher = EMPLOYER_ID.matcher(fieldId);
      if (matcher.matches()) {
        count = Math.max(count, Integer.parseInt(matcher.group(1)));
      }
    }
    return count;
  }

  /** The employer payments for commuting of all employers together. */
  public static Money employerCommute(Answers answers) {
    Money total = Money.ofCents(0);
    for (int employer = 1; employer <= employerCount(answers); employer++) {
      total = total.plus(answers.money(employerFieldId(employer, "employerCommute")));
    }
    return total;
  }

  @Override
  public String id() {
    return ID;
  }

  @Override
  public String title() {
    return "Anlage N";
  }

  /** The fields of the first employer and the work expenses. */
  @Override
  public List<Field> fields() {
    return fields(new Answers(Map.of()));
  }

  /** One block of fields for each employer, then the work expenses. */
  @Override
  public List<Field> fields(Answers answers) {
    return layout.fields(employerCount(answers));
  }

  @Override
  public Optional<Field> field(String fieldId) {
    return layout.field(fieldId);
  }

  /** The rules of the employer section for each employer, then the rules of the work section. */
  @Override
  public List<String> check(Answers answers) {
    return layout.check(answers, pack);
  }

  /** One block for each employer in the order of the form, then the work expenses by line. */
  @Override
  public List<ReviewLine> review(Answers answers, List<Expense> expenses) {
    List<ReviewLine> employers = new ArrayList<>();
    List<ReviewLine> work = new ArrayList<>();
    for (ReviewLine line : FormModule.super.review(answers, expenses)) {
      (line.section().equals(WORK) ? work : employers).add(line);
    }
    for (ExpenseLine expenseLine : EXPENSE_LINES) {
      work.addAll(expenseLines(expenseLine, expenses));
    }
    // A year without the lines of its form keeps the order of the form.
    work.sort(
        Comparator.comparingInt(
            line -> line.line().isEmpty() ? Integer.MAX_VALUE : Integer.parseInt(line.line())));
    employers.addAll(work);
    return employers;
  }

  private List<ReviewLine> expenseLines(ExpenseLine expenseLine, List<Expense> expenses) {
    List<Expense> matching =
        expenses.stream().filter(e -> e.purpose().topic() == expenseLine.topic()).toList();
    if (matching.isEmpty()) {
      return List.of();
    }
    String line = layout.line(expenseLine.topic().name());
    List<ReviewLine> lines = new ArrayList<>();
    for (Expense expense : matching) {
      lines.add(
          new ReviewLine(
              WORK,
              line,
              expense.purpose().description()
                  + ", bezahlt am "
                  + GermanFormats.format(expense.payment().paidOn()),
              GermanFormats.format(expense.payment().amount()),
              Source.of(expense.evidence())));
    }
    lines.add(
        new ReviewLine(
            WORK,
            line,
            expenseLine.label() + ": Summe",
            GermanFormats.format(ExpenseSummary.of(matching).totalFor(expenseLine.topic())),
            new Source.Sum()));
    return lines;
  }
}
