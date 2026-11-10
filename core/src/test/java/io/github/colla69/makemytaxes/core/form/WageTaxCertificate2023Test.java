package io.github.colla69.makemytaxes.core.form;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.colla69.makemytaxes.core.TaxYear;
import io.github.colla69.makemytaxes.core.expense.Expense;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The expected lines come from the official sample "Ausdruck der elektronischen
 * Lohnsteuerbescheinigung für 2023", print mark 3.22, published by ELSTER as
 * https://www.elster.de/eportal/attachments/help_lstb_2023/Muster_LSTB_2023.pdf, read on
 * 10.11.2026. Lines 1 to 34 have the same numbers and parts as the sample of 2022, print mark 3.21.
 *
 * <p>The forms of 2023 have no line of the form yet, because the official forms of 2023 are not
 * in law/forms/2023.
 */
class WageTaxCertificate2023Test {

  private static final TaxYear YEAR = new TaxYear(2023);
  private static final WageTaxCertificate CERTIFICATE = WageTaxCertificate2023.LAYOUT;

  private final YearDefinition definition = YearDefinition.of(YEAR).orElseThrow();
  private final FormModule anlageN = Forms.find(YEAR, AnlageN.ID).orElseThrow();
  private final FormModule vorsorge = Forms.find(YEAR, AnlageVorsorgeaufwand.ID).orElseThrow();

  @ParameterizedTest(name = "{0} is on {1}")
  @DisplayName("Sample of the Lohnsteuerbescheinigung 2023, print mark 3.22: lines of Anlage N")
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
      "Sample of the Lohnsteuerbescheinigung 2023, print mark 3.22: lines of Anlage Vorsorgeaufwand")
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
      "Sample of the Lohnsteuerbescheinigung 2023, print mark 3.22: the tax class and the employer"
          + " have no numbered line")
  void theTaxClassAndTheEmployerAreOnTheCertificateWithoutALine() {
    assertThat(CERTIFICATE.fieldIds()).contains("taxClass", "employerName");
    assertThat(CERTIFICATE.lineOf("taxClass")).isEmpty();
    assertThat(CERTIFICATE.lineOf("employerName")).isEmpty();
  }

  @Test
  void theSectionsHoldExactlyTheFieldsOfTheCertificate() {
    assertThat(Sections.certificateFieldIds())
        .containsExactlyInAnyOrderElementsOf(CERTIFICATE.fieldIds());
  }

  @Test
  void theFormsOf2023HaveNoLineOfTheFormAndSaySo() {
    for (String formId : List.of(MainForm.ID, AnlageN.ID, AnlageVorsorgeaufwand.ID)) {
      FormModule form = Forms.find(YEAR, formId).orElseThrow();
      assertThat(form.fields()).extracting(Field::line).containsOnly("");
      assertThat(definition.lacksFormLines(formId)).isTrue();
    }
    assertThat(definition.lacksFormLines(HouseholdServicesForm.ID)).isFalse();
  }

  @Test
  void theFormsOf2022HaveTheirLines() {
    YearDefinition of2022 = YearDefinition.of(new TaxYear(2022)).orElseThrow();

    assertThat(of2022.lacksFormLines(AnlageN.ID)).isFalse();
  }

  @Test
  void theReviewOfAnlageNWithoutLinesKeepsTheOrderOfTheForm() {
    Answers answers =
        new Answers(Map.of("grossWage", "1.000,00", "commuteKm", "12", "homeOfficeDays", "10"));

    List<ReviewLine> lines = anlageN.review(answers, List.<Expense>of());

    assertThat(lines)
        .extracting(ReviewLine::value)
        .containsExactly("1.000,00", "12", "10");
  }
}
