package io.github.colla69.makemytaxes.core;

import io.github.colla69.makemytaxes.core.expense.Money;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.regex.Pattern;

/** Reads and writes the formats that the UI uses: 03.02.2022 and 1.234,56. */
public final class GermanFormats {

  private static final DateTimeFormatter DATE_INPUT =
      DateTimeFormatter.ofPattern("d.M.uuuu").withResolverStyle(ResolverStyle.STRICT);
  private static final DateTimeFormatter DATE_OUTPUT = DateTimeFormatter.ofPattern("dd.MM.uuuu");
  private static final Pattern AMOUNT =
      Pattern.compile("(\\d{1,3}(\\.\\d{3})+|\\d+)(,\\d{1,2})?");

  /** Up to 999.999.999,99 euros, so sums of amounts cannot overflow. */
  private static final int MAX_EURO_DIGITS = 9;

  private GermanFormats() {}

  public static Money parseMoney(String input) {
    String text = input.strip();
    if (!AMOUNT.matcher(text).matches()) {
      throw new IllegalArgumentException(
          "Write the amount as 1.234,56, but got: \"" + input + "\".");
    }
    String[] parts = text.replace(".", "").split(",");
    if (parts[0].replaceFirst("^0+", "").length() > MAX_EURO_DIGITS) {
      throw new IllegalArgumentException("This amount is too large: \"" + input + "\".");
    }
    long euros = Long.parseLong(parts[0]);
    long cents = parts.length == 1 ? 0 : Long.parseLong((parts[1] + "0").substring(0, 2));
    return Money.ofCents(euros * 100 + cents);
  }

  public static String format(Money money) {
    long euros = money.cents() / 100;
    long cents = money.cents() % 100;
    String grouped = String.valueOf(euros).replaceAll("(\\d)(?=(\\d{3})+$)", "$1.");
    return "%s,%02d".formatted(grouped, cents);
  }

  public static LocalDate parseDate(String input) {
    try {
      return LocalDate.parse(input.strip(), DATE_INPUT);
    } catch (DateTimeParseException e) {
      throw new IllegalArgumentException(
          "Write the date as dd.mm.yyyy, and use a real date. Got: \"" + input + "\".", e);
    }
  }

  public static String format(LocalDate date) {
    return DATE_OUTPUT.format(date);
  }
}
