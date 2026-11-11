package io.github.colla69.makemytaxes.core.form;

import io.github.colla69.makemytaxes.core.year.YearPack;
import java.util.List;
import java.util.Map;

/**
 * The forms of 2025: ESt 1 A, Anlage N and Anlage Vorsorgeaufwand, with the lines of 2025. The
 * sources are named in {@code Definition2025Test}. The sections are the ones of 2022, except the
 * home office help of Anlage N, which follows the rule of 2025.
 */
final class Definition2025 {

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
   * The name and the period of the employment have no line on Anlage N. Lines 31 to 33 split the
   * distance by the means of transport. The help of the field names the line for each choice.
   */
  private static final Map<String, String> ANLAGE_N_LINES =
      Map.ofEntries(
          Map.entry("taxClass", "4"),
          Map.entry("grossWage", "5"),
          Map.entry("wageTax", "6"),
          Map.entry("solidarity", "7"),
          Map.entry("churchTax", "8"),
          Map.entry("shortTimeWork", "20"),
          Map.entry("workplace", "27"),
          Map.entry("commuteDays", "29"),
          Map.entry("commuteKm", "30"),
          Map.entry("commuteTransport", "31 bis 33"),
          Map.entry("employerCommute", "51"),
          Map.entry("WORK_ASSOCIATIONS", "53"),
          Map.entry("WORK_EQUIPMENT", "54"),
          Map.entry("homeOfficeDays", "58"),
          Map.entry("WORK_TRAINING", "60"),
          Map.entry("WORK_OTHER", "62"));

  private Definition2025() {}

  static YearDefinition of(YearPack pack) {
    WageTaxCertificate certificate = WageTaxCertificate2025.LAYOUT;
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
                List.of(Sections.EMPLOYER, Sections.WORK_2025), ANLAGE_N_LINES, certificate),
            AnlageVorsorgeaufwand.ID,
            new FormLayout(
                List.of(Sections.SOCIAL_INSURANCE, Sections.PRIVATE_HEALTH),
                Map.of(),
                certificate)),
        true);
  }
}
