package io.github.colla69.makemytaxes.core.expense;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import io.github.colla69.makemytaxes.core.TaxYear;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ExpenseTest {

  private static final Purpose WORK_LAPTOP = new Purpose(ExpenseTopic.WORK_OTHER, "Made-up laptop");

  @Test
  void rejectsAPaymentOfZero() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new Payment(LocalDate.of(2022, 3, 1), Money.ofCents(0)))
        .withMessageContaining("more than zero");
  }

  @Test
  void rejectsAnEmptyDescription() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new Purpose(ExpenseTopic.WORK_OTHER, "  "))
        .withMessageContaining("description");
  }

  @Test
  void aCostCountsInTheYearOfThePayment() {
    var payment = new Payment(LocalDate.of(2022, 12, 31), Money.ofCents(100));

    assertThat(payment.isIn(new TaxYear(2022))).isTrue();
    assertThat(payment.isIn(new TaxYear(2023))).isFalse();
  }

  @Test
  void anExpenseWithoutAReceiptHasNoEvidence() {
    var payment = new Payment(LocalDate.of(2022, 3, 1), Money.ofCents(100));

    assertThat(new Expense(payment, WORK_LAPTOP, "  ").hasEvidence()).isFalse();
    assertThat(new Expense(payment, WORK_LAPTOP, "laptop.pdf").hasEvidence()).isTrue();
  }
}
