package io.github.colla69.makemytaxes.core.form;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.colla69.makemytaxes.core.TaxYear;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The expected lines come from the official sample "Ausdruck der elektronischen
 * Lohnsteuerbescheinigung für 2025", print mark 2.25, which the BMF published with its notice of
 * 20.02.2025, IV C 5 - S 2533/00123/006, BStBl I 2025, 650. It replaced the sample of 24.09.2024
 * (BStBl I 2024, 1262) and changes only line 30, which the app does not read. The basis of the
 * sample is § 51 (4) no. 1 EStG, versions 30.06.2025 and 31.12.2025. Compared with the sample of 2022, the numbered lines of these fields
 * are the same. Line 15 now reads "Leistungen, die dem Progressionsvorbehalt unterliegen (z.B.
 * Lohnersatzleistungen)", and a new line 15a reads "(Saison-)Kurzarbeitergeld in 15. enthalten".
 *
 * <p>All values are made up.
 */
class WageTaxCertificate2025Test {

  private static final WageTaxCertificate CERTIFICATE = WageTaxCertificate2025.LAYOUT;

  private final FormModule anlageN = Forms.find(new TaxYear(2025), AnlageN.ID).orElseThrow();
  private final FormModule vorsorge =
      Forms.find(new TaxYear(2025), AnlageVorsorgeaufwand.ID).orElseThrow();

  @ParameterizedTest(name = "{0} is on {1}")
  @DisplayName("Sample of the Lohnsteuerbescheinigung 2025, print mark 2.25: lines of Anlage N")
  @CsvSource({
    "employedFrom, Zeile 1",
    "employedTo, Zeile 1",
    "grossWage, Zeile 3",
    "wageTax, Zeile 4",
    "solidarity, Zeile 5",
    "churchTax, Zeile 6",
    "shortTimeWork, Zeile 15",
    "employerCommute, Zeile 17 und 18"
  })
  void eachWageFieldShowsItsLine(String fieldId, String line) {
    assertThat(CERTIFICATE.lineOf(fieldId)).hasValue(line);
    assertThat(anlageN.field(fieldId).orElseThrow().label()).startsWith(line + " ");
  }

  @ParameterizedTest(name = "{0} is on {1}")
  @DisplayName(
      "Sample of the Lohnsteuerbescheinigung 2025, print mark 2.25: lines of Anlage Vorsorgeaufwand")
  @CsvSource({
    "pensionEmployer, Zeile 22a/b",
    "pensionEmployee, Zeile 23a/b",
    "healthAllowanceStatutory, Zeile 24a",
    "healthAllowancePrivate, Zeile 24b",
    "careAllowance, Zeile 24c",
    "healthEmployee, Zeile 25",
    "careEmployee, Zeile 26",
    "unemploymentEmployee, Zeile 27"
  })
  void eachSocialInsuranceFieldShowsItsLine(String fieldId, String line) {
    assertThat(CERTIFICATE.lineOf(fieldId)).hasValue(line);
    assertThat(vorsorge.field(fieldId).orElseThrow().label()).startsWith(line + " ");
  }

  @Test
  @DisplayName(
      "Sample of the Lohnsteuerbescheinigung 2025, print mark 2.25: the tax class and the employer"
          + " have no numbered line")
  void theTaxClassAndTheEmployerAreOnTheCertificateWithoutALine() {
    for (String fieldId : new String[] {"taxClass", "employerName"}) {
      assertThat(CERTIFICATE.lineOf(fieldId)).isEmpty();
      assertThat(anlageN.field(fieldId).orElseThrow().label()).doesNotContain("Zeile");
    }
  }

  @Test
  void theSectionsHoldExactlyTheFieldsOfTheCertificate() {
    assertThat(Sections.certificateFieldIds())
        .containsExactlyInAnyOrderElementsOf(CERTIFICATE.fieldIds());
  }

  private static final String LEFT =
      """
      Steuerklasse/Faktor
      1
      Anschrift und Steuernummer des Arbeitgebers:
      Beispiel Werkstatt GmbH
      """;

  private static final String RIGHT =
      """
      1. Bescheinigungszeitraum 01.03.-31.12.
      3. Bruttoarbeitslohn einschl. Sachbezüge ohne 9. 30.000,00
      4. Einbehaltene Lohnsteuer von 3. 4.321,00
      15. Leistungen, die dem Progressionsvorbehalt unterliegen 150,00
      (z.B. Lohnersatzleistungen)
      15a. (Saison-)Kurzarbeitergeld in 15. enthalten 100,00
      17. Steuerfreie Arbeitgeberleistungen, die auf die 300,00
      Entfernungspauschale anzurechnen sind
      """;

  private static CertificateValues read(int printedYear) {
    String page = "Ausdruck der elektronischen Lohnsteuerbescheinigung für " + printedYear;
    return CERTIFICATE.read(new CertificateText(page, LEFT, RIGHT));
  }

  @Test
  @DisplayName(
      "Sample of the Lohnsteuerbescheinigung 2025, print mark 2.25: line 15a is not taken for"
          + " line 15")
  void readsACertificateFor2025() {
    assertThat(read(2025).found())
        .containsExactlyInAnyOrderEntriesOf(
            Map.ofEntries(
                Map.entry("employerName", "Beispiel Werkstatt GmbH"),
                Map.entry("taxClass", "1"),
                Map.entry("employedFrom", "01.03.2025"),
                Map.entry("employedTo", "31.12.2025"),
                Map.entry("grossWage", "30.000,00"),
                Map.entry("wageTax", "4.321,00"),
                Map.entry("shortTimeWork", "150,00"),
                Map.entry("employerCommute", "300,00")));
  }

  @Test
  void aCertificateOfAnotherYearGivesNothing() {
    assertThat(read(2022).found()).isEmpty();
  }
}
