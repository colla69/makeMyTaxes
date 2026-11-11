package io.github.colla69.makemytaxes.core.document;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.colla69.makemytaxes.core.expense.Money;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PayslipCheckTest {

  private static Optional<Money> euros(long euros) {
    return Optional.of(Money.ofCents(euros * 100));
  }

  @Test
  void showsNoDifferenceIfTheSumMatches() {
    PayslipCheck check = PayslipCheck.of(List.of(euros(1_000), euros(2_000)), euros(3_000));

    assertThat(check.sum()).isEqualTo(euros(3_000));
    assertThat(check.differenceCents()).contains(0L);
    assertThat(check.result()).isEqualTo("stimmt überein");
  }

  @Test
  void showsTheDifferenceIfTheCertificateIsHigher() {
    PayslipCheck check = PayslipCheck.of(List.of(euros(1_000), euros(2_000)), euros(3_500));

    assertThat(check.differenceCents()).contains(50_000L);
    assertThat(check.result()).isEqualTo("Differenz 500,00 €: die Bescheinigung ist höher");
  }

  @Test
  void showsTheDifferenceIfThePayslipsAreHigher() {
    PayslipCheck check =
        PayslipCheck.of(List.of(Optional.of(Money.ofCents(123_456))), euros(1_000));

    assertThat(check.result()).isEqualTo("Differenz 234,56 €: die Lohnabrechnungen sind höher");
  }

  @Test
  void hasNoSumIfAPayslipWasNotRead() {
    PayslipCheck check = PayslipCheck.of(List.of(euros(1_000), Optional.empty()), euros(1_000));

    assertThat(check.sum()).isEmpty();
    assertThat(check.result()).isEqualTo("kein Vergleich möglich");
  }

  @Test
  void hasNoComparisonWithoutTheCertificateOrWithoutPayslips() {
    assertThat(PayslipCheck.of(List.of(euros(1_000)), Optional.empty()).differenceCents())
        .isEmpty();
    assertThat(PayslipCheck.of(List.of(), euros(1_000)).differenceCents()).isEmpty();
  }
}
