package io.github.colla69.makemytaxes.core.expense;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExpenseSummaryTest {

  private static Expense expense(ExpenseTopic topic, long cents, String receipt) {
    var payment = new Payment(LocalDate.of(2022, 3, 1), Money.ofCents(cents));
    return new Expense(payment, new Purpose(topic, "Made-up cost"), receipt);
  }

  @Test
  void addsUpEachTopicSeparately() {
    var summary =
        ExpenseSummary.of(
            List.of(
                expense(ExpenseTopic.WORK, 1_000, "a.pdf"),
                expense(ExpenseTopic.WORK, 250, "b.pdf"),
                expense(ExpenseTopic.DONATIONS_AND_PENSIONS, 500, "c.pdf")));

    assertThat(summary.totalFor(ExpenseTopic.WORK)).isEqualTo(Money.ofCents(1_250));
    assertThat(summary.totalFor(ExpenseTopic.DONATIONS_AND_PENSIONS))
        .isEqualTo(Money.ofCents(500));
  }

  @Test
  void aTopicWithoutExpensesTotalsZero() {
    var summary = ExpenseSummary.of(List.of());

    assertThat(summary.totalFor(ExpenseTopic.MEDICAL)).isEqualTo(Money.ofCents(0));
  }

  @Test
  void countsTheExpensesWithoutEvidence() {
    var summary =
        ExpenseSummary.of(
            List.of(
                expense(ExpenseTopic.WORK, 100, "a.pdf"),
                expense(ExpenseTopic.WORK, 100, ""),
                expense(ExpenseTopic.MEDICAL, 100, "")));

    assertThat(summary.expensesWithoutEvidence()).isEqualTo(2);
  }
}
