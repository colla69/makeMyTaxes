package io.github.colla69.makemytaxes.core.form;

import static io.github.colla69.makemytaxes.core.form.Field.optional;
import static io.github.colla69.makemytaxes.core.form.Field.required;

import io.github.colla69.makemytaxes.core.GermanFormats;
import io.github.colla69.makemytaxes.core.expense.Evidence;
import io.github.colla69.makemytaxes.core.expense.Expense;
import io.github.colla69.makemytaxes.core.expense.ExpenseSummary;
import io.github.colla69.makemytaxes.core.expense.ExpenseTopic;
import io.github.colla69.makemytaxes.core.expense.Money;
import io.github.colla69.makemytaxes.core.year.YearPack;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Anlage N 2022, with one block for each employer, as Mein ELSTER has one entry for each
 * Lohnsteuerbescheinigung. The app does not add the employers together on the review.
 */
public final class AnlageN implements FormModule {

  public static final String ID = "anlage-n";

  private static final String WORK = "Werbungskosten";

  private record ExpenseLine(ExpenseTopic topic, String line, String label) {}

  private static final List<ExpenseLine> EXPENSE_LINES =
      List.of(
          new ExpenseLine(ExpenseTopic.WORK_ASSOCIATIONS, "41", "Beiträge zu Berufsverbänden"),
          new ExpenseLine(ExpenseTopic.WORK_EQUIPMENT, "42", "Aufwendungen für Arbeitsmittel"),
          new ExpenseLine(ExpenseTopic.WORK_TRAINING, "46", "Fortbildungskosten"),
          new ExpenseLine(ExpenseTopic.WORK_OTHER, "48", "Weitere Werbungskosten"));

  /**
   * The fields of one Lohnsteuerbescheinigung. A label starts with the number on the certificate.
   * The name and the period have no line on Anlage N.
   */
  private static final List<Field> EMPLOYER_FIELDS =
      List.of(
          required("employerName", "", "", "Name des Arbeitgebers", Kind.TEXT),
          optional("employedFrom", "", "", "Nr. 1 Dauer des Dienstverhältnisses: vom", Kind.DATE),
          optional("employedTo", "", "", "Nr. 1 Dauer des Dienstverhältnisses: bis", Kind.DATE),
          required("taxClass", "", "5", "Steuerklasse", Kind.WHOLE_NUMBER),
          required("grossWage", "", "6", "Nr. 3 Bruttoarbeitslohn (EUR)", Kind.MONEY),
          required("wageTax", "", "7", "Nr. 4 Lohnsteuer (EUR)", Kind.MONEY),
          optional("solidarity", "", "8", "Nr. 5 Solidaritätszuschlag (EUR)", Kind.MONEY),
          optional("churchTax", "", "9", "Nr. 6 Kirchensteuer des Arbeitnehmers (EUR)", Kind.MONEY),
          optional("shortTimeWork", "", "28", "Nr. 15 Kurzarbeitergeld (EUR)", Kind.MONEY),
          optional(
                  "employerCommute",
                  "",
                  "39",
                  "Nr. 17 und Nr. 18 Arbeitgeberleistungen (EUR)",
                  Kind.MONEY)
              .withHelp("Die Summe der Beträge in Nr. 17 und Nr. 18."));

  private static final List<Field> WORK_FIELDS =
      List.of(
          optional("workplace", WORK, "31", "Erste Tätigkeitsstätte", Kind.TEXT)
              .withHelp("Postleitzahl, Ort und Straße."),
          optional(
              "commuteDays", WORK, "35", "Wege zur Arbeit: aufgesucht an Tagen", Kind.WHOLE_NUMBER),
          optional(
                  "commuteKm",
                  WORK,
                  "35",
                  "Wege zur Arbeit: einfache Entfernung (km)",
                  Kind.WHOLE_NUMBER)
              .withHelp("Auf volle Kilometer abgerundet."),
          optional(
              "commuteTransport",
              WORK,
              "35",
              "Wege zur Arbeit: zurückgelegt mit",
              Kind.COMMUTE_TRANSPORT),
          optional("homeOfficeDays", WORK, "45", "Homeoffice: Kalendertage", Kind.WHOLE_NUMBER)
              .withHelp(
                  "Tage, an denen Sie nur zu Hause gearbeitet haben. Das Finanzamt berechnet den"
                      + " Betrag."));

  private static final Pattern EMPLOYER_ID = Pattern.compile("employer(\\d+)-(.+)");

  private final YearPack pack;

  public AnlageN(YearPack pack) {
    this.pack = pack;
  }

  /**
   * The ID of a field of one employer. The first employer keeps the plain ID, so the answers that
   * the app saved before the list of employers stay valid. Employer 2 has {@code
   * employer2-<id>}.
   */
  public static String employerFieldId(int employer, String fieldId) {
    return employer == 1 ? fieldId : "employer" + employer + "-" + fieldId;
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

  public static List<Field> employerFields(int employer) {
    return EMPLOYER_FIELDS.stream()
        .map(
            field ->
                field.inEntry(employerFieldId(employer, field.id()), "Arbeitgeber " + employer))
        .toList();
  }

  /** The employer payments for commuting, line 39, of all employers together. */
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
    List<Field> fields = new ArrayList<>();
    for (int employer = 1; employer <= employerCount(answers); employer++) {
      fields.addAll(employerFields(employer));
    }
    fields.addAll(WORK_FIELDS);
    return fields;
  }

  @Override
  public Optional<Field> field(String fieldId) {
    Matcher matcher = EMPLOYER_ID.matcher(fieldId);
    List<Field> candidates =
        matcher.matches() ? employerFields(Integer.parseInt(matcher.group(1))) : fields();
    return candidates.stream().filter(field -> field.id().equals(fieldId)).findFirst();
  }

  /** A day cannot be both, because the home office amount excludes days at the workplace. */
  @Override
  public List<String> check(Answers answers) {
    List<String> messages = new ArrayList<>();
    for (int employer = 1; employer <= employerCount(answers); employer++) {
      messages.addAll(checkPeriod(answers, employer));
    }
    long days = answers.wholeNumber("commuteDays") + answers.wholeNumber("homeOfficeDays");
    if (days > pack.daysInYear()) {
      messages.add(
          "Tage mit Wegen zur Arbeit und Homeoffice-Tage ergeben zusammen %d, aber %d hat nur %d Tage."
              .formatted(days, pack.taxYear().value(), pack.daysInYear()));
    }
    // The limit of § 9(1) no. 4 EStG depends on the transport, so the amount needs this answer.
    if (answers.has("commuteDays") && !answers.has("commuteTransport")) {
      messages.add("Wählen Sie, womit Sie zur Arbeit gefahren sind. Davon hängt der Höchstbetrag ab.");
    }
    return messages;
  }

  /** The certificate of a year covers days of that year only, and the start comes first. */
  private List<String> checkPeriod(Answers answers, int employer) {
    List<String> messages = new ArrayList<>();
    Optional<LocalDate> from = dateOf(answers, employerFieldId(employer, "employedFrom"));
    Optional<LocalDate> to = dateOf(answers, employerFieldId(employer, "employedTo"));
    int year = pack.taxYear().value();
    for (Optional<LocalDate> date : List.of(from, to)) {
      if (date.isPresent() && date.get().getYear() != year) {
        messages.add(
            "Arbeitgeber %d: Die Dauer des Dienstverhältnisses muss in %d liegen."
                .formatted(employer, year));
        return messages;
      }
    }
    if (from.isPresent() && to.isPresent() && from.get().isAfter(to.get())) {
      messages.add(
          "Arbeitgeber %d: Das Ende des Dienstverhältnisses liegt vor dem Beginn."
              .formatted(employer));
    }
    return messages;
  }

  private static Optional<LocalDate> dateOf(Answers answers, String fieldId) {
    return answers.has(fieldId)
        ? Optional.of(GermanFormats.parseDate(answers.text(fieldId)))
        : Optional.empty();
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
    work.sort(Comparator.comparingInt(line -> Integer.parseInt(line.line())));
    employers.addAll(work);
    return employers;
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
            line.label() + ": Summe",
            GermanFormats.format(ExpenseSummary.of(matching).totalFor(line.topic())),
            ""));
    return lines;
  }

  private static String evidenceOf(Expense expense) {
    String paid = "Bezahlt am " + GermanFormats.format(expense.payment().paidOn());
    Evidence evidence = expense.evidence();
    return evidence.isPresent() || !evidence.hasReceiptNote()
        ? paid + ", " + evidence.label()
        : paid + ", Hinweis: " + evidence.receiptNote() + ", " + evidence.label();
  }
}
