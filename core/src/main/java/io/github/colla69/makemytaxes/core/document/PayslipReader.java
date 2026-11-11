package io.github.colla69.makemytaxes.core.document;

import io.github.colla69.makemytaxes.core.GermanFormats;
import io.github.colla69.makemytaxes.core.expense.Money;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Finds the taxable gross pay and the wage tax in the text of a monthly payslip, such as a DATEV
 * "Brutto/Netto-Abrechnung". It looks for a label that an amount follows on the same line:
 * "Steuer-Brutto 3.000,00" and "Lohnsteuer 400,00".
 *
 * <p>The reader never guesses. It reads nothing if the text does not name the month and the year,
 * such as "Januar 2022". An amount is not found if its label is missing, if the label has two
 * different amounts, or if the amount has a minus sign.
 */
public final class PayslipReader {

  private static final String AMOUNT = "(-\\s*)?((?:\\d{1,3}(?:\\.\\d{3})+|\\d+),\\d{2})(\\s*-)?";

  private static final Pattern TAXABLE_GROSS =
      Pattern.compile("(?:^|\\s)Steuer-?[Bb]rutto:?\\s+" + AMOUNT + "(?=\\s|$)");

  private static final Pattern WAGE_TAX =
      Pattern.compile("(?:^|\\s)Lohnsteuer:?\\s+" + AMOUNT + "(?=\\s|$)");

  private PayslipReader() {}

  public static PayslipValues read(String text, YearMonth month) {
    if (!namesMonth(text, month)) {
      return PayslipValues.NONE;
    }
    return new PayslipValues(amountOf(text, TAXABLE_GROSS), amountOf(text, WAGE_TAX));
  }

  private static boolean namesMonth(String text, YearMonth month) {
    String name = month.getMonth().getDisplayName(TextStyle.FULL, Locale.GERMAN);
    return Pattern.compile(
            "(?:^|\\s)" + Pattern.quote(name) + "\\s+" + month.getYear() + "(?!\\d)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
        .matcher(text)
        .find();
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
