package io.github.colla69.makemytaxes.core.form;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.colla69.makemytaxes.core.TaxYear;
import io.github.colla69.makemytaxes.core.expense.Expense;
import io.github.colla69.makemytaxes.core.expense.ExpenseTopic;
import io.github.colla69.makemytaxes.core.expense.Money;
import io.github.colla69.makemytaxes.core.expense.Payment;
import io.github.colla69.makemytaxes.core.expense.Purpose;
import io.github.colla69.makemytaxes.core.year.YearPack;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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
            "made-up.pdf");

    List<ReviewLine> lines = form.review(answers, List.of(laptop));

    assertThat(lines).extracting(ReviewLine::line).containsExactly("6", "42", "42", "45");
    assertThat(lines.get(1).value()).isEqualTo("123,45");
    assertThat(lines.get(1).evidence()).isEqualTo("Paid on 03.02.2022, receipt: made-up.pdf");
    assertThat(lines.get(2).label()).isEqualTo("Aufwendungen für Arbeitsmittel: total");
  }

  @Test
  void aMissingReceiptIsVisible() {
    var cable =
        new Expense(
            new Payment(LocalDate.of(2022, 2, 3), Money.ofCents(10_00)),
            new Purpose(ExpenseTopic.WORK_OTHER, "Made-up fee"),
            "");

    assertThat(form.review(new Answers(Map.of()), List.of(cable)).get(0).evidence())
        .isEqualTo("Paid on 03.02.2022, no receipt");
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
        .contains("how you travelled");
  }

  @Test
  void missingNamesOnlyRequiredFields() {
    assertThat(form.missing(new Answers(Map.of("taxClass", "1", "grossWage", "1,00"))))
        .extracting(Field::id)
        .containsExactly("wageTax");
  }
}
