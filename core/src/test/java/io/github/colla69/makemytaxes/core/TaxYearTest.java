package io.github.colla69.makemytaxes.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class TaxYearTest {

  @Test
  void keepsAFourDigitYear() {
    assertThat(new TaxYear(2022).value()).isEqualTo(2022);
  }

  @Test
  void rejectsAYearThatIsNotFourDigits() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new TaxYear(22))
        .withMessageContaining("22");
  }
}
