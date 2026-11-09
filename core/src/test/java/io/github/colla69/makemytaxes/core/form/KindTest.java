package io.github.colla69.makemytaxes.core.form;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class KindTest {

  private static final String MADE_UP_TAX_ID = "86402715392"; // privacy-scan:allow made up
  private static final String MADE_UP_IBAN = "DE02120300000000202051"; // privacy-scan:allow made up

  @Test
  void acceptsAValidTaxIdWithOrWithoutSpaces() {
    assertThatCode(() -> Kind.TAX_ID.validate(MADE_UP_TAX_ID)).doesNotThrowAnyException();
    assertThatCode(() -> Kind.TAX_ID.validate(MADE_UP_TAX_ID.replaceAll("(..)(...)(...)(...)", "$1 $2 $3 $4")))
        .doesNotThrowAnyException();
  }

  @Test
  void rejectsATaxIdWithAWrongCheckDigit() {
    String last = MADE_UP_TAX_ID.substring(10);
    String wrong = MADE_UP_TAX_ID.substring(0, 10) + (last.equals("9") ? "0" : "9");

    assertThatIllegalArgumentException().isThrownBy(() -> Kind.TAX_ID.validate(wrong));
  }

  @Test
  void acceptsAValidIbanAndRejectsATypingError() {
    assertThatCode(() -> Kind.IBAN.validate(MADE_UP_IBAN)).doesNotThrowAnyException();
    assertThatCode(() -> Kind.IBAN.validate(MADE_UP_IBAN.toLowerCase())).doesNotThrowAnyException();
    String typo = MADE_UP_IBAN.substring(0, 21) + (MADE_UP_IBAN.endsWith("9") ? "0" : "9");

    assertThatIllegalArgumentException().isThrownBy(() -> Kind.IBAN.validate(typo));
  }

  @Test
  void showsMoneyAndDatesInTheFormatOfTheApp() {
    assertThat(Kind.MONEY.display("1234,5")).isEqualTo("1.234,50");
    assertThat(Kind.DATE.display("3.2.1990")).isEqualTo("03.02.1990");
    assertThat(Kind.YES_NO_UNSURE.display("UNSURE")).isEqualTo("Weiß nicht");
  }

  @ParameterizedTest
  @CsvSource({
    "MONEY, 12.5.3",
    "WHOLE_NUMBER, 1.5",
    "WHOLE_NUMBER, -3",
    "DATE, 31.02.2022",
    "YES_NO_UNSURE, MAYBE",
    "COMMUTE_TRANSPORT, BOAT",
    "PAYMENT, CHEQUE"
  })
  void rejectsAValueOfTheWrongKind(Kind kind, String value) {
    assertThatIllegalArgumentException().isThrownBy(() -> kind.validate(value));
  }
}
