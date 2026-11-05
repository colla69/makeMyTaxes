package io.github.colla69.makemytaxes.core.year;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.colla69.makemytaxes.core.TaxYear;
import io.github.colla69.makemytaxes.core.expense.Money;
import java.time.DayOfWeek;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The sources are law versions in law/text. Read them with scripts/law.sh 2022. */
class YearPackTest {

  private final YearPack pack = YearPack.of(new TaxYear(2022)).orElseThrow();

  @Test
  @DisplayName("§ 46(2) no. 1 EStG, version 31.12.2022: 410 euros")
  void limitForBenefitsAndOtherIncome() {
    assertThat(pack.benefitAndOtherIncomeLimit()).isEqualTo(Money.ofCents(410_00));
  }

  @Test
  @DisplayName("§ 46(2) no. 3 and no. 4 EStG, version 31.12.2022: 13 150 euros")
  void wageLimit() {
    assertThat(pack.wageLimit()).isEqualTo(Money.ofCents(13_150_00));
  }

  @Test
  @DisplayName("§ 9a sentence 1 no. 1a EStG, version 30.06.2022: 1 200 euros")
  void employeeLumpSum() {
    assertThat(pack.employeeLumpSum()).isEqualTo(Money.ofCents(1_200_00));
  }

  @Test
  @DisplayName("§ 9(1) no. 4 EStG, version 31.12.2022: 0,30 euros, 0,38 from km 21, limit 4 500 euros")
  void commutingRates() {
    assertThat(pack.commutePerKmFirst20()).isEqualTo(Money.ofCents(30));
    assertThat(pack.commutePerKmFrom21()).isEqualTo(Money.ofCents(38));
    assertThat(pack.commuteLimitWithoutCar()).isEqualTo(Money.ofCents(4_500_00));
  }

  @Test
  @DisplayName("§ 4(5) no. 6b EStG, version 30.06.2022, and § 52(6) EStG: 5 euros, limit 600 euros")
  void homeOfficeUsesTheWordingFor2022AndNotTheYearEndText() {
    assertThat(pack.homeOfficePerDay()).isEqualTo(Money.ofCents(5_00));
    assertThat(pack.homeOfficeLimit()).isEqualTo(Money.ofCents(600_00));
  }

  @Test
  @DisplayName("§ 149(2) AO, Art. 97 § 36(3) no. 3 b EGAO, § 108(3) AO: nine months, then a workday")
  void mandatoryDeadline() {
    assertThat(LocalDate.of(2023, 9, 30).getDayOfWeek()).isEqualTo(DayOfWeek.SATURDAY);
    assertThat(pack.mandatoryDeadline()).isEqualTo(LocalDate.of(2023, 10, 2));
  }

  @Test
  @DisplayName("§ 169(2) no. 2 and § 170(1) AO: four years from the end of the tax year")
  void voluntaryDeadline() {
    assertThat(pack.voluntaryDeadline()).isEqualTo(LocalDate.of(2026, 12, 31));
  }

  @Test
  void aYearWithoutAYearPackHasNone() {
    assertThat(YearPack.of(new TaxYear(2021))).isEmpty();
  }
}
