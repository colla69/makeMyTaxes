package io.github.colla69.makemytaxes.core.form;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.colla69.makemytaxes.core.TaxYear;
import io.github.colla69.makemytaxes.core.expense.Expense;
import io.github.colla69.makemytaxes.core.expense.Money;
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
 * <p>The lines of the forms come from the official forms of 2023 in law/forms/2023.
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

  @ParameterizedTest(name = "{1} is on line {2}")
  @DisplayName(
      "Official forms 2023 in law/forms/2023: Hauptvordruck 2023ESt1A011NET/012NET and Anlage N"
          + " 2023AnlN031NET to 034NET, September 2023")
  @CsvSource({
    "main, taxNumber, 4",
    "main, taxOffice, 5",
    "main, taxId, 8",
    "main, birthDate, 8",
    "main, lastName, 9",
    "main, firstName, 10",
    "main, occupation, 12",
    "main, street, 13",
    "main, houseNumber, 14",
    "main, postalCode, 15",
    "main, city, 16",
    "main, iban, 30",
    "main, benefits, 35",
    "main, notes, 37",
    "anlage-n, taxClass, 4",
    "anlage-n, grossWage, 5",
    "anlage-n, wageTax, 6",
    "anlage-n, solidarity, 7",
    "anlage-n, churchTax, 8",
    "anlage-n, shortTimeWork, 23",
    "anlage-n, workplace, 30",
    "anlage-n, commuteDays, 32",
    "anlage-n, commuteKm, 33",
    "anlage-n, commuteTransport, 34 bis 36",
    "anlage-n, employerCommute, 54",
    "anlage-n, homeOfficeDays, 61"
  })
  void theFieldsHaveTheLinesOfTheFormsOf2023(String formId, String fieldId, String line) {
    FormLayout layout = definition.layouts().get(formId);

    assertThat(layout.field(fieldId)).map(Field::line).hasValue(line);
  }

  @ParameterizedTest(name = "{0} is on line {1}")
  @DisplayName("Official form Anlage N 2023, 2023AnlN033NET: the lines of the expense topics")
  @CsvSource({
    "WORK_ASSOCIATIONS, 56",
    "WORK_EQUIPMENT, 57",
    "WORK_TRAINING, 63",
    "WORK_OTHER, 65"
  })
  void theExpenseTopicsHaveTheLinesOfAnlageN2023(String topic, String line) {
    assertThat(definition.layouts().get(AnlageN.ID).line(topic)).isEqualTo(line);
  }

  @ParameterizedTest(name = "{0} goes on line {1}")
  @DisplayName(
      "Anlage N 2023, mark 2023AnlN032NET, lines 34 to 36: the km line of each means of transport")
  @CsvSource({
    "PKW, Anlage N Zeile 34",
    "öffentlichen Verkehrsmitteln, Zeile 36",
  })
  void theTransportHelpNamesTheLineOfEachChoice(String transport, String line) {
    String help = anlageN.field("commuteTransport").orElseThrow().help();

    assertThat(help).containsPattern(transport + "[^.:]*" + line);
  }

  @Test
  @DisplayName(
      "Anlage N 2023, lines 60 to 62, and § 4(5) sentence 1 no. 6c sentences 1 to 3 EStG, versions"
          + " 30.06.2023 and 31.12.2023: the home office day, the days with no other permanent"
          + " workplace and the study")
  void theHomeOfficeHelpFollowsTheRuleAndTheLinesOf2023() {
    assertThat(anlageN.field("homeOfficeDays").orElseThrow().help())
        .contains(
            "überwiegend zu Hause",
            "erste Tätigkeitsstätte nicht",
            "Zeile 61",
            "dauerhaft kein anderer Arbeitsplatz",
            "Zeile 62 deckt die App nicht ab",
            "keine Tagespauschale, soweit Sie ein häusliches Arbeitszimmer abziehen (Zeile 60)")
        .doesNotContain("denselben Zeitraum");
  }

  @Test
  @DisplayName("The interview of 2023 does not ask for the home office days of 2022 only at home")
  void theHomeOfficeQuestionOf2023DoesNotStateTheRuleOf2022() {
    Interview.Question question =
        definition.interview().find(new Answers(Map.of()), "homeOfficeDays").orElseThrow();

    assertThat(question.text()).doesNotContain("nur zu Hause");
  }

  @Test
  @DisplayName("§ 4(5) sentence 1 no. 6c EStG, version 31.12.2023: 6 euros for each day")
  void theEstimateCountsSixEurosForEachHomeOfficeDay() {
    Answers answers = new Answers(Map.of("homeOfficeDays", "10"));

    assertThat(WorkExpenseEstimate.of(definition.pack(), answers, List.of()).homeOffice())
        .isEqualTo(Money.ofCents(60_00));
  }

  @Test
  @DisplayName(
      "Official form Anlage N 2023 (2023AnlN032NET), lines 32 to 36: the transport follows the"
          + " kilometres")
  void theTransportFollowsTheKilometresOnTheReview() {
    Answers answers =
        new Answers(
            Map.of(
                "commuteDays", "220",
                "commuteKm", "60",
                "commuteTransport", Kind.CAR,
                "homeOfficeDays", "10"));

    assertThat(anlageN.review(answers, List.<Expense>of()))
        .extracting(ReviewLine::line)
        .containsExactly("32", "33", "34 bis 36", "61");
  }
}
