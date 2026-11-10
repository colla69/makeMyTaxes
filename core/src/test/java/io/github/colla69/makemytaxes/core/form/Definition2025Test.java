package io.github.colla69.makemytaxes.core.form;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.colla69.makemytaxes.core.TaxYear;
import io.github.colla69.makemytaxes.core.document.DocumentRef;
import io.github.colla69.makemytaxes.core.expense.Evidence;
import io.github.colla69.makemytaxes.core.expense.Expense;
import io.github.colla69.makemytaxes.core.expense.ExpenseTopic;
import io.github.colla69.makemytaxes.core.expense.Money;
import io.github.colla69.makemytaxes.core.expense.Payment;
import io.github.colla69.makemytaxes.core.expense.Purpose;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The expected lines come from the official forms of 2025, which law/forms/2025/README.md names:
 *
 * <ul>
 *   <li>Hauptvordruck ESt 1 A 2025, marks 2025ESt1A011NET and 2025ESt1A012NET, September 2025.
 *   <li>Anlage N 2025, marks 2025AnlN031NET to 2025AnlN034NET, Oktober 2025.
 * </ul>
 */
class Definition2025Test {

  private final YearDefinition definition =
      YearDefinition.of(new TaxYear(2025)).orElseThrow();

  @ParameterizedTest(name = "{0} is on line {1}")
  @DisplayName("Hauptvordruck ESt 1 A 2025, mark 2025ESt1A011NET: the line of each field")
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
  void eachFieldOfTheMainFormIsOnItsLine(String fieldId, String line) {
    assertThat(definition.layouts().get(MainForm.ID).line(fieldId)).isEqualTo(line);
  }

  @ParameterizedTest(name = "{0} is on line {1}")
  @DisplayName("Anlage N 2025, marks 2025AnlN031NET to 2025AnlN033NET: the line of each field")
  @CsvSource({
    "taxClass, 4",
    "grossWage, 5",
    "wageTax, 6",
    "solidarity, 7",
    "churchTax, 8",
    "shortTimeWork, 20",
    "workplace, 27",
    "commuteDays, 29",
    "commuteKm, 30",
    "commuteTransport, 31",
    "employerCommute, 51",
    "WORK_ASSOCIATIONS, 53",
    "WORK_EQUIPMENT, 54",
    "homeOfficeDays, 58",
    "WORK_TRAINING, 60",
    "WORK_OTHER, 62"
  })
  void eachFieldOfAnlageNIsOnItsLine(String key, String line) {
    assertThat(definition.layouts().get(AnlageN.ID).line(key)).isEqualTo(line);
  }

  @Test
  @DisplayName(
      "Anlage N 2025, line 58, and § 4(5) no. 6c EStG, versions 30.06.2025 and 31.12.2025: the"
          + " home office day")
  void theHomeOfficeHelpFollowsTheRuleOf2025() {
    FormModule anlageN = Forms.find(new TaxYear(2025), AnlageN.ID).orElseThrow();

    assertThat(anlageN.field("homeOfficeDays").orElseThrow().help())
        .contains("überwiegend zu Hause", "erste Tätigkeitsstätte nicht");
  }

  @Test
  @DisplayName(
      "Anlage N 2025, line 59, and § 4(5) sentence 1 no. 6c sentences 2 and 3 EStG, versions"
          + " 30.06.2025 and 31.12.2025: the days with no other permanent workplace and the study")
  void theHomeOfficeHelpNamesLine59AndTheStudy() {
    FormModule anlageN = Forms.find(new TaxYear(2025), AnlageN.ID).orElseThrow();

    assertThat(anlageN.field("homeOfficeDays").orElseThrow().help())
        .contains(
            "dauerhaft kein anderer Arbeitsplatz",
            "Zeile 59 deckt die App nicht ab",
            "häusliches Arbeitszimmer");
  }

  @Test
  void theReviewOf2025FollowsTheLinesOf2025() {
    FormModule anlageN = Forms.find(new TaxYear(2025), AnlageN.ID).orElseThrow();
    var answers =
        new Answers(Map.of("grossWage", "1.000,00", "homeOfficeDays", "10", "commuteKm", "5"));
    var laptop =
        new Expense(
            new Payment(LocalDate.of(2025, 2, 3), Money.ofCents(123_45)),
            new Purpose(ExpenseTopic.WORK_EQUIPMENT, "Made-up laptop"),
            new Evidence("", new DocumentRef(7, "made-up.pdf"), ""));

    assertThat(anlageN.review(answers, List.of(laptop)))
        .extracting(ReviewLine::line)
        .containsExactly("5", "30", "54", "54", "58");
  }
}
