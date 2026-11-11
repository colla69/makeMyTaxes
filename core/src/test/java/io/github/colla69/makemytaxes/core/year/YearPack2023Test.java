package io.github.colla69.makemytaxes.core.year;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.colla69.makemytaxes.core.TaxYear;
import io.github.colla69.makemytaxes.core.expense.Money;
import java.time.DayOfWeek;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The sources are law versions in law/text. Read them with scripts/law.sh 2023. The EStG versions
 * of 30.06.2023 and 31.12.2023 have the same text for each provision here. § 52(1) EStG, version
 * 30.06.2023, applies this text first for 2023.
 */
class YearPack2023Test {

  private final YearPack pack = YearPack.of(new TaxYear(2023)).orElseThrow();

  @Test
  @DisplayName("§ 46(2) no. 1 EStG, versions 30.06.2023 and 31.12.2023: 410 euros")
  void limitForBenefitsAndOtherIncome() {
    assertThat(pack.benefitAndOtherIncomeLimit()).isEqualTo(Money.ofCents(410_00));
  }

  @Test
  @DisplayName(
      "§ 46(2) no. 3 and no. 4 EStG, versions 30.06.2023 and 31.12.2023: the Grundfreibetrag of"
          + " § 32a(1) sentence 2 no. 1 (10 908), the lump sum of § 9a sentence 1 no. 1a (1 230)"
          + " and of § 10c sentence 1 (36)")
  void wageLimitIsTheSumOfThreeAmounts() {
    long sum = 10_908_00 + 1_230_00 + 36_00;

    assertThat(pack.wageLimit()).isEqualTo(Money.ofCents(sum));
    assertThat(pack.wageLimit()).isEqualTo(Money.ofCents(12_174_00));
  }

  @Test
  @DisplayName("§ 9a sentence 1 no. 1a EStG, versions 30.06.2023 and 31.12.2023: 1 230 euros")
  void employeeLumpSum() {
    assertThat(pack.employeeLumpSum()).isEqualTo(Money.ofCents(1_230_00));
  }

  @Test
  @DisplayName(
      "§ 9(1) sentence 3 no. 4 sentence 8 EStG, versions 30.06.2023 and 31.12.2023: 0,30 euros,"
          + " 0,38 from km 21, limit 4 500 euros")
  void commutingRates() {
    assertThat(pack.commutePerKmFirst20()).isEqualTo(Money.ofCents(30));
    assertThat(pack.commutePerKmFrom21()).isEqualTo(Money.ofCents(38));
    assertThat(pack.commuteLimitWithoutCar()).isEqualTo(Money.ofCents(4_500_00));
  }

  @Test
  @DisplayName(
      "§ 4(5) sentence 1 no. 6c EStG, versions 30.06.2023 and 31.12.2023, with § 52(6) sentence"
          + " 12 EStG: 6 euros for each day, limit 1 260 euros")
  void homeOfficeDailyAmount() {
    assertThat(pack.homeOfficePerDay()).isEqualTo(Money.ofCents(6_00));
    assertThat(pack.homeOfficeLimit()).isEqualTo(Money.ofCents(1_260_00));
  }

  @Test
  @DisplayName(
      "§ 149(2) AO, Art. 97 § 36(3) no. 3 c EGAO, § 108(3) AO, versions 30.06.2023 and"
          + " 31.12.2023: eight months, then a workday")
  void mandatoryDeadline() {
    assertThat(LocalDate.of(2024, 8, 31).getDayOfWeek()).isEqualTo(DayOfWeek.SATURDAY);
    assertThat(pack.mandatoryDeadline()).isEqualTo(LocalDate.of(2024, 9, 2));
  }

  @Test
  @DisplayName(
      "§ 169(2) sentence 1 no. 2 and § 170(1) AO with § 36(1) EStG, version 30.06.2023: four years"
          + " from the end of the tax year")
  void voluntaryDeadline() {
    assertThat(pack.voluntaryDeadline()).isEqualTo(LocalDate.of(2027, 12, 31));
  }
}
