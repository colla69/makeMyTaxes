package io.github.colla69.makemytaxes.core.year;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.colla69.makemytaxes.core.TaxYear;
import io.github.colla69.makemytaxes.core.expense.Money;
import java.time.DayOfWeek;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The sources are the law versions of 30.06.2026 and 05.11.2026 in law/text. Read them with
 * scripts/law.sh 2026. Both versions have the same text for each provision here.
 */
class YearPack2026Test {

  private final YearPack pack = YearPack.of(new TaxYear(2026)).orElseThrow();

  @Test
  @DisplayName("§ 46(2) no. 1 EStG, versions 30.06.2026 and 05.11.2026: 410 euros")
  void limitForBenefitsAndOtherIncome() {
    assertThat(pack.benefitAndOtherIncomeLimit()).isEqualTo(Money.ofCents(410_00));
  }

  @Test
  @DisplayName(
      "§ 46(2) no. 3 and no. 4 EStG with § 52(36) sentence 3 EStG, versions 30.06.2026 and"
          + " 05.11.2026: Grundfreibetrag 12 348 (§ 32a(1) sentence 2 no. 1) + Arbeitnehmer-"
          + "Pauschbetrag 1 230 (§ 9a sentence 1 no. 1a) + Sonderausgaben-Pauschbetrag 36 (§ 10c"
          + " sentence 1) = 13 614 euros")
  void wageLimitIsTheSumOfThreeAmounts() {
    assertThat(pack.wageLimit()).isEqualTo(Money.ofCents((12_348 + 1_230 + 36) * 100L));
    assertThat(pack.wageLimit()).isEqualTo(Money.ofCents(13_614_00));
  }

  @Test
  @DisplayName("§ 9a sentence 1 no. 1a EStG, versions 30.06.2026 and 05.11.2026: 1 230 euros")
  void employeeLumpSum() {
    assertThat(pack.employeeLumpSum()).isEqualTo(Money.ofCents(1_230_00));
  }

  @Test
  @DisplayName(
      "§ 9(1) sentence 3 no. 4 sentence 2 EStG, versions 30.06.2026 and 05.11.2026: 0,38 euros"
          + " from the first km, limit 4 500 euros")
  void commutingRateIsTheSameFromTheFirstKm() {
    assertThat(pack.commutePerKmFirst20()).isEqualTo(Money.ofCents(38));
    assertThat(pack.commutePerKmFrom21()).isEqualTo(Money.ofCents(38));
    assertThat(pack.commuteLimitWithoutCar()).isEqualTo(Money.ofCents(4_500_00));
  }

  @Test
  @DisplayName(
      "§ 4(5) sentence 1 no. 6c EStG with § 52(6) sentence 13 EStG, versions 30.06.2026 and"
          + " 05.11.2026: 6 euros a day, limit 1 260 euros")
  void homeOffice() {
    assertThat(pack.homeOfficePerDay()).isEqualTo(Money.ofCents(6_00));
    assertThat(pack.homeOfficeLimit()).isEqualTo(Money.ofCents(1_260_00));
  }

  @Test
  @DisplayName(
      "§ 149(2) sentence 1 AO, versions 30.06.2026 and 05.11.2026: seven months; Art. 97 § 36(3)"
          + " EGAO covers only 2020 to 2024; § 108(3) AO: then a workday")
  void mandatoryDeadline() {
    assertThat(LocalDate.of(2027, 7, 31).getDayOfWeek()).isEqualTo(DayOfWeek.SATURDAY);
    assertThat(pack.mandatoryDeadline()).isEqualTo(LocalDate.of(2027, 8, 2));
  }

  @Test
  @DisplayName("§ 169(2) no. 2 and § 170(1) AO: four years from the end of the tax year")
  void voluntaryDeadline() {
    assertThat(pack.voluntaryDeadline()).isEqualTo(LocalDate.of(2030, 12, 31));
  }
}
