package io.github.colla69.makemytaxes.core.form;

import io.github.colla69.makemytaxes.core.year.YearPack;
import java.util.List;
import java.util.Map;

/** The forms of 2022: ESt 1 A, Anlage N and Anlage Vorsorgeaufwand, with the lines of 2022. */
final class Definition2022 {

  private static final Map<String, String> MAIN_LINES =
      Map.ofEntries(
          Map.entry("taxNumber", "4"),
          Map.entry("taxOffice", "5"),
          Map.entry("taxId", "8"),
          Map.entry("birthDate", "8"),
          Map.entry("lastName", "9"),
          Map.entry("firstName", "10"),
          Map.entry("street", "12"),
          Map.entry("houseNumber", "13"),
          Map.entry("postalCode", "14"),
          Map.entry("city", "15"),
          Map.entry("occupation", "17"),
          Map.entry("iban", "31"),
          Map.entry("benefits", "43"),
          Map.entry("notes", "45"));

  /** The name and the period of the employment have no line on Anlage N. */
  private static final Map<String, String> ANLAGE_N_LINES =
      Map.ofEntries(
          Map.entry("taxClass", "5"),
          Map.entry("grossWage", "6"),
          Map.entry("wageTax", "7"),
          Map.entry("solidarity", "8"),
          Map.entry("churchTax", "9"),
          Map.entry("shortTimeWork", "28"),
          Map.entry("workplace", "31"),
          Map.entry("commuteDays", "35"),
          Map.entry("commuteKm", "35"),
          Map.entry("commuteTransport", "35"),
          Map.entry("employerCommute", "39"),
          Map.entry("WORK_ASSOCIATIONS", "41"),
          Map.entry("WORK_EQUIPMENT", "42"),
          Map.entry("homeOfficeDays", "45"),
          Map.entry("WORK_TRAINING", "46"),
          Map.entry("WORK_OTHER", "48"));

  private Definition2022() {}

  static YearDefinition of(YearPack pack) {
    WageTaxCertificate certificate = WageTaxCertificate2022.LAYOUT;
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
                List.of(Sections.EMPLOYER, Sections.WORK), ANLAGE_N_LINES, certificate),
            AnlageVorsorgeaufwand.ID,
            new FormLayout(
                List.of(Sections.SOCIAL_INSURANCE, Sections.PRIVATE_HEALTH),
                Map.of(),
                certificate)));
  }
}
