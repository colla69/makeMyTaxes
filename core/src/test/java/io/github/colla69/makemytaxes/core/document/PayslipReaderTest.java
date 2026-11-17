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

  private static final YearMonth MARCH_2025 = YearMonth.of(2025, 3);

  /** A statement of the month, then a correction of January, over two pages. */
  private static final String ENTGELTABRECHNUNG =
      """
      Beispiel Werkstatt GmbH
      Entgeltabrechnung für den Zeitraum vom 01.03. bis 31.03.2025
      Bezeichnung          Monat       Jahr
      Steuerbrutto, EZ    1.000,00    1.000,00
      Steuerbrutto, lfd.  3.000,00   30.000,00
      Lohnsteuer, EZ        300,00      300,00
      Lohnsteuer, lfd.      500,00    5.000,00
      Seite 1 von 2
      Entgeltabrechnung für den Zeitraum vom 01.01. bis 31.01.2025
      Rückrechnung
      Steuerbrutto, lfd.  2.800,00   24.000,00
      Lohnsteuer, lfd.      450,00    4.000,00
      Seite 2 von 2
      """;

  @Test
  void readsTheEntgeltabrechnungOfTheMonthAndAddsTheOneOffPay() {
    PayslipValues values = PayslipReader.read(ENTGELTABRECHNUNG, MARCH_2025);

    assertThat(values.taxableGross()).contains(Money.ofCents(400_000));
    assertThat(values.wageTax()).contains(Money.ofCents(80_000));
  }

  @Test
  void readsTheRunningPayAloneWithoutAnEzLine() {
    String text =
        ENTGELTABRECHNUNG
            .replace("Steuerbrutto, EZ    1.000,00    1.000,00\n", "")
            .replace("Lohnsteuer, EZ        300,00      300,00\n", "");

    PayslipValues values = PayslipReader.read(text, MARCH_2025);

    assertThat(values.taxableGross()).contains(Money.ofCents(300_000));
    assertThat(values.wageTax()).contains(Money.ofCents(50_000));
  }

  @Test
  void readsTheCorrectionOnlyForItsOwnMonth() {
    PayslipValues values = PayslipReader.read(ENTGELTABRECHNUNG, YearMonth.of(2025, 1));

    assertThat(values.taxableGross()).contains(Money.ofCents(280_000));
    assertThat(values.wageTax()).contains(Money.ofCents(45_000));
  }

  @Test
  void readsNothingFromAnEntgeltabrechnungOfAnotherMonth() {
    assertThat(PayslipReader.read(ENTGELTABRECHNUNG, YearMonth.of(2025, 2)))
        .isEqualTo(PayslipValues.NONE);
    assertThat(PayslipReader.read(ENTGELTABRECHNUNG, YearMonth.of(2024, 3)))
        .isEqualTo(PayslipValues.NONE);
  }

  @Test
  void readsAStatementOfTheMonthThatGoesOnOverAnotherPage() {
    String text =
        ENTGELTABRECHNUNG.replace(
            "Lohnsteuer, EZ        300,00      300,00\n",
            "Seite 1 von 3\nEntgeltabrechnung für den Zeitraum vom 01.03. bis 31.03.2025\n"
                + "Lohnsteuer, EZ        300,00      300,00\n");

    assertThat(PayslipReader.read(text, MARCH_2025).wageTax()).contains(Money.ofCents(80_000));
  }

  @Test
  void doesNotReadTwoDifferentAmountsForTheSameLabelInTheMonth() {
    String text =
        ENTGELTABRECHNUNG.replace(
            "Lohnsteuer, lfd.      500,00    5.000,00\n",
            "Lohnsteuer, lfd.      500,00    5.000,00\nLohnsteuer, lfd.      510,00    5.010,00\n");

    PayslipValues values = PayslipReader.read(text, MARCH_2025);

    assertThat(values.wageTax()).isEmpty();
    assertThat(values.taxableGross()).contains(Money.ofCents(400_000));
  }

  @Test
  void doesNotReadANegativeAmountInTheEntgeltabrechnung() {
    String text = ENTGELTABRECHNUNG.replace("Lohnsteuer, EZ        300,00", "Lohnsteuer, EZ       -300,00");

    assertThat(PayslipReader.read(text, MARCH_2025).wageTax()).isEmpty();
  }

  @Test
  void readsAMonthWithoutOneOffPayWhereTheEzLineShowsOnlyTheSumOfTheYear() {
    String text =
        """
        Zeitraum vom 01.04. bis 30.04.2025
        Steuerbrutto, lfd. 3.000,00 12.000,00
        Lohnsteuer, lfd. 400,00 1.600,00
        Steuerbrutto, EZ 500,00
        Lohnsteuer, EZ 100,00
        """;

    PayslipValues values = PayslipReader.read(text, YearMonth.of(2025, 4));

    assertThat(values.taxableGross()).contains(Money.ofCents(300_000));
    assertThat(values.wageTax()).contains(Money.ofCents(40_000));
  }

  @Test
  void doesNotReadAnEzLineWithOnlyANegativeAmount() {
    String text =
        ENTGELTABRECHNUNG.replace(
            "Lohnsteuer, EZ        300,00      300,00", "Lohnsteuer, EZ       -300,00");

    assertThat(PayslipReader.read(text, MARCH_2025).wageTax()).isEmpty();
  }

  @Test
  void doesNotReadEzLinesWithOneAndTwoColumnsInTheSameMonth() {
    String text =
        ENTGELTABRECHNUNG.replace(
            "Steuerbrutto, EZ    1.000,00    1.000,00\n",
            "Steuerbrutto, EZ    1.000,00    1.000,00\nSteuerbrutto, EZ    1.000,00\n");

    assertThat(PayslipReader.read(text, MARCH_2025).taxableGross()).isEmpty();
  }

  @Test
  void doesNotReadALfdLineWithOneColumn() {
    String text =
        ENTGELTABRECHNUNG.replace("Lohnsteuer, lfd.      500,00    5.000,00", "Lohnsteuer, lfd. 500,00");

    assertThat(PayslipReader.read(text, MARCH_2025).wageTax()).isEmpty();
  }

  @Test
  void doesNotReadTheOneOffPayAloneWithoutTheRunningPay() {
    String text = ENTGELTABRECHNUNG.replace("Steuerbrutto, lfd.  3.000,00   30.000,00\n", "");

    assertThat(PayslipReader.read(text, MARCH_2025).taxableGross()).isEmpty();
  }
}
