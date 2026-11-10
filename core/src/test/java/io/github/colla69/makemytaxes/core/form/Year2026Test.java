package io.github.colla69.makemytaxes.core.form;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.colla69.makemytaxes.core.TaxYear;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The forms of 2026. The official forms and the certificate sample of 2026 are not in the app yet,
 * so no field may have a line.
 */
class Year2026Test {

  private static final TaxYear YEAR = new TaxYear(2026);
  private static final YearDefinition DEFINITION = YearDefinition.of(YEAR).orElseThrow();

  @Test
  void eachFormOf2022IsThereAndNoFieldHasALine() {
    assertThat(DEFINITION.linesKnown()).isFalse();
    assertThat(DEFINITION.forms())
        .extracting(FormModule::id)
        .containsExactlyElementsOf(
            YearDefinition.of(new TaxYear(2022)).orElseThrow().forms().stream()
                .map(FormModule::id)
                .toList());
    // A form line is a number. Some fields name their legal source in the same place, such as
    // "§ 46 Abs. 2 Nr. 1 EStG". That is no line of the form.
    for (FormModule form : DEFINITION.forms()) {
      assertThat(form.fields()).allSatisfy(field -> assertThat(field.line()).doesNotMatch("\\d.*"));
      assertThat(form.fields())
          .allSatisfy(
              field -> {
                assertThat(field.label()).doesNotContain("Zeile");
                assertThat(field.help()).doesNotContain("Zeile");
              });
    }
  }

  @Test
  void theCertificateKeepsTheFieldIdsButHasNoLine() {
    WageTaxCertificate certificate = DEFINITION.certificate();
    assertThat(certificate.fieldIds()).isEqualTo(WageTaxCertificate2022.LAYOUT.fieldIds());
    assertThat(certificate.lineOf("grossWage")).isEmpty();
    assertThat(certificate.read(new CertificateText("Lohnsteuerbescheinigung für 2026", "", "3. Bruttoarbeitslohn 40.000,00")).found())
        .isEmpty();
  }

  @Test
  void theInterviewAsksForAnAmountWithoutALine() {
    Answers employed = new Answers(Map.of("employed", Interview.YES));
    Interview interview = DEFINITION.interview();
    assertThat(interview.find(employed, "grossWage").orElseThrow().text())
        .endsWith("Welcher Betrag steht auf der Lohnsteuerbescheinigung, Bruttoarbeitslohn?")
        .doesNotContain("Zeile");
    assertThat(interview.find(employed, "employedFrom").orElseThrow().text())
        .endsWith(": Lohnsteuerbescheinigung: Ab welchem Tag dauerte das Dienstverhältnis?");
    for (Interview.Topic topic : Interview.Topic.values()) {
      assertThat(interview.questionsOf(employed, topic))
          .allSatisfy(question -> assertThat(question.text()).doesNotContain("Zeile"));
    }
  }

  @Test
  @DisplayName(
      "§ 46(2) no. 3 EStG with § 52(36) sentence 3 EStG, versions 30.06.2026 and 05.11.2026: a"
          + " refund of health and care contributions over 410 euros")
  void theFilingQuestionOfNo3AsksForARefundFrom2026() {
    assertThat(healthQuestion(YEAR))
        .contains("13.614,00 Euro")
        .contains("mit mehr als 410,00 Euro erstattet")
        .doesNotContain("Vorsorgepauschale");
    assertThat(healthQuestion(new TaxYear(2022))).contains("Vorsorgepauschale");
  }

  @Test
  @DisplayName(
      "§ 46(2) no. 5 EStG, versions 31.12.2022 and 30.06.2026 and 05.11.2026, with § 52(1) EStG:"
          + " from 2025 only the 20 percent of § 39c(3)")
  void theFilingQuestionOfNo5NamesOnlyTheThirdPartyFrom2026() {
    assertThat(filingQuestion(YEAR, "specialPayment"))
        .contains("§ 39c Abs. 3 EStG", "20 Prozent")
        .doesNotContain("ermäßigt");
    assertThat(filingQuestion(new TaxYear(2022), "specialPayment")).contains("ermäßigt");
  }

  @Test
  @DisplayName(
      "§ 4(5) sentence 1 no. 6c sentences 1 and 2 EStG with § 52(6) sentence 13 EStG, versions"
          + " 30.06.2026 and 05.11.2026: a day of work mainly at home counts, not only at home")
  void theHomeOfficeDaysFollowNo6cIn2026() {
    String help =
        Forms.find(YEAR, AnlageN.ID).orElseThrow().fields().stream()
            .filter(field -> field.id().equals("homeOfficeDays"))
            .findFirst()
            .orElseThrow()
            .help();
    assertThat(help)
        .contains("überwiegend zu Hause", "erste Tätigkeitsstätte nicht", "dauerhaft kein anderer")
        .doesNotContain("nur zu Hause");
    Answers employed = new Answers(Map.of("employed", Interview.YES));
    assertThat(DEFINITION.interview().find(employed, "homeOfficeDays").orElseThrow().text())
        .contains("überwiegend zu Hause")
        .doesNotContain("nur zu Hause");
    assertThat(HomeOfficeDay.reviewText(2026)).contains("überwiegend zu Hause");
  }

  @Test
  @DisplayName("§ 4(5) sentence 1 no. 6b sentence 4 EStG, version 31.12.2022: only at home in 2022")
  void theHomeOfficeDaysOf2022StayOnlyAtHome() {
    TaxYear year2022 = new TaxYear(2022);
    Answers employed = new Answers(Map.of("employed", Interview.YES));
    assertThat(
            YearDefinition.of(year2022).orElseThrow().interview()
                .find(employed, "homeOfficeDays").orElseThrow().text())
        .contains("nur zu Hause");
    assertThat(HomeOfficeDay.reviewText(2022)).isEqualTo("Tag nur zu Hause");
  }

  private static String healthQuestion(TaxYear year) {
    return filingQuestion(year, "healthAllowance");
  }

  private static String filingQuestion(TaxYear year, String fieldId) {
    return Forms.find(year, FilingCheckForm.ID).orElseThrow().fields().stream()
        .filter(field -> field.id().equals(fieldId))
        .findFirst()
        .orElseThrow()
        .label();
  }
}
