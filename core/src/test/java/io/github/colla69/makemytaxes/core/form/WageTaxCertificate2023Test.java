package io.github.colla69.makemytaxes.core.form;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

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
    "anlage-n, employerCommute, 54",
    "anlage-n, homeOfficeDays, 61",
    "anlage-n, homeOfficeDaysNoWorkplace, 62"
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

  @Test
  @DisplayName(
      "§ 4(5) sentence 1 no. 6c sentence 1 EStG, version 31.12.2023: a day with another workplace"
          + " counts only without a trip to the first workplace")
  void aDayWithAnotherWorkplaceCannotAlsoBeACommuteDay() {
    Answers answers =
        new Answers(
            Map.of("commuteDays", "230", "homeOfficeDays", "136", "commuteTransport", "CAR"));

    assertThat(anlageN.check(answers))
        .containsExactly(
            "Tage mit Wegen zur Arbeit und Homeoffice-Tage mit anderem Arbeitsplatz ergeben"
                + " zusammen 366, aber 2023 hat nur 365 Tage.");
  }

  @Test
  @DisplayName(
      "§ 4(5) sentence 1 no. 6c sentence 2 EStG, version 31.12.2023: without another workplace, a"
          + " day with a trip to work also counts")
  void withoutAnotherWorkplaceACommuteDayAlsoCounts() {
    Answers answers =
        new Answers(
            Map.of(
                "commuteDays", "230",
                Sections.NO_WORKPLACE_DAYS, "200",
                "commuteTransport", "CAR"));

    assertThat(anlageN.check(answers)).isEmpty();
  }

  @Test
  @DisplayName("Official form Anlage N 2023, lines 61 and 62: a day goes on one line only")
  void bothHomeOfficeLinesTogetherFitInTheYear() {
    Answers answers =
        new Answers(Map.of("homeOfficeDays", "200", Sections.NO_WORKPLACE_DAYS, "166"));

    assertThat(anlageN.check(answers))
        .containsExactly("Die Homeoffice-Tage ergeben zusammen 366, aber 2023 hat nur 365 Tage.");
  }

  @Test
  @DisplayName(
      "§ 4(5) sentence 1 no. 6c EStG, version 31.12.2023: 6 euros for each day of both lines,"
          + " limit 1 260 euros")
  void theEstimateCountsTheDaysOfBothLines() {
    Answers answers =
        new Answers(Map.of("homeOfficeDays", "10", Sections.NO_WORKPLACE_DAYS, "20"));

    assertThat(WorkExpenseEstimate.of(definition.pack(), answers, List.of()).homeOffice())
        .isEqualTo(Money.ofCents(180_00));
  }

  @Test
  void theInterviewOf2023AsksForBothHomeOfficeLines() {
    assertThat(definition.interview().questions(new Answers(Map.of())))
        .extracting(Interview.Question::id)
        .contains("homeOfficeDays", Sections.NO_WORKPLACE_DAYS);
  }

  @Test
  @DisplayName(
      "Official form Anlage N 2023 (2023AnlN032NET), lines 33 and 34: the kilometres by car go"
          + " again on line 34")
  void theKilometresByCarGoAgainOnLine34() {
    Answers answers =
        new Answers(
            Map.of(
                "commuteDays", "220",
                "commuteKm", "60",
                "commuteTransport", Kind.CAR,
                "homeOfficeDays", "10"));

    List<ReviewLine> lines = anlageN.review(answers, List.<Expense>of());

    assertThat(lines)
        .extracting(ReviewLine::line, ReviewLine::value)
        .containsExactly(
            tuple("32", "220"), tuple("33", "60"), tuple("34", "60"), tuple("61", "10"));
    assertThat(lines.get(2).label()).contains("PKW");
  }

  @Test
  @DisplayName(
      "Official form Anlage N 2023 (2023AnlN032NET), lines 33 and 36: the kilometres by public"
          + " transport, bike or on foot go again on line 36")
  void theKilometresByOtherTransportGoAgainOnLine36() {
    Answers answers =
        new Answers(Map.of("commuteKm", "12", "commuteTransport", Kind.OTHER_TRANSPORT));

    assertThat(anlageN.review(answers, List.<Expense>of()))
        .extracting(ReviewLine::line, ReviewLine::value)
        .containsExactly(tuple("33", "12"), tuple("36", "12"));
  }

  @Test
  void theTransportWithoutKilometresHasNoLineAndGoesLast() {
    Answers answers =
        new Answers(
            Map.of("commuteDays", "220", "homeOfficeDays", "10", "commuteTransport", Kind.CAR));

    assertThat(anlageN.review(answers, List.<Expense>of()))
        .extracting(ReviewLine::line, ReviewLine::value)
        .containsExactly(
            tuple("32", "220"),
            tuple("61", "10"),
            tuple("", "Eigener oder zur Nutzung überlassener Kraftwagen"));
  }
}
