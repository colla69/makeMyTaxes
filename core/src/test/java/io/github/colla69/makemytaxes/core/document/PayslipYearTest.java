package io.github.colla69.makemytaxes.core.document;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.colla69.makemytaxes.core.document.PayslipYear.Payslip;
import io.github.colla69.makemytaxes.core.document.PayslipYear.SourcedCorrection;
import io.github.colla69.makemytaxes.core.expense.Money;
import java.time.Month;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The amounts are made up. */
class PayslipYearTest {

  private static final PayslipValues JANUARY_CORRECTION = values(15_000, 4_000);

  private static final PayslipValues FEBRUARY_CORRECTION = values(0, -1_000);

  private static final Payslip JANUARY =
      payslip("januar.pdf", 1, values(300_000, 40_000), List.of(), values(300_000, 40_000));

  private static final Payslip MARCH =
      payslip(
          "maerz.pdf",
          3,
          values(300_000, 40_000),
          List.of(
              new PayslipCorrection(YearMonth.of(2025, 1), JANUARY_CORRECTION),
              new PayslipCorrection(YearMonth.of(2025, 2), FEBRUARY_CORRECTION)),
          values(915_000, 123_000));

  private static final Payslip FEBRUARY =
      payslip("februar.pdf", 2, values(300_000, 40_000), List.of(), values(600_000, 80_000));

  @Test
  void addsTheCorrectionsOfAMonthFromALaterFile() {
    PayslipYear year = PayslipYear.of(List.of(JANUARY, FEBRUARY, MARCH));

    PayslipYear.MonthValue january = year.month(Month.JANUARY).orElseThrow();
    assertThat(january.taxableGross()).contains(Money.ofCents(315_000));
    assertThat(january.wageTax()).contains(Money.ofCents(44_000));
    assertThat(january.corrections())
        .containsExactly(new SourcedCorrection("maerz.pdf", JANUARY_CORRECTION, List.of()));
    assertThat(year.month(Month.FEBRUARY).orElseThrow().wageTax())
        .contains(Money.ofCents(39_000));
  }

  @Test
  void theSumOfTheMonthsMatchesTheYearColumnOfTheLastPayslip() {
    PayslipYear year = PayslipYear.of(List.of(MARCH, JANUARY, FEBRUARY));
    Payslip last = year.last().orElseThrow();

    PayslipCheck gross =
        PayslipCheck.of(
            year.monthTotals(PayslipValues::taxableGross), last.content().yearToDate().taxableGross());
    PayslipCheck wageTax =
        PayslipCheck.of(
            year.monthTotals(PayslipValues::wageTax), last.content().yearToDate().wageTax());

    assertThat(last).isEqualTo(MARCH);
    assertThat(gross.result("die Jahressumme")).isEqualTo("stimmt überein");
    assertThat(wageTax.result("die Jahressumme")).isEqualTo("stimmt überein");
  }

  @Test
  void countsACorrectionOnceIfTwoFilesPrintIt() {
    Payslip april =
        payslip(
            "april.pdf",
            4,
            values(300_000, 40_000),
            List.of(new PayslipCorrection(YearMonth.of(2025, 1), JANUARY_CORRECTION)),
            values(1_215_000, 163_000));

    PayslipYear year = PayslipYear.of(List.of(JANUARY, FEBRUARY, MARCH, april));

    assertThat(year.month(Month.JANUARY).orElseThrow().corrections())
        .containsExactly(
            new SourcedCorrection("maerz.pdf", JANUARY_CORRECTION, List.of("april.pdf")));
    assertThat(year.month(Month.JANUARY).orElseThrow().wageTax()).contains(Money.ofCents(44_000));
  }

  @Test
  void saysSoIfTheSumOfTheMonthsDoesNotMatch() {
    PayslipYear year = PayslipYear.of(List.of(JANUARY, MARCH));

    PayslipCheck wageTax =
        PayslipCheck.of(
            year.monthTotals(PayslipValues::wageTax),
            year.last().orElseThrow().content().yearToDate().wageTax());

    assertThat(wageTax.result("die Jahressumme"))
        .isEqualTo("Differenz 400,00 €: die Jahressumme ist höher");
  }

  @Test
  void aMonthWithACorrectionThatWasNotReadHasNoValue() {
    Payslip march =
        payslip(
            "maerz.pdf",
            3,
            values(300_000, 40_000),
            List.of(
                new PayslipCorrection(
                    YearMonth.of(2025, 1),
                    new PayslipValues(Optional.of(Money.ofCents(0)), Optional.empty()))),
            values(900_000, 120_000));

    PayslipYear year = PayslipYear.of(List.of(JANUARY, march));

    assertThat(year.month(Month.JANUARY).orElseThrow().wageTax()).isEmpty();
  }

  @Test
  void aMonthWithoutPayslipOrCorrectionHasNoValue() {
    assertThat(PayslipYear.of(List.of(JANUARY)).month(Month.MAY)).isEmpty();
  }

  private static Payslip payslip(
      String fileName,
      int month,
      PayslipValues own,
      List<PayslipCorrection> corrections,
      PayslipValues yearToDate) {
    return new Payslip(
        fileName, YearMonth.of(2025, month), new PayslipContent(own, corrections, yearToDate));
  }

  private static PayslipValues values(long grossCents, long wageTaxCents) {
    return new PayslipValues(
        Optional.of(Money.ofCents(grossCents)), Optional.of(Money.ofCents(wageTaxCents)));
  }
}
