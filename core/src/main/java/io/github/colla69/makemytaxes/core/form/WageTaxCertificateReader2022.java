package io.github.colla69.makemytaxes.core.form;

import io.github.colla69.makemytaxes.core.GermanFormats;
import io.github.colla69.makemytaxes.core.expense.Money;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the values of the fields of {@link WageTaxCertificate2022} in the text of a printed
 * Lohnsteuerbescheinigung for 2022. The layout is the one of the official sample, which {@code
 * WageTaxCertificate2022Test} names.
 *
 * <p>The reader never guesses. A field is not found if its line is missing, holds two amounts or a
 * negative amount, or if the certificate is for another year.
 */
public final class WageTaxCertificateReader2022 {

  private static final int YEAR = 2022;
  private static final int LAST_LINE = 34;

  private static final Pattern TITLE =
      Pattern.compile("Lohnsteuerbescheinigung\\s+für\\s+(\\d{4})");

  /** The number of a line, such as "3. Bruttoarbeitslohn" or "15. (Saison-)Kurzarbeitergeld". */
  private static final Pattern LINE_NUMBER = Pattern.compile("(?:^|\\s)(\\d{1,2})\\.\\s+[\\p{Lu}(]");

  /** The letter of a part of lines 22 to 24, such as "a) zur gesetzlichen Rentenversicherung". */
  private static final Pattern PART = Pattern.compile("(?:^|\\s)([abc])\\)\\s");

  /** An amount on a text line, with a minus sign before or after it. */
  private static final Pattern AMOUNT =
      Pattern.compile(
          "(?:^|\\s)(-\\s*)?((?:\\d{1,3}(?:\\.\\d{3})+|\\d+),\\d{2})(\\s*-)?(?=\\s|$)");

  private static final String DATE = "(\\d{1,2}\\.\\d{1,2}\\.(?:\\d{4})?)";

  private static final Pattern ANY_DATE = Pattern.compile(DATE);

  /**
   * The period of line 1, such as "01.01.-31.12.", "01.01.2022 bis 31.12.2022" or two dates with
   * only space between them, as when the dates stand on two text lines.
   */
  private static final Pattern PERIOD =
      Pattern.compile(DATE + "(?:\\s*(?:-|–|bis)\\s*|\\s+)" + DATE);

  private static final Pattern TAX_CLASS = Pattern.compile("([1-6])(?:\\s*/\\s*\\d+,\\d+)?");

  /** Marks an amount that the reader must not use, such as a negative one. */
  private static final String UNUSABLE = "";

  /** Line 15a of the sample for 2025: "(Saison-)Kurzarbeitergeld in 15. enthalten". */
  private static final Pattern LINE_15A = Pattern.compile("(?:^|\\s)15a\\.\\s");

  private WageTaxCertificateReader2022() {}

  public static CertificateValues read(CertificateText text) {
    return read(text, YEAR);
  }

  /**
   * Reads a certificate for the given year. The sample for 2025 has the same numbered lines for
   * the fields of {@link WageTaxCertificate2025}, and adds line 15a, which the reader keeps apart
   * from line 15.
   */
  public static CertificateValues read(CertificateText text, int year) {
    Map<String, String> found = new HashMap<>();
    if (yearOf(text.page()).filter(printed -> printed == year).isEmpty()) {
      return new CertificateValues(found);
    }
    readLeftColumn(text.leftColumn(), found);
    readRightColumn(text.rightColumn(), year, found);
    return new CertificateValues(found);
  }

  private static Optional<Integer> yearOf(String page) {
    Matcher matcher = TITLE.matcher(page);
    return matcher.find() ? Optional.of(Integer.parseInt(matcher.group(1))) : Optional.empty();
  }

  private static void readLeftColumn(String column, Map<String, String> found) {
    List<String> lines = column.lines().map(String::strip).toList();
    valueAfter(lines, "Anschrift und Steuernummer des Arbeitgebers")
        .ifPresent(name -> found.put("employerName", name));
    valueAfter(lines, "Steuerklasse/Faktor")
        .map(TAX_CLASS::matcher)
        .filter(Matcher::matches)
        .ifPresent(matcher -> found.put("taxClass", matcher.group(1)));
  }

  /** The first line with text after the line of the label. */
  private static Optional<String> valueAfter(List<String> lines, String label) {
    for (int i = 0; i < lines.size(); i++) {
      if (lines.get(i).contains(label)) {
        return lines.subList(i + 1, lines.size()).stream().filter(l -> !l.isEmpty()).findFirst();
      }
    }
    return Optional.empty();
  }

  private static void readRightColumn(String column, int year, Map<String, String> found) {
    Map<String, List<String>> amounts = new HashMap<>();
    StringBuilder periodText = new StringBuilder();
    int line = 0;
    String part = "";
    for (String text : column.lines().toList()) {
      int number = lineNumberIn(text, line);
      if (number != line) {
        line = number;
        part = "";
      }
      Matcher partMatcher = PART.matcher(text);
      if (line >= 22 && line <= 24 && partMatcher.find()) {
        part = partMatcher.group(1);
      }
      if (line == 15 && LINE_15A.matcher(text).find()) {
        part = "a";
      }
      if (line == 1) {
        periodText.append(text).append(' ');
      }
      Matcher amount = AMOUNT.matcher(text);
      while (line > 0 && amount.find()) {
        amounts.computeIfAbsent(line + part, key -> new ArrayList<>()).add(amountText(amount));
      }
    }
    putPeriod(periodText.toString(), year, found);
    putAmounts(new Amounts(amounts), found);
  }

  /** A new line number must be higher, because a label such as "von 9. und 10." names others. */
  private static int lineNumberIn(String text, int current) {
    Matcher matcher = LINE_NUMBER.matcher(text);
    while (matcher.find()) {
      int number = Integer.parseInt(matcher.group(1));
      if (number > current && number <= LAST_LINE) {
        return number;
      }
    }
    return current;
  }

  private static String amountText(Matcher amount) {
    boolean negative = amount.group(1) != null || amount.group(3) != null;
    return negative ? UNUSABLE : amount.group(2);
  }

  /**
   * Line 1 shows the period, with or without the year. The text of line 1 may print the period more
   * than once. The reader takes it only if each print gives the same dates, and if no date stands
   * outside a period.
   */
  private static void putPeriod(String text, int year, Map<String, String> found) {
    Set<List<LocalDate>> periods = new HashSet<>();
    Matcher matcher = PERIOD.matcher(text);
    int dates = 0;
    while (matcher.find()) {
      Optional<LocalDate> from = dateOf(matcher.group(1), year);
      Optional<LocalDate> to = dateOf(matcher.group(2), year);
      if (from.isEmpty() || to.isEmpty() || from.get().isAfter(to.get())) {
        return;
      }
      periods.add(List.of(from.get(), to.get()));
      dates += 2;
    }
    if (periods.size() == 1 && ANY_DATE.matcher(text).results().count() == dates) {
      List<LocalDate> period = periods.iterator().next();
      found.put("employedFrom", GermanFormats.format(period.get(0)));
      found.put("employedTo", GermanFormats.format(period.get(1)));
    }
  }

  private static Optional<LocalDate> dateOf(String text, int year) {
    String withYear = text.endsWith(".") ? text + year : text;
    try {
      return Optional.of(GermanFormats.parseDate(withYear)).filter(date -> date.getYear() == year);
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  /**
   * Anlage Vorsorgeaufwand 2022 takes lines 22 a/b and 23 a/b in one box each, so the reader adds
   * the two parts. Anlage N 2022 takes lines 17 and 18 in two boxes, so the reader does not add
   * them. The sources are named in {@code WageTaxCertificateReader2022Test}.
   */
  private static void putAmounts(Amounts amounts, Map<String, String> found) {
    String[][] singles = {
      {"grossWage", "3"},
      {"wageTax", "4"},
      {"solidarity", "5"},
      {"churchTax", "6"},
      {"shortTimeWork", "15"},
      {"healthAllowanceStatutory", "24a"},
      {"healthAllowancePrivate", "24b"},
      {"careAllowance", "24c"},
      {"healthEmployee", "25"},
      {"careEmployee", "26"},
      {"unemploymentEmployee", "27"}
    };
    for (String[] single : singles) {
      amounts.single(single[1]).ifPresent(money -> put(found, single[0], money));
    }
    amounts.sum("22a", "22b").ifPresent(money -> put(found, "pensionEmployer", money));
    amounts.sum("23a", "23b").ifPresent(money -> put(found, "pensionEmployee", money));
    amounts.oneOf("17", "18").ifPresent(money -> put(found, "employerCommute", money));
  }

  private static void put(Map<String, String> found, String fieldId, Money money) {
    found.put(fieldId, GermanFormats.format(money));
  }

  /** The amounts of each line, such as "3" or "22a", as they appear on the certificate. */
  private record Amounts(Map<String, List<String>> byLine) {

    /** The amount of a line with exactly one usable amount. */
    Optional<Money> single(String line) {
      List<String> texts = byLine.getOrDefault(line, List.of());
      if (texts.size() != 1 || texts.getFirst().equals(UNUSABLE)) {
        return Optional.empty();
      }
      try {
        return Optional.of(GermanFormats.parseMoney(texts.getFirst()));
      } catch (IllegalArgumentException e) {
        return Optional.empty();
      }
    }

    boolean has(String line) {
      return byLine.containsKey(line);
    }

    /** The sum of the lines that have an amount. Nothing if a line cannot be read. */
    Optional<Money> sum(String first, String second) {
      if (!has(first) && !has(second)) {
        return Optional.empty();
      }
      Money total = Money.ofCents(0);
      for (String line : List.of(first, second)) {
        if (has(line)) {
          Optional<Money> money = single(line);
          if (money.isEmpty()) {
            return Optional.empty();
          }
          total = total.plus(money.get());
        }
      }
      return Optional.of(total);
    }

    /** The amount of the one line that has an amount. Nothing if both lines have one. */
    Optional<Money> oneOf(String first, String second) {
      if (has(first) == has(second)) {
        return Optional.empty();
      }
      return single(has(first) ? first : second);
    }
  }
}
