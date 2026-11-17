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
 * negative amount, or if the certificate is for another year. A field is empty if its line is
 * printed and holds no amount.
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

  /**
   * An amount on a text line, with a minus sign before or after it. The cents follow a comma, a
   * space, as in the euro and cent columns ("24000 00", "87 50"), or a dot ("50.00"). A line
   * reference such as "15." has no digits right after its dot, and a date such as "01.03." has a
   * dot after the second number, so neither is an amount.
   */
  private static final Pattern AMOUNT =
      Pattern.compile(
          "(?:^|\\s)(-\\s*)?(?:(\\d{1,3}(?:\\.\\d{3})+|\\d+)[, ]|(\\d+)\\.)(\\d{2})(\\s*-)?(?=\\s|$)");

  /**
   * A reference to a line in a label, such as "15.", "15a." or "von 3.", or a rate, such as the
   * "15 %" of line 18. The labels of the lines that can be empty hold no other digits. So a digit
   * that is left after the reader removes the amounts and these parts of labels belongs to an
   * amount the reader cannot read, such as "1.234,56€" or "1.234.56", and the line is not empty.
   */
  private static final Pattern LABEL_NUMBER =
      Pattern.compile("\\d{1,2}[a-c]?\\.(?!\\d)|\\d{1,2}\\s?%");

  private static final Pattern DIGIT = Pattern.compile("\\d");

  /** A date such as "01.03.", "01.03.2022" or "01. 03. 2022", with one space after each dot. */
  private static final String DATE = "(\\d{1,2}\\.\\s?\\d{1,2}\\.(?:\\s?\\d{4})?)";

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
    Amounts amounts = readRightColumn(text.rightColumn(), year, found);
    putAmounts(amounts, found);
    return new CertificateValues(found, emptyFields(amounts));
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

  /** Puts the period of line 1, and gives the amounts and the printed lines of the column. */
  private static Amounts readRightColumn(String column, int year, Map<String, String> found) {
    Map<String, List<String>> amounts = new HashMap<>();
    Set<String> printed = new HashSet<>();
    Set<String> unclear = new HashSet<>();
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
      if (line > 0) {
        printed.add(line + part);
      }
      if (line > 0 && holdsUnreadDigits(text)) {
        unclear.add(line + part);
      }
      Matcher amount = AMOUNT.matcher(text);
      while (line > 0 && amount.find()) {
        amounts.computeIfAbsent(line + part, key -> new ArrayList<>()).add(amountText(amount));
      }
    }
    putPeriod(periodText.toString(), year, found);
    printed.removeAll(unclear);
    return new Amounts(amounts, printed);
  }

  private static boolean holdsUnreadDigits(String text) {
    String withoutAmounts = AMOUNT.matcher(text).replaceAll(" ");
    return DIGIT.matcher(LABEL_NUMBER.matcher(withoutAmounts).replaceAll(" ")).find();
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
    boolean negative = amount.group(1) != null || amount.group(5) != null;
    String euros = amount.group(2) != null ? amount.group(2) : amount.group(3);
    return negative ? UNUSABLE : euros + "," + amount.group(4);
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

  private static Optional<LocalDate> dateOf(String printed, int year) {
    String text = printed.replaceAll("\\s", "");
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
    for (String[] single : SINGLES) {
      amounts.single(single[1]).ifPresent(money -> put(found, single[0], money));
    }
    amounts.sum("22a", "22b").ifPresent(money -> put(found, "pensionEmployer", money));
    amounts.sum("23a", "23b").ifPresent(money -> put(found, "pensionEmployee", money));
    amounts.oneOf("17", "18").ifPresent(money -> put(found, "employerCommute", money));
  }

  /** The fields of the fixed lines, with the line that holds the amount. */
  private static final String[][] SINGLES = {
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

  /**
   * The fields whose lines are all printed and hold no amount. The reader gives no value for them:
   * an empty line is not an amount of 0,00.
   */
  private static Set<String> emptyFields(Amounts amounts) {
    Set<String> empty = new HashSet<>();
    for (String[] single : SINGLES) {
      if (amounts.empty(single[1])) {
        empty.add(single[0]);
      }
    }
    if (amounts.empty("22a", "22b")) {
      empty.add("pensionEmployer");
    }
    if (amounts.empty("23a", "23b")) {
      empty.add("pensionEmployee");
    }
    if (amounts.empty("17", "18")) {
      empty.add("employerCommute");
    }
    return empty;
  }

  private static void put(Map<String, String> found, String fieldId, Money money) {
    found.put(fieldId, GermanFormats.format(money));
  }

  /**
   * The amounts of each line, such as "3" or "22a", as they appear on the certificate, and the
   * lines whose number or letter is printed, without digits that the reader cannot read.
   */
  private record Amounts(Map<String, List<String>> byLine, Set<String> printed) {

    /** True if each line is printed and none holds an amount. */
    boolean empty(String... lines) {
      for (String line : lines) {
        if (!printed.contains(line) || has(line)) {
          return false;
        }
      }
      return true;
    }

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
