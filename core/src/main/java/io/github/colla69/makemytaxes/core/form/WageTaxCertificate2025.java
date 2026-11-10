package io.github.colla69.makemytaxes.core.form;

import java.util.Map;
import java.util.Set;

/**
 * The printed Lohnsteuerbescheinigung for 2025: the numbered line of each field on that
 * certificate. The lines are the same as in 2022. The source is named in {@code
 * WageTaxCertificate2025Test}.
 */
public final class WageTaxCertificate2025 {

  private static final int YEAR = 2025;

  public static final WageTaxCertificate LAYOUT =
      new WageTaxCertificate(
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
              Map.entry("unemploymentEmployee", "27")),
          Set.of("employerName", "taxClass"),
          text -> WageTaxCertificateReader2022.read(text, YEAR));

  private WageTaxCertificate2025() {}
}
