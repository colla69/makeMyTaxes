package io.github.colla69.makemytaxes.core.document;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.colla69.makemytaxes.core.expense.Money;
import java.time.YearMonth;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The payslip texts are made up. */
class PayslipReaderTest {

  private static final YearMonth MARCH_2022 = YearMonth.of(2022, 3);

  private static final String PAYSLIP =
      """
      Abrechnung der Brutto/Netto-Bezüge für März 2022
      Beispiel Werkstatt GmbH
      Gesamt-Brutto 3.250,00
      Steuer-Brutto 3.200,00
      Lohnsteuer 410,25
      Kirchensteuer 0,00
      Auszahlungsbetrag 2.300,00
      """;

  @Test
  void readsTheTaxableGrossAndTheWageTax() {
    PayslipValues values = PayslipReader.read(PAYSLIP, MARCH_2022);

    assertThat(values.taxableGross()).contains(Money.ofCents(320_000));
    assertThat(values.wageTax()).contains(Money.ofCents(41_025));
  }

  @Test
  void readsNothingFromAPayslipOfAnotherMonth() {
    assertThat(PayslipReader.read(PAYSLIP, YearMonth.of(2022, 4))).isEqualTo(PayslipValues.NONE);
    assertThat(PayslipReader.read(PAYSLIP, YearMonth.of(2023, 3))).isEqualTo(PayslipValues.NONE);
  }

  @Test
  void readsNothingFromATextWithoutText() {
    assertThat(PayslipReader.read("", MARCH_2022)).isEqualTo(PayslipValues.NONE);
  }

  @Test
  void doesNotReadALabelWithTwoDifferentAmounts() {
    String text = PAYSLIP + "Lohnsteuer 99,00\n";

    PayslipValues values = PayslipReader.read(text, MARCH_2022);

    assertThat(values.wageTax()).isEmpty();
    assertThat(values.taxableGross()).contains(Money.ofCents(320_000));
  }

  @Test
  void readsTheSameAmountTwiceAsOneAmount() {
    String text = PAYSLIP + "Lohnsteuer 410,25\n";

    assertThat(PayslipReader.read(text, MARCH_2022).wageTax()).contains(Money.ofCents(41_025));
  }

  @Test
  void doesNotReadANegativeAmount() {
    String text = PAYSLIP.replace("Lohnsteuer 410,25", "Lohnsteuer 410,25-");

    assertThat(PayslipReader.read(text, MARCH_2022).wageTax()).isEqualTo(Optional.empty());
  }

  @Test
  void doesNotTakeAnotherLabelThatEndsLikeTheLabel() {
    String text = PAYSLIP.replace("Lohnsteuer 410,25", "Pauschal-Lohnsteuer 410,25");

    assertThat(PayslipReader.read(text, MARCH_2022).wageTax()).isEmpty();
  }
}
