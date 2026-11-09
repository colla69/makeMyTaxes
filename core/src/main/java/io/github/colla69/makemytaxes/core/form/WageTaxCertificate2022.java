package io.github.colla69.makemytaxes.core.form;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The form fields that come from the printed Lohnsteuerbescheinigung for 2022, and the numbered
 * line of each field on that certificate. The source is named in {@code
 * WageTaxCertificate2022Test}. A field ID here is the ID without the prefix of the employer.
 */
public final class WageTaxCertificate2022 {

  private static final Map<String, String> LINES =
      Map.ofEntries(
          Map.entry("employedFrom", "1"),
          Map.entry("employedTo", "1"),
          Map.entry("grossWage", "3"),
          Map.entry("wageTax", "4"),
          Map.entry("solidarity", "5"),
          Map.entry("churchTax", "6"),
          Map.entry("shortTimeWork", "15"),
          Map.entry("employerCommute", "17 und 18"),
          Map.entry("pensionEmployer", "22a/b"),
          Map.entry("pensionEmployee", "23a/b"),
          Map.entry("healthAllowanceStatutory", "24a"),
          Map.entry("healthAllowancePrivate", "24b"),
          Map.entry("careAllowance", "24c"),
          Map.entry("healthEmployee", "25"),
          Map.entry("careEmployee", "26"),
          Map.entry("unemploymentEmployee", "27"));

  /** The certificate shows these values outside its numbered lines. */
  private static final Set<String> WITHOUT_LINE = Set.of("employerName", "taxClass");

  private WageTaxCertificate2022() {}

  /** The text of the line, for example "Zeile 3", or none for a field without a line. */
  public static Optional<String> lineOf(String fieldId) {
    return Optional.ofNullable(LINES.get(fieldId)).map(line -> "Zeile " + line);
  }

  /** True if the value of this field comes from the certificate. */
  public static boolean isOnCertificate(String fieldId) {
    return LINES.containsKey(fieldId) || WITHOUT_LINE.contains(fieldId);
  }

  /** The label of a field with a numbered line: the line, then the text. */
  static String label(String fieldId, String text) {
    return line(fieldId) + " " + text;
  }

  /** The line of a field that has one. */
  static String line(String fieldId) {
    return lineOf(fieldId).orElseThrow();
  }
}
