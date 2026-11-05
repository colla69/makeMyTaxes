package io.github.colla69.makemytaxes.core.expense;

import io.github.colla69.makemytaxes.core.TaxYear;
import java.time.LocalDate;

public record Payment(LocalDate paidOn, Money amount) {

  public Payment {
    if (!amount.isPositive()) {
      throw new IllegalArgumentException(
          "A payment must be more than zero, but got " + amount.cents() + " cents.");
    }
  }

  /** A cost counts in the year of the payment (§ 11(2) EStG). */
  public boolean isIn(TaxYear taxYear) {
    return paidOn.getYear() == taxYear.value();
  }
}
