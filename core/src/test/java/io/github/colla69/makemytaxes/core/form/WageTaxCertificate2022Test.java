package io.github.colla69.makemytaxes.core.form;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.colla69.makemytaxes.core.TaxYear;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The expected lines come from the official sample "Ausdruck der elektronischen
 * Lohnsteuerbescheinigung für 2022", print mark 3.21, published by ELSTER as
 * https://www.elster.de/eportal/attachments/help_lstb_2022/Muster_LSTB_2022.pdf. The basis of the
 * sample is § 51 (4) no. 1 EStG, versions 30.06.2022 and 31.12.2022. The notice of the BMF of
 * 15.07.2022, IV C 5 - S 2533/19/10030 :003, BStBl I 2022, 1203, adds the letter E to line 2 and
 * says that nothing else changed.
 */
class WageTaxCertificate2022Test {

  private static final WageTaxCertificate CERTIFICATE = WageTaxCertificate2022.LAYOUT;

  private final FormModule anlageN = Forms.find(new TaxYear(2022), AnlageN.ID).orElseThrow();
  private final FormModule vorsorge =
      Forms.find(new TaxYear(2022), AnlageVorsorgeaufwand.ID).orElseThrow();

  @ParameterizedTest(name = "{0} is on {1}")
  @DisplayName("Sample of the Lohnsteuerbescheinigung 2022, print mark 3.21: lines of Anlage N")
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
    assertThat(anlageN.field("employer2-" + fieldId).orElseThrow().label()).startsWith(line + " ");
  }

  @ParameterizedTest(name = "{0} is on {1}")
  @DisplayName(
      "Sample of the Lohnsteuerbescheinigung 2022, print mark 3.21: lines of Anlage Vorsorgeaufwand")
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
      "Sample of the Lohnsteuerbescheinigung 2022, print mark 3.21: the tax class and the employer"
          + " have no numbered line")
  void theTaxClassAndTheEmployerAreOnTheCertificateWithoutALine() {
    for (String fieldId : new String[] {"taxClass", "employerName"}) {
      assertThat(CERTIFICATE.isOnCertificate(fieldId)).isTrue();
      assertThat(CERTIFICATE.lineOf(fieldId)).isEmpty();
      assertThat(anlageN.field(fieldId).orElseThrow().label()).doesNotContain("Zeile");
    }
  }

  @Test
  void aFieldFromAnotherSourceIsNotOnTheCertificate() {
    assertThat(CERTIFICATE.isOnCertificate("commuteKm")).isFalse();
    assertThat(CERTIFICATE.isOnCertificate("privateHealthBasic")).isFalse();
  }

  @Test
  void theSectionsHoldExactlyTheFieldsOfTheCertificate() {
    for (String fieldId : CERTIFICATE.fieldIds()) {
      assertThat(Sections.isOnCertificate(fieldId)).as(fieldId).isTrue();
    }
    assertThat(Sections.isOnCertificate("commuteKm")).isFalse();
  }
}
