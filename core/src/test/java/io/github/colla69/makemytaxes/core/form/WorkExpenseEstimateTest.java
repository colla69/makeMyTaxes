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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WorkExpenseEstimateTest {

  private final YearPack pack = YearPack.of(new TaxYear(2022)).orElseThrow();

  @Test
  @DisplayName("§ 9(1) no. 4 EStG: 20 km at 0,30 euros")
  void commuteUpTo20Km() {
    assertThat(WorkExpenseEstimate.commute(pack, 100, 20, true)).isEqualTo(Money.ofCents(600_00));
  }

  @Test
  @DisplayName("§ 9(1) no. 4 EStG: 0,38 euros from the 21st km")
  void commuteFrom21Km() {
    // 20 x 0,30 + 10 x 0,38 = 9,80 for each day.
    assertThat(WorkExpenseEstimate.commute(pack, 100, 30, true)).isEqualTo(Money.ofCents(980_00));
  }

  @Test
  @DisplayName("§ 9(1) no. 4 EStG: the 4 500 euro limit applies only without a car")
  void limitWithoutCar() {
    assertThat(WorkExpenseEstimate.commute(pack, 230, 100, false))
        .isEqualTo(Money.ofCents(4_500_00));
    assertThat(WorkExpenseEstimate.commute(pack, 230, 100, true))
        .isEqualTo(Money.ofCents(8_372_00));
  }

  @Test
  @DisplayName("§ 4(5) no. 6b EStG, version 30.06.2022: 5 euros a day, limit 600 euros")
  void homeOfficeStopsAtTheLimit() {
    var answers = new Answers(Map.of("homeOfficeDays", "200"));

    assertThat(WorkExpenseEstimate.of(pack, answers, List.of()).homeOffice())
        .isEqualTo(Money.ofCents(600_00));
  }

  @Test
  @DisplayName("§ 9a EStG: the expenses must be higher than the lump sum")
  void comparesWithTheLumpSum() {
    var equal = WorkExpenseEstimate.of(pack, new Answers(Map.of()), List.of(work(1_200_00)));
    var above = WorkExpenseEstimate.of(pack, new Answers(Map.of()), List.of(work(1_200_01)));

    assertThat(equal.isAboveLumpSum()).isFalse();
    assertThat(above.isAboveLumpSum()).isTrue();
  }

  @Test
  void onlyWorkTopicsCount() {
    Expense medical =
        new Expense(
            new Payment(LocalDate.of(2022, 2, 3), Money.ofCents(500_00)),
            new Purpose(ExpenseTopic.MEDICAL, "Made-up"),
            "");

    assertThat(WorkExpenseEstimate.of(pack, new Answers(Map.of()), List.of(medical)).listed())
        .isEqualTo(Money.ofCents(0));
  }

  private static Expense work(long cents) {
    return new Expense(
        new Payment(LocalDate.of(2022, 2, 3), Money.ofCents(cents)),
        new Purpose(ExpenseTopic.WORK_EQUIPMENT, "Made-up"),
        "");
  }
}
