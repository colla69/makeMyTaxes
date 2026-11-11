package io.github.colla69.makemytaxes.core.document;

import io.github.colla69.makemytaxes.core.expense.Money;
import java.util.Optional;

/**
 * The amounts that the reader found on one monthly payslip. An empty amount was not found.
 *
 * @param taxableGross the taxable gross pay of the month, "Steuer-Brutto" on the payslip
 * @param wageTax the wage tax of the month, "Lohnsteuer" on the payslip
 */
public record PayslipValues(Optional<Money> taxableGross, Optional<Money> wageTax) {

  /** The values of a payslip that the reader cannot read, such as a scan. */
  public static final PayslipValues NONE = new PayslipValues(Optional.empty(), Optional.empty());
}
