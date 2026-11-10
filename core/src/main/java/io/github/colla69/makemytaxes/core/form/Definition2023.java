package io.github.colla69.makemytaxes.core.form;

import io.github.colla69.makemytaxes.core.year.YearPack;
import java.util.List;
import java.util.Map;

/**
 * The forms of 2023: ESt 1 A, Anlage N and Anlage Vorsorgeaufwand, with the lines of the official
 * forms of 2023 in {@code law/forms/2023}.
 */
final class Definition2023 {

  private static final Map<String, String> MAIN_LINES =
      Map.ofEntries(
          Map.entry("taxNumber", "4"),
          Map.entry("taxOffice", "5"),
          Map.entry("taxId", "8"),
          Map.entry("birthDate", "8"),
          Map.entry("lastName", "9"),
          Map.entry("firstName", "10"),
          Map.entry("occupation", "12"),
          Map.entry("street", "13"),
          Map.entry("houseNumber", "14"),
          Map.entry("postalCode", "15"),
          Map.entry("city", "16"),
          Map.entry("iban", "30"),
          Map.entry("benefits", "35"),
          Map.entry("notes", "37"));

  /**
   * The name and the period of the employment have no line on Anlage N. The transport has no line
   * either: the kilometres go on line 34 for a car and on line 36 for other transport.
   */
  private static final Map<String, String> ANLAGE_N_LINES =
      Map.ofEntries(
          Map.entry("taxClass", "4"),
          Map.entry("grossWage", "5"),
          Map.entry("wageTax", "6"),
          Map.entry("solidarity", "7"),
          Map.entry("churchTax", "8"),
          Map.entry("shortTimeWork", "23"),
          Map.entry("workplace", "30"),
          Map.entry("commuteDays", "32"),
          Map.entry("commuteKm", "33"),
          Map.entry("employerCommute", "54"),
          Map.entry("WORK_ASSOCIATIONS", "56"),
          Map.entry("WORK_EQUIPMENT", "57"),
          Map.entry("homeOfficeDays", "61"),
          Map.entry(Sections.NO_WORKPLACE_DAYS, "62"),
          Map.entry("WORK_TRAINING", "63"),
          Map.entry("WORK_OTHER", "65"));

  private Definition2023() {}

  static YearDefinition of(YearPack pack) {
    WageTaxCertificate certificate = WageTaxCertificate2023.LAYOUT;
    return new YearDefinition(
        pack,
        certificate,
        Map.of(
            MainForm.ID,
            new FormLayout(
                List.of(Sections.GENERAL, Sections.BANK, Sections.BENEFITS, Sections.NOTES),
                MAIN_LINES,
                certificate),
            AnlageN.ID,
            new FormLayout(
                List.of(Sections.EMPLOYER, Sections.WORK_DAY_RATE), ANLAGE_N_LINES, certificate),
            AnlageVorsorgeaufwand.ID,
            new FormLayout(
                List.of(Sections.SOCIAL_INSURANCE, Sections.PRIVATE_HEALTH),
                Map.of(),
                certificate)));
  }
}
