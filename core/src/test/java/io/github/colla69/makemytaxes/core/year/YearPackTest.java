package io.github.colla69.makemytaxes.core.year;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.colla69.makemytaxes.core.TaxYear;
import io.github.colla69.makemytaxes.core.expense.Money;
import java.time.DayOfWeek;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
    assertThat(YearPack.of(new TaxYear(2023))).isEmpty();
  }

  /**
   * The sources are the versions 30.06.2025 and 31.12.2025 in law/text, which have the same text
   * for each provision below. Read them with scripts/law.sh 2025. § 52(1) EStG in both versions
   * applies the EStG text first for the Veranlagungszeitraum 2025. The changes that the Hinweis
   * lines of the version 31.12.2025 name do not change these values: the version 30.06.2026 has
   * the same amounts in § 4(5) no. 6c, § 9a, § 10c and § 46. Its new commuting rate of § 9(1) no.
   * 4 applies under its § 52(1) first for 2026.
   */
  @Nested
  class Year2025 {

    private final YearPack pack = YearPack.of(new TaxYear(2025)).orElseThrow();

    @Test
    @DisplayName("§ 46(2) no. 1 EStG, versions 30.06.2025 and 31.12.2025: 410 euros")
    void limitForBenefitsAndOtherIncome() {
      assertThat(pack.benefitAndOtherIncomeLimit()).isEqualTo(Money.ofCents(410_00));
    }

    /**
     * § 46(2) no. 4 EStG, versions 30.06.2025 and 31.12.2025, sets the limit as the sum of the
     * Grundfreibetrag (§ 32a(1) sentence 2 no. 1 EStG: 12 096 euros), the Arbeitnehmer-Pauschbetrag
     * (§ 9a sentence 1 no. 1a EStG: 1 230 euros) and the Sonderausgaben-Pauschbetrag (§ 10c
     * sentence 1 EStG: 36 euros). § 52(36) sentence 3 EStG applies the new no. 3 of the law of
     * 08.12.2022 only from 01.01.2026, so 2025 keeps the earlier no. 3 about the Vorsorgepauschale.
     * That wording, in the versions 30.06.2023 and 31.12.2023, uses the same sum.
     */
    @Test
    @DisplayName("§ 46(2) no. 3 and no. 4 EStG, versions 30.06.2025 and 31.12.2025: 13 362 euros")
    void wageLimit() {
      assertThat(pack.wageLimit()).isEqualTo(Money.ofCents((12_096 + 1_230 + 36) * 100L));
      assertThat(pack.wageLimit()).isEqualTo(Money.ofCents(13_362_00));
    }

    @Test
    @DisplayName("§ 9a sentence 1 no. 1a EStG, versions 30.06.2025 and 31.12.2025: 1 230 euros")
    void employeeLumpSum() {
      assertThat(pack.employeeLumpSum()).isEqualTo(Money.ofCents(1_230_00));
    }

    @Test
    @DisplayName(
        "§ 9(1) no. 4 EStG, versions 30.06.2025 and 31.12.2025: 0,30 euros, 0,38 from km 21 for"
            + " 2022 to 2026, limit 4 500 euros")
    void commutingRates() {
      assertThat(pack.commutePerKmFirst20()).isEqualTo(Money.ofCents(30));
      assertThat(pack.commutePerKmFrom21()).isEqualTo(Money.ofCents(38));
      assertThat(pack.commuteLimitWithoutCar()).isEqualTo(Money.ofCents(4_500_00));
    }

    /** § 52(6) sentence 13 EStG applies no. 6c to work at home after 31.12.2022. */
    @Test
    @DisplayName(
        "§ 4(5) no. 6c EStG, versions 30.06.2025 and 31.12.2025, and § 52(6) EStG: 6 euros,"
            + " limit 1 260 euros")
    void homeOffice() {
      assertThat(pack.homeOfficePerDay()).isEqualTo(Money.ofCents(6_00));
      assertThat(pack.homeOfficeLimit()).isEqualTo(Money.ofCents(1_260_00));
    }

    /** Art. 97 § 36(3) EGAO extends the deadline only for 2020 to 2024. */
    @Test
    @DisplayName(
        "§ 149(2) AO and Art. 97 § 36(3) EGAO, versions 30.06.2025 and 31.12.2025: seven months,"
            + " a Friday")
    void mandatoryDeadline() {
      assertThat(LocalDate.of(2026, 7, 31).getDayOfWeek()).isEqualTo(DayOfWeek.FRIDAY);
      assertThat(pack.mandatoryDeadline()).isEqualTo(LocalDate.of(2026, 7, 31));
    }

    @Test
    @DisplayName("§ 169(2) no. 2 and § 170(1) AO: four years from the end of the tax year")
    void voluntaryDeadline() {
      assertThat(pack.voluntaryDeadline()).isEqualTo(LocalDate.of(2029, 12, 31));
    }
  }
}
