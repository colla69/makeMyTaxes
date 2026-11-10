package io.github.colla69.makemytaxes.core.form;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.colla69.makemytaxes.core.TaxYear;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The lines come from the official forms in law/forms/2024: Hauptvordruck ESt 1 A, marks
 * 2024ESt1A011NET and 2024ESt1A012NET, and Anlage N, marks 2024AnlN031NET to 2024AnlN033NET.
 *
 * <p>The certificate lines come from the official sample "Ausdruck der elektronischen
 * Lohnsteuerbescheinigung für 2024", print mark 8.23, published by ELSTER as
 * https://www.elster.de/eportal/attachments/help_lstb_2024/Muster_LSTB_2024.pdf. Its lines 1 to 34
 * have the same numbers and labels as the sample of 2022.
 */
class Definition2024Test {

  private static final TaxYear YEAR = new TaxYear(2024);

  private final FormModule main = Forms.find(YEAR, MainForm.ID).orElseThrow();
  private final FormModule anlageN = Forms.find(YEAR, AnlageN.ID).orElseThrow();
  private final FormLayout anlageNLayout =
      YearDefinition.of(YEAR).orElseThrow().layouts().get(AnlageN.ID);

  @ParameterizedTest(name = "{0} is on line {1}")
  @DisplayName("Hauptvordruck 2024, 2024ESt1A011NET and 012NET: the line of each field")
  @CsvSource({
    "taxNumber, 4",
    "taxOffice, 5",
    "taxId, 8",
    "birthDate, 8",
    "lastName, 9",
    "firstName, 10",
    "occupation, 12",
    "street, 13",
    "houseNumber, 14",
    "postalCode, 15",
    "city, 16",
    "iban, 30",
    "benefits, 35",
    "notes, 37"
  })
  void mainFormLines(String fieldId, String line) {
    assertThat(main.field(fieldId).orElseThrow().line()).isEqualTo(line);
  }

  @Test
  @DisplayName("Hauptvordruck 2024: the occupation comes before the address")
  void occupationComesBeforeTheAddress() {
    assertThat(main.fields().stream().map(Field::id).toList())
        .containsSubsequence("firstName", "occupation", "street");
    assertThat(Sections.GENERAL_OCCUPATION_FIRST.fields())
        .containsExactlyInAnyOrderElementsOf(Sections.GENERAL.fields());
  }

  @ParameterizedTest(name = "{0} is on line {1}")
  @DisplayName("Anlage N 2024, 2024AnlN031NET to 033NET: the line of each field")
  @CsvSource({
    "taxClass, 4",
    "grossWage, 5",
    "wageTax, 6",
    "solidarity, 7",
    "churchTax, 8",
    "shortTimeWork, 23",
    "workplace, 30",
    "commuteDays, 32",
    "commuteKm, 33",
    "commuteTransport, 34 bis 36",
    "employerCommute, 54",
    "homeOfficeDays, 61"
  })
  void anlageNLines(String fieldId, String line) {
    assertThat(anlageN.field(fieldId).orElseThrow().line()).isEqualTo(line);
  }

  @ParameterizedTest(name = "{0} is on line {1}")
  @DisplayName("Anlage N 2024, 2024AnlN033NET: the line of each kind of work expense")
  @CsvSource({
    "WORK_ASSOCIATIONS, 56",
    "WORK_EQUIPMENT, 57",
    "WORK_TRAINING, 63",
    "WORK_OTHER, 65"
  })
  void workExpenseLines(String topic, String line) {
    assertThat(anlageNLayout.line(topic)).isEqualTo(line);
  }

  @ParameterizedTest(name = "{0} goes on line {1}")
  @DisplayName(
      "Anlage N 2024, mark 2024AnlN032NET, lines 34 to 36: the km line of each means of transport")
  @CsvSource({
    "Kraftwagen, Anlage N Zeile 34",
    "Sammelbeförderung des Arbeitgebers, Zeile 35",
    "öffentlichen Verkehrsmitteln, Zeile 36",
  })
  void theTransportHelpNamesTheLineOfEachChoice(String transport, String line) {
    String help = anlageN.field("commuteTransport").orElseThrow().help();

    assertThat(help).containsPattern(transport + "[^.:]*" + line);
  }

  @Test
  @DisplayName(
      "§ 4(5) sentence 1 no. 6c sentence 1 EStG, versions 30.06.2024 and 31.12.2024: a day worked"
          + " mostly at home, without the first place of work")
  void homeOfficeHelpFollowsTheRuleFrom2023() {
    assertThat(anlageN.field("homeOfficeDays").orElseThrow().help())
        .contains("ganz oder überwiegend zu Hause")
        .contains("keine erste Tätigkeitsstätte aufgesucht");
  }

  @Test
  @DisplayName(
      "Anlage N 2024, notes on lines 61 and 62, and § 4(5) sentence 1 no. 6c sentence 2 EStG,"
          + " version 31.12.2024: line 61 only with another workplace; line 62 is not supported")
  void homeOfficeHelpAndQuestionNameTheConditionOfLine61() {
    assertThat(anlageN.field("homeOfficeDays").orElseThrow().help())
        .contains("Nur wenn Ihnen ein anderer Arbeitsplatz zur Verfügung steht (Zeile 61)")
        .contains("Steht Ihnen dauerhaft kein anderer Arbeitsplatz zur Verfügung, gilt Zeile 62")
        .contains("die App noch nicht unterstützt");
    assertThat(homeOfficeQuestion(YEAR))
        .contains("Nur wenn Ihnen ein anderer Arbeitsplatz zur Verfügung steht")
        .contains("Steht Ihnen dauerhaft kein anderer Arbeitsplatz zur Verfügung")
        .contains("unterstützt die App diese Tage noch nicht");
  }

  @Test
  @DisplayName(
      "§ 4(5) sentence 1 no. 6c sentence 1 EStG, versions 30.06.2024 and 31.12.2024: the interview"
          + " asks for days worked mostly at home; 2022 keeps no. 6b, version 30.06.2022")
  void interviewAsksTheHomeOfficeQuestionOfTheYear() {
    assertThat(homeOfficeQuestion(YEAR)).isEqualTo(Interview.HOME_OFFICE_FROM_2023);
    assertThat(homeOfficeQuestion(new TaxYear(2022))).isEqualTo(Interview.HOME_OFFICE_UNTIL_2022);
  }

  private static String homeOfficeQuestion(TaxYear year) {
    return YearDefinition.of(year).orElseThrow().interview().questions(new Answers(Map.of())).stream()
        .filter(question -> question.id().equals("homeOfficeDays"))
        .findFirst()
        .orElseThrow()
        .text();
  }

  @ParameterizedTest(name = "{0} is on {1}")
  @DisplayName("Sample of the Lohnsteuerbescheinigung 2024, print mark 8.23: lines of Anlage N")
  @CsvSource({
    "employedFrom, Zeile 1",
    "grossWage, Zeile 3",
    "wageTax, Zeile 4",
    "solidarity, Zeile 5",
    "churchTax, Zeile 6",
    "shortTimeWork, Zeile 15",
    "employerCommute, Zeile 17 und 18"
  })
  void certificateLines(String fieldId, String line) {
    assertThat(WageTaxCertificate2024.LAYOUT.lineOf(fieldId)).hasValue(line);
    assertThat(anlageN.field(fieldId).orElseThrow().label()).startsWith(line + " ");
  }

  @Test
  @DisplayName("Sample of the Lohnsteuerbescheinigung 2024: the reader takes 2024 and rejects 2022")
  void readerChecksTheYear() {
    // Made-up values.
    String right =
        """
        3. Bruttoarbeitslohn einschl. Sachbezüge ohne 9. 30.000,00
        und 10.
        4. Einbehaltene Lohnsteuer von 3. 4.321,00
        """;

    CertificateValues of2024 = WageTaxCertificate2024.LAYOUT.read(page(2024, right));
    CertificateValues of2022 = WageTaxCertificate2024.LAYOUT.read(page(2022, right));

    assertThat(of2024.found()).containsEntry("grossWage", "30.000,00");
    assertThat(of2022.found()).isEmpty();
  }

  private static CertificateText page(int year, String right) {
    return new CertificateText(
        "Ausdruck der elektronischen Lohnsteuerbescheinigung für " + year + "\n", "", right);
  }
}
