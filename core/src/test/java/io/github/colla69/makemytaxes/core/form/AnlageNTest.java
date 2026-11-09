package io.github.colla69.makemytaxes.core.form;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.colla69.makemytaxes.core.TaxYear;
import io.github.colla69.makemytaxes.core.expense.Evidence;
import io.github.colla69.makemytaxes.core.expense.Expense;
import io.github.colla69.makemytaxes.core.expense.ExpenseTopic;
import io.github.colla69.makemytaxes.core.expense.Money;
import io.github.colla69.makemytaxes.core.expense.Payment;
import io.github.colla69.makemytaxes.core.expense.Purpose;
import io.github.colla69.makemytaxes.core.year.YearPack;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AnlageNTest {

  private final AnlageN form = new AnlageN(YearPack.of(new TaxYear(2022)).orElseThrow());

  @Test
  void reviewFollowsTheLinesOfTheFormAndNamesTheEvidence() {
    var answers = new Answers(Map.of("grossWage", "1.000,00", "homeOfficeDays", "10"));
    var laptop =
        new Expense(
            new Payment(LocalDate.of(2022, 2, 3), Money.ofCents(123_45)),
            new Purpose(ExpenseTopic.WORK_EQUIPMENT, "Made-up laptop"),
            new Evidence("", "made-up.pdf", ""));

    List<ReviewLine> lines = form.review(answers, List.of(laptop));

    assertThat(lines).extracting(ReviewLine::line).containsExactly("6", "42", "42", "45");
    assertThat(lines.get(1).value()).isEqualTo("123,45");
    assertThat(lines.get(1).evidence()).isEqualTo("Bezahlt am 03.02.2022, Beleg: made-up.pdf");
    assertThat(lines.get(2).label()).isEqualTo("Aufwendungen für Arbeitsmittel: Summe");
  }

  @Test
  void aMissingReceiptIsVisible() {
    var cable =
        new Expense(
            new Payment(LocalDate.of(2022, 2, 3), Money.ofCents(10_00)),
            new Purpose(ExpenseTopic.WORK_OTHER, "Made-up fee"),
            "");

    assertThat(form.review(new Answers(Map.of()), List.of(cable)).get(0).evidence())
        .isEqualTo("Bezahlt am 03.02.2022, Beleg fehlt");
  }

  @Test
  void anOldReceiptNoteStaysVisibleButIsNotADocument() {
    var cable =
        new Expense(
            new Payment(LocalDate.of(2022, 2, 3), Money.ofCents(10_00)),
            new Purpose(ExpenseTopic.WORK_OTHER, "Made-up fee"),
            "made-up-folder");

    assertThat(form.review(new Answers(Map.of()), List.of(cable)).get(0).evidence())
        .isEqualTo("Bezahlt am 03.02.2022, Hinweis: made-up-folder, Beleg fehlt");
  }

  @Test
  void aStatedBasisIsNamedAsTheEvidence() {
    var fee =
        new Expense(
            new Payment(LocalDate.of(2022, 2, 3), Money.ofCents(10_00)),
            new Purpose(ExpenseTopic.WORK_OTHER, "Made-up fee"),
            new Evidence("", null, "Made-up 12 days"));

    assertThat(form.review(new Answers(Map.of()), List.of(fee)).get(0).evidence())
        .isEqualTo("Bezahlt am 03.02.2022, Grundlage: Made-up 12 days");
  }

  @Test
  void expensesOfOtherTopicsStayOffThisForm() {
    var doctor =
        new Expense(
            new Payment(LocalDate.of(2022, 2, 3), Money.ofCents(10_00)),
            new Purpose(ExpenseTopic.MEDICAL, "Made-up"),
            "");

    assertThat(form.review(new Answers(Map.of()), List.of(doctor))).isEmpty();
  }

  @Test
  void commutingAndHomeOfficeDaysMustFitInTheYear() {
    assertThat(
            form.check(
                new Answers(
                    Map.of("commuteDays", "200", "homeOfficeDays", "165", "commuteTransport", "CAR"))))
        .isEmpty();
    assertThat(
            form.check(
                new Answers(
                    Map.of("commuteDays", "200", "homeOfficeDays", "166", "commuteTransport", "CAR"))))
        .singleElement()
        .asString()
        .contains("366", "365");
  }

  @Test
  void commutingDaysNeedTheTransport() {
    assertThat(form.check(new Answers(Map.of("commuteDays", "10"))))
        .singleElement()
        .asString()
        .contains("womit Sie zur Arbeit");
  }

  @Test
  void missingNamesOnlyRequiredFields() {
    assertThat(form.missing(new Answers(Map.of("taxClass", "1", "grossWage", "1,00"))))
        .extracting(Field::id)
        .containsExactly("employerName", "wageTax");
  }

  @Test
  void eachEmployerHasItsOwnBlockAndTheReviewDoesNotAddThemTogether() {
    var answers =
        new Answers(
            Map.of(
                "employerName", "Made-up GmbH",
                "grossWage", "1.000,00",
                "employer2-employerName", "Made-up AG",
                "employer2-grossWage", "2.000,00",
                "commuteKm", "5"));

    assertThat(AnlageN.employerCount(answers)).isEqualTo(2);
    assertThat(form.fields(answers))
        .extracting(Field::section)
        .containsSubsequence("Arbeitgeber 1", "Arbeitgeber 2", "Werbungskosten");
    assertThat(form.review(answers, List.of()))
        .extracting(line -> line.section() + "|" + line.line() + "|" + line.value())
        .containsExactly(
            "Arbeitgeber 1||Made-up GmbH",
            "Arbeitgeber 1|6|1.000,00",
            "Arbeitgeber 2||Made-up AG",
            "Arbeitgeber 2|6|2.000,00",
            "Werbungskosten|35|5");
    assertThat(form.missing(answers))
        .extracting(Field::id)
        .contains("wageTax", "employer2-wageTax");
  }

  @Test
  void eachLabelOfTheCertificateNamesItsLine() {
    assertThat(form.field("employer3-grossWage"))
        .hasValueSatisfying(
            field -> {
              assertThat(field.label()).startsWith("Zeile 3 ");
              assertThat(field.section()).isEqualTo("Arbeitgeber 3");
              assertThat(field.hintId()).isEqualTo("grossWage");
            });
    assertThat(form.field("wageTax").orElseThrow().label()).startsWith("Zeile 4 ");
    assertThat(form.field("employedFrom").orElseThrow().label()).startsWith("Zeile 1 ");
  }

  @Test
  @DisplayName("§ 41b(1) sentence 2 no. 2 EStG, version 31.12.2022: the period lies in the calendar year")
  void thePeriodOfACertificateLiesInTheTaxYearAndStartsBeforeItEnds() {
    assertThat(
            form.check(
                new Answers(Map.of("employedFrom", "01.03.2022", "employedTo", "31.12.2022"))))
        .isEmpty();
    assertThat(
            form.check(
                new Answers(
                    Map.of("employer2-employedFrom", "01.06.2022", "employer2-employedTo", "01.05.2022"))))
        .singleElement()
        .asString()
        .contains("Arbeitgeber 2", "vor dem Beginn");
    assertThat(form.check(new Answers(Map.of("employedTo", "01.01.2023"))))
        .singleElement()
        .asString()
        .contains("2022");
  }
}
