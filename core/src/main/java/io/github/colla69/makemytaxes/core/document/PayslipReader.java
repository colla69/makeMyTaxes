package io.github.colla69.makemytaxes.core.document;

import io.github.colla69.makemytaxes.core.GermanFormats;
import io.github.colla69.makemytaxes.core.expense.Money;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Finds the taxable gross pay and the wage tax in the text of a monthly payslip. It reads two
 * layouts:
 *
 * <ul>
 *   <li>A DATEV "Brutto/Netto-Abrechnung" names the month as a word, such as "Januar 2022", and
 *       shows one amount after each label: "Steuer-Brutto 3.000,00" and "Lohnsteuer 400,00".
 *   <li>An "Entgeltabrechnung" names the period as dates, "Zeitraum vom 01.03. bis 31.03.2025", and
 *       splits each amount into running pay ("Steuerbrutto, lfd.") and one-off pay ("Steuerbrutto,
 *       EZ"). The amount is the sum of both lines. Each line shows the amount of the month first,
 *       then the sum of the year. In a month without one-off pay, the EZ line shows only the sum of
 *       the year, so the one-off pay of the month is 0,00. One file can hold corrections of other
 *       months of the year, each under its own period, with the change as one amount that can be
 *       negative: "Lohnsteuer, lfd. 10,00 -". {@link #read} reads only the text under the period
 *       of the month. {@link #readContent} also reads the corrections and the year column.
 * </ul>
 *
 * <p>The reader never guesses. It reads nothing if the text does not name the month and the year.
 * An amount is not found if its label is missing, if the label has two different amounts, or if
 * the amount has a minus sign. One-off pay without a line for running pay is not read either.
 */
public final class PayslipReader {

  private static final String AMOUNT = "(-\\s*)?((?:\\d{1,3}(?:\\.\\d{3})+|\\d+),\\d{2})(\\s*-)?";

  private static final Pattern TAXABLE_GROSS =
      Pattern.compile("(?:^|\\s)Steuer-?[Bb]rutto:?\\s+" + AMOUNT + "(?=\\s|$)");

  private static final Pattern WAGE_TAX =
      Pattern.compile("(?:^|\\s)Lohnsteuer:?\\s+" + AMOUNT + "(?=\\s|$)");

  /** The sum of the year, the second column of a split line. Its sign does not matter here. */
  private static final String YEAR_AMOUNT = "-?\\s*(?:\\d{1,3}(?:\\.\\d{3})+|\\d+),\\d{2}(?:\\s*-)?";

  /** The year of the start date is optional: "vom 01.03. bis 31.03.2025". */
  private static final Pattern PERIOD =
      Pattern.compile(
          "Zeitraum\\s+vom\\s+\\d{2}\\.(\\d{2})\\.(\\d{4})?\\s+bis\\s+\\d{2}\\.(\\d{2})\\.(\\d{4})");

  private static final String GROSS_LABEL = "Steuer-?[Bb]rutto";

  private static final String WAGE_TAX_LABEL = "Lohnsteuer";

  private static final PayslipValues NO_CHANGE =
      new PayslipValues(Optional.of(Money.ofCents(0)), Optional.of(Money.ofCents(0)));

  private PayslipReader() {}

  public static PayslipValues read(String text, YearMonth month) {
    List<MatchResult> periods = PERIOD.matcher(text).results().toList();
    if (!periods.isEmpty()) {
      return readEntgeltabrechnung(
          Optional.ofNullable(statementsByMonth(text, periods).get(month)));
    }
    if (!namesMonth(text, month)) {
      return PayslipValues.NONE;
    }
    return new PayslipValues(amountOf(text, TAXABLE_GROSS), amountOf(text, WAGE_TAX));
  }

  /**
   * The amounts of the month, the corrections of other months of the same year, and the year
   * column of the month. Only an Entgeltabrechnung has corrections and a year column.
   */
  public static PayslipContent readContent(String text, YearMonth month) {
    List<MatchResult> periods = PERIOD.matcher(text).results().toList();
    if (periods.isEmpty()) {
      return new PayslipContent(read(text, month), List.of(), PayslipValues.NONE);
    }
    Map<YearMonth, String> statements = statementsByMonth(text, periods);
    Optional<String> own = Optional.ofNullable(statements.get(month));
    return new PayslipContent(
        readEntgeltabrechnung(own),
        correctionsOf(statements, month),
        own.map(PayslipReader::yearToDate).orElse(PayslipValues.NONE));
  }

  private static PayslipValues readEntgeltabrechnung(Optional<String> statement) {
    if (statement.isEmpty()) {
      return PayslipValues.NONE;
    }
    String text = statement.get();
    return new PayslipValues(sumOf(text, GROSS_LABEL), sumOf(text, WAGE_TAX_LABEL));
  }

  /**
   * The text under each period of one month, up to the next period, by month. A statement over
   * several pages can repeat its period on each page.
   */
  private static Map<YearMonth, String> statementsByMonth(String text, List<MatchResult> periods) {
    Map<YearMonth, String> statements = new TreeMap<>();
    for (int i = 0; i < periods.size(); i++) {
      MatchResult period = periods.get(i);
      int end = i + 1 < periods.size() ? periods.get(i + 1).start() : text.length();
      String part = text.substring(period.end(), end);
      monthOf(period).ifPresent(month -> statements.merge(month, part, (a, b) -> a + "\n" + b));
    }
    return statements;
  }

  /** The month of a period that starts and ends in the same month. */
  private static Optional<YearMonth> monthOf(MatchResult period) {
    int startMonth = Integer.parseInt(period.group(1));
    String startYear = period.group(2);
    int endMonth = Integer.parseInt(period.group(3));
    int endYear = Integer.parseInt(period.group(4));
    if (startMonth != endMonth || (startYear != null && Integer.parseInt(startYear) != endYear)) {
      return Optional.empty();
    }
    return Optional.of(YearMonth.of(endYear, endMonth));
  }

  private static List<PayslipCorrection> correctionsOf(
      Map<YearMonth, String> statements, YearMonth month) {
    return statements.entrySet().stream()
        .filter(entry -> entry.getKey().getYear() == month.getYear())
        .filter(entry -> entry.getKey().isBefore(month))
        .map(entry -> new PayslipCorrection(entry.getKey(), changesOf(entry.getValue())))
        .filter(correction -> !correction.values().equals(NO_CHANGE))
        .toList();
  }

  private static PayslipValues changesOf(String correction) {
    return new PayslipValues(
        changeOf(correction, GROSS_LABEL), changeOf(correction, WAGE_TAX_LABEL));
  }

  /**
   * The change of the running pay: the first amount on the "lfd." line, with its sign. Without the
   * line, nothing changed. A correction with an EZ line is not read, because an EZ line can show
   * the sum of the year alone, and the reader cannot tell the columns apart.
   */
  private static Optional<Money> changeOf(String correction, String label) {
    if (Pattern.compile("(?:^|\\s)" + label + ",\\s*EZ(?=\\s|$)").matcher(correction).find()) {
      return Optional.empty();
    }
    Pattern running =
        Pattern.compile("(?:^|\\s)" + label + ",\\s*lfd\\.\\s+" + AMOUNT + "(?=\\s|$)");
    if (!running.matcher(correction).find()) {
      return Optional.of(Money.ofCents(0));
    }
    return signedAmountOf(correction, running);
  }

  /** The year column of the "lfd." line, plus the year column of the EZ line if there is one. */
  private static PayslipValues yearToDate(String statement) {
    return new PayslipValues(
        yearSumOf(statement, GROSS_LABEL), yearSumOf(statement, WAGE_TAX_LABEL));
  }

  private static Optional<Money> yearSumOf(String statement, String label) {
    String endOfLine = "(?=[ \\t]*(?:\\r?\\n|\\z))";
    Pattern running =
        Pattern.compile(
            "(?:^|\\s)" + label + ",\\s*lfd\\.[ \\t]+" + YEAR_AMOUNT + "[ \\t]+" + AMOUNT
                + endOfLine);
    Optional<Money> runningSum = signedAmountOf(statement, running);
    String oneOffLabel = "(?:^|\\s)" + label + ",\\s*EZ(?=\\s|$)";
    if (runningSum.isEmpty() || !Pattern.compile(oneOffLabel).matcher(statement).find()) {
      return runningSum;
    }
    Pattern oneOff =
        Pattern.compile(
            "(?:^|\\s)" + label + ",\\s*EZ[ \\t]+(?:" + YEAR_AMOUNT + "[ \\t]+)?" + AMOUNT
                + endOfLine);
    return signedAmountOf(statement, oneOff).map(runningSum.get()::plus);
  }

  /** The one amount, with its sign, that the pattern finds. Two different amounts are not read. */
  private static Optional<Money> signedAmountOf(String text, Pattern pattern) {
    Set<Money> amounts =
        pattern.matcher(text).results().map(PayslipReader::signed).collect(Collectors.toSet());
    return amounts.size() == 1 ? Optional.of(amounts.iterator().next()) : Optional.empty();
  }

  private static Money signed(MatchResult amount) {
    Money money = GermanFormats.parseMoney(amount.group(2));
    boolean negative = amount.group(1) != null || amount.group(3) != null;
    return negative ? Money.ofCents(-money.cents()) : money;
  }

  private static boolean namesMonth(String text, YearMonth month) {
    String name = month.getMonth().getDisplayName(TextStyle.FULL, Locale.GERMAN);
    return Pattern.compile(
            "(?:^|\\s)" + Pattern.quote(name) + "\\s+" + month.getYear() + "(?!\\d)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
        .matcher(text)
        .find();
  }

  /** The running pay, plus the one-off pay if the statement has a line for it. */
  private static Optional<Money> sumOf(String text, String label) {
    Optional<Money> running = amountOf(text, splitLine(label, "lfd\\."));
    String oneOffLabel = "(?:^|\\s)" + label + ",\\s*EZ(?=\\s|$)";
    if (running.isEmpty() || !Pattern.compile(oneOffLabel).matcher(text).find()) {
      return running;
    }
    return oneOffOfMonth(text, label).map(running.get()::plus);
  }

  /**
   * The one-off pay of the month. In a month without one-off pay, the EZ line shows only the sum
   * of the year, and the one-off pay of the month is 0,00. The text has no x positions, so the
   * reader tells the columns apart by their count: an amount of the month is part of the sum of
   * the year, so a line with an amount of the month always has the year column too. A single
   * negative amount, or both kinds of line in one statement, is not read.
   */
  private static Optional<Money> oneOffOfMonth(String text, String label) {
    Pattern twoColumns = splitLine(label, "EZ");
    Pattern yearOnly = yearOnlyLine(label);
    boolean hasTwoColumns = twoColumns.matcher(text).find();
    boolean hasYearOnly = yearOnly.matcher(text).find();
    if (hasTwoColumns && hasYearOnly) {
      return Optional.empty();
    }
    if (hasYearOnly) {
      return amountOf(text, yearOnly).map(yearSum -> Money.ofCents(0));
    }
    return amountOf(text, twoColumns);
  }

  /** "Label, EZ" with one amount and nothing else up to the end of the line. */
  private static Pattern yearOnlyLine(String label) {
    return Pattern.compile(
        "(?:^|\\s)" + label + ",\\s*EZ[ \\t]+" + AMOUNT + "(?=[ \\t]*(?:\\r?\\n|\\z))");
  }

  /**
   * The amount of the month and the sum of the year, as two amounts after "Label, suffix". A line
   * with one amount does not match, because the reader cannot tell which column the amount is in.
   */
  private static Pattern splitLine(String label, String suffix) {
    return Pattern.compile(
        "(?:^|\\s)" + label + ",\\s*" + suffix + "\\s+" + AMOUNT + "\\s+" + YEAR_AMOUNT
            + "(?=\\s|$)");
  }

  /** The one amount after the label. Two different amounts, or a negative one, are not read. */
  private static Optional<Money> amountOf(String text, Pattern label) {
    Matcher matcher = label.matcher(text);
    Set<String> amounts =
        matcher.results()
            .map(result -> result.group(1) != null || result.group(3) != null ? "" : result.group(2))
            .collect(Collectors.toSet());
    if (amounts.size() != 1 || amounts.contains("")) {
      return Optional.empty();
    }
    return Optional.of(GermanFormats.parseMoney(amounts.iterator().next()));
  }
}
