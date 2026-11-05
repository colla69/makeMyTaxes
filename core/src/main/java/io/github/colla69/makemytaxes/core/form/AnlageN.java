package io.github.colla69.makemytaxes.core.form;

import static io.github.colla69.makemytaxes.core.form.Field.optional;
import static io.github.colla69.makemytaxes.core.form.Field.required;

import io.github.colla69.makemytaxes.core.GermanFormats;
import io.github.colla69.makemytaxes.core.expense.Expense;
import io.github.colla69.makemytaxes.core.expense.ExpenseSummary;
import io.github.colla69.makemytaxes.core.expense.ExpenseTopic;
import io.github.colla69.makemytaxes.core.year.YearPack;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Anlage N 2022, for one employer. */
public final class AnlageN implements FormModule {

  public static final String ID = "anlage-n";

  private static final String WAGE = "Angaben zum Arbeitslohn";
  private static final String WORK = "Werbungskosten";

  private record ExpenseLine(ExpenseTopic topic, String line, String label) {}

  private static final List<ExpenseLine> EXPENSE_LINES =
      List.of(
          new ExpenseLine(ExpenseTopic.WORK_ASSOCIATIONS, "41", "Beiträge zu Berufsverbänden"),
          new ExpenseLine(ExpenseTopic.WORK_EQUIPMENT, "42", "Aufwendungen für Arbeitsmittel"),
          new ExpenseLine(ExpenseTopic.WORK_TRAINING, "46", "Fortbildungskosten"),
          new ExpenseLine(ExpenseTopic.WORK_OTHER, "48", "Weitere Werbungskosten"));

  private final YearPack pack;

  public AnlageN(YearPack pack) {
    this.pack = pack;
  }

  @Override
  public String id() {
    return ID;
  }

  @Override
  public String title() {
    return "Anlage N";
  }

  @Override
  public List<Field> fields() {
    return List.of(
        required("taxClass", WAGE, "5", "Steuerklasse", Kind.WHOLE_NUMBER),
        required("grossWage", WAGE, "6", "Bruttoarbeitslohn (EUR)", Kind.MONEY)
            .withHelp("No. 3 of the wage tax certificate (Lohnsteuerbescheinigung)."),
        required("wageTax", WAGE, "7", "Lohnsteuer (EUR)", Kind.MONEY),
        optional("solidarity", WAGE, "8", "Solidaritätszuschlag (EUR)", Kind.MONEY),
        optional("churchTax", WAGE, "9", "Kirchensteuer des Arbeitnehmers (EUR)", Kind.MONEY),
        optional("shortTimeWork", WAGE, "28", "Kurzarbeitergeld (EUR)", Kind.MONEY)
            .withHelp("No. 15 of the wage tax certificate."),
        optional("workplace", WORK, "31", "Erste Tätigkeitsstätte", Kind.TEXT)
            .withHelp("Postal code, city and street."),
        optional("commuteDays", WORK, "35", "Commuting: aufgesucht an Tagen", Kind.WHOLE_NUMBER),
        optional("commuteKm", WORK, "35", "Commuting: einfache Entfernung (km)", Kind.WHOLE_NUMBER)
            .withHelp("Rounded down to full kilometres."),
        optional("commuteTransport", WORK, "35", "Commuting: travelled by", Kind.COMMUTE_TRANSPORT),
        optional("employerCommute", WORK, "39", "Arbeitgeberleistungen (EUR)", Kind.MONEY)
            .withHelp("No. 17 and no. 18 of the wage tax certificate."),
        optional("homeOfficeDays", WORK, "45", "Homeoffice: Kalendertage", Kind.WHOLE_NUMBER)
            .withHelp("Days with work only at home. The tax office calculates the amount."));
  }

  /** A day cannot be both, because the home office amount excludes days at the workplace. */
  @Override
  public List<String> check(Answers answers) {
    long days = answers.wholeNumber("commuteDays") + answers.wholeNumber("homeOfficeDays");
    if (days > pack.daysInYear()) {
      return List.of(
          "Commuting days and home office days total %d, but %d has only %d days."
              .formatted(days, pack.taxYear().value(), pack.daysInYear()));
    }
    return List.of();
  }

  @Override
  public List<ReviewLine> review(Answers answers, List<Expense> expenses) {
    List<ReviewLine> lines = new ArrayList<>(FormModule.super.review(answers, expenses));
    for (ExpenseLine expenseLine : EXPENSE_LINES) {
      lines.addAll(expenseLines(expenseLine, expenses));
    }
    lines.sort(Comparator.comparingInt(line -> Integer.parseInt(line.line())));
    return lines;
  }

  private static List<ReviewLine> expenseLines(ExpenseLine line, List<Expense> expenses) {
    List<Expense> matching =
        expenses.stream().filter(e -> e.purpose().topic() == line.topic()).toList();
    if (matching.isEmpty()) {
      return List.of();
    }
    List<ReviewLine> lines = new ArrayList<>();
    for (Expense expense : matching) {
      lines.add(
          new ReviewLine(
              WORK,
              line.line(),
              expense.purpose().description(),
              GermanFormats.format(expense.payment().amount()),
              evidenceOf(expense)));
    }
    lines.add(
        new ReviewLine(
            WORK,
            line.line(),
            line.label() + ": total",
            GermanFormats.format(ExpenseSummary.of(matching).totalFor(line.topic())),
            ""));
    return lines;
  }

  private static String evidenceOf(Expense expense) {
    String paid = "Paid on " + GermanFormats.format(expense.payment().paidOn());
    return expense.receiptReference().isEmpty()
        ? paid + ", no receipt"
        : paid + ", receipt: " + expense.receiptReference();
  }
}
