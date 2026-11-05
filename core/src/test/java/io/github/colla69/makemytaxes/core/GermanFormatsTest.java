package io.github.colla69.makemytaxes.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import io.github.colla69.makemytaxes.core.expense.Money;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class GermanFormatsTest {

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {"1.234,56|123456", "12,5|1250", "12|1200", "0,99|99", "1234,56|123456"})
  void readsAGermanAmount(String input, long cents) {
    assertThat(GermanFormats.parseMoney(input)).isEqualTo(Money.ofCents(cents));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "abc", "12.5", "1,234", "-5,00", "1.23,45"})
  void rejectsAnAmountThatIsNotGerman(String input) {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> GermanFormats.parseMoney(input))
        .withMessageContaining("amount");
  }

  @Test
  void rejectsAnAmountThatCouldOverflow() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> GermanFormats.parseMoney("184.467.440.737.095.517"));
    assertThat(GermanFormats.parseMoney("999.999.999,99")).isEqualTo(Money.ofCents(99_999_999_999L));
  }

  @Test
  void writesAGermanAmount() {
    assertThat(GermanFormats.format(Money.ofCents(123_456))).isEqualTo("1.234,56");
    assertThat(GermanFormats.format(Money.ofCents(99))).isEqualTo("0,99");
  }

  @Test
  void readsAGermanDateAsDayFirst() {
    assertThat(GermanFormats.parseDate("03.02.2022")).isEqualTo(LocalDate.of(2022, 2, 3));
    assertThat(GermanFormats.parseDate("3.2.2022")).isEqualTo(LocalDate.of(2022, 2, 3));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "2022-02-03", "31.02.2022", "13/02/2022", "03.02.22"})
  void rejectsADateThatIsNotGermanOrDoesNotExist(String input) {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> GermanFormats.parseDate(input))
        .withMessageContaining("dd.mm.yyyy");
  }

  @Test
  void writesAGermanDate() {
    assertThat(GermanFormats.format(LocalDate.of(2022, 2, 3))).isEqualTo("03.02.2022");
  }
}
