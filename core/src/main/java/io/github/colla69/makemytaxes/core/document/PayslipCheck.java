package io.github.colla69.makemytaxes.core.document;

import io.github.colla69.makemytaxes.core.GermanFormats;
import io.github.colla69.makemytaxes.core.expense.Money;
import java.util.List;
import java.util.Optional;

/**
 * One amount of the payslips against the same amount on the Lohnsteuerbescheinigung. The check
 * only shows the difference. It never changes a value of the return.
 *
 * @param sum the sum of the payslips, or empty if a payslip has no read amount
 * @param certificate the amount that the reader found on the certificate, or empty
 */
public record PayslipCheck(Optional<Money> sum, Optional<Money> certificate) {

  /** The sum is empty if there is no payslip, or if the reader did not find one of the amounts. */
  public static PayslipCheck of(List<Optional<Money>> payslips, Optional<Money> certificate) {
    Optional<Money> sum = Optional.empty();
    if (!payslips.isEmpty() && payslips.stream().allMatch(Optional::isPresent)) {
      sum = Optional.of(payslips.stream().map(Optional::get).reduce(Money.ofCents(0), Money::plus));
    }
    return new PayslipCheck(sum, certificate);
  }

  /** The certificate minus the sum, or empty if one of the two is missing. */
  public Optional<Long> differenceCents() {
    if (sum.isEmpty() || certificate.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(certificate.get().cents() - sum.get().cents());
  }

  public String result() {
    return differenceCents().map(PayslipCheck::resultOf).orElse("kein Vergleich möglich");
  }

  private static String resultOf(long difference) {
    if (difference == 0) {
      return "stimmt überein";
    }
    String amount = GermanFormats.format(Money.ofCents(Math.abs(difference)));
    return difference > 0
        ? "Differenz " + amount + " €: die Bescheinigung ist höher"
        : "Differenz " + amount + " €: die Lohnabrechnungen sind höher";
  }
}
