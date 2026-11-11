package io.github.colla69.makemytaxes.core.year;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.colla69.makemytaxes.core.TaxYear;
import io.github.colla69.makemytaxes.core.expense.Money;
import io.github.colla69.makemytaxes.core.form.Field;
import io.github.colla69.makemytaxes.core.form.FilingCheckForm;
import java.time.DayOfWeek;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The sources are the law versions of 30.06.2024 and 31.12.2024 in law/text. Read them with
 * scripts/law.sh 2024. Where both versions say the same, the test names both.
 */
class YearPack2024Test {

  private final YearPack pack = YearPack.of(new TaxYear(2024)).orElseThrow();

  @Test
  @DisplayName("§ 46(2) no. 1 EStG, versions 30.06.2024 and 31.12.2024: 410 euros")
  void limitForBenefitsAndOtherIncome() {
    assertThat(pack.benefitAndOtherIncomeLimit()).isEqualTo(Money.ofCents(410_00));
  }

  /**
   * The version of 31.12.2024 of § 46(2) no. 4 EStG names no amount. It adds the Grundfreibetrag of
   * § 32a(1) sentence 2 no. 1, the Arbeitnehmer-Pauschbetrag of § 9a sentence 1 no. 1a and the
   * Sonderausgaben-Pauschbetrag of § 10c sentence 1. § 32a(1) of that version holds 11 784 euros,
   * and § 52(1) of that version applies it "erstmals für den Veranlagungszeitraum 2024". The
   * version of 30.06.2024 still holds 13 150 euros and § 32a(1) with 11 604 euros.
   */
  @Test
  @DisplayName(
      "§ 46(2) no. 4 with § 32a(1), § 9a, § 10c and § 52(1) EStG, version 31.12.2024:"
          + " 11 784 + 1 230 + 36 = 13 050 euros")
  void wageLimit() {
    assertThat(pack.wageLimit()).isEqualTo(Money.ofCents((11_784 + 1_230 + 36) * 100L));
    assertThat(pack.wageLimit()).isEqualTo(Money.ofCents(13_050_00));
  }

  /**
   * The source chain for the § 46(2) no. 3 question of the filing check in 2024:
   *
   * <ol>
   *   <li>§ 52(36) sentence 3 EStG, versions 30.06.2024 and 31.12.2024, applies § 46(2) no. 3 in
   *       the wording of Art. 3 of the law of 08.12.2022 (BGBl. I S. 2230) only from 01.01.2026. So
   *       the wording in the two 2024 versions (Erstattung über 410 Euro) does not apply to 2024.
   *   <li>The wording that applies to 2024 is the one in the version of 31.12.2023 (scripts/law.sh
   *       2023 estg "§ 46"): the Teilbeträge der Vorsorgepauschale are higher than the abziehbare
   *       Vorsorgeaufwendungen, and the wage is higher than Grundfreibetrag +
   *       Arbeitnehmer-Pauschbetrag + Sonderausgaben-Pauschbetrag.
   *   <li>The amounts for 2024: Grundfreibetrag 11 784 euros (§ 32a(1) sentence 2 no. 1, version
   *       31.12.2024), Arbeitnehmer-Pauschbetrag 1 230 euros (§ 9a sentence 1 no. 1a),
   *       Sonderausgaben-Pauschbetrag 36 euros (§ 10c sentence 1). The sum is 13 050 euros, the
   *       same amount as for no. 4.
   * </ol>
   */
  @Test
  @DisplayName(
      "§ 46(2) no. 3 EStG, version 31.12.2023, with § 52(36) sentence 3, version 31.12.2024:"
          + " 11 784 + 1 230 + 36 = 13 050 euros in the filing check of 2024")
  void wageLimitOfTheHealthAllowanceQuestion() {
    Field question =
        new FilingCheckForm(pack)
            .fields().stream()
                .filter(field -> field.id().equals("healthAllowance"))
                .findFirst()
                .orElseThrow();

    assertThat(question.line()).isEqualTo("§ 46 Abs. 2 Nr. 3 EStG");
    assertThat(question.label()).contains("Arbeitslohn über 13.050,00 Euro");
    assertThat(pack.wageLimit()).isEqualTo(Money.ofCents((11_784 + 1_230 + 36) * 100L));
  }

  @Test
  @DisplayName("§ 9a sentence 1 no. 1a EStG, versions 30.06.2024 and 31.12.2024: 1 230 euros")
  void employeeLumpSum() {
    assertThat(pack.employeeLumpSum()).isEqualTo(Money.ofCents(1_230_00));
  }

  @Test
  @DisplayName(
      "§ 9(1) sentence 3 no. 4 EStG, versions 30.06.2024 and 31.12.2024: 0,30 euros, 0,38 from km"
          + " 21 for 2022 to 2026, limit 4 500 euros")
  void commutingRates() {
    assertThat(pack.commutePerKmFirst20()).isEqualTo(Money.ofCents(30));
    assertThat(pack.commutePerKmFrom21()).isEqualTo(Money.ofCents(38));
    assertThat(pack.commuteLimitWithoutCar()).isEqualTo(Money.ofCents(4_500_00));
  }

  @Test
  @DisplayName(
      "§ 4(5) sentence 1 no. 6c EStG, versions 30.06.2024 and 31.12.2024: 6 euros, limit 1 260"
          + " euros")
  void homeOffice() {
    assertThat(pack.homeOfficePerDay()).isEqualTo(Money.ofCents(6_00));
    assertThat(pack.homeOfficeLimit()).isEqualTo(Money.ofCents(1_260_00));
  }

  /**
   * Art. 97 § 36(3) no. 3 EGAO, version 31.12.2024, extends the deadline only up to 2023. So 2024
   * has the seven months of § 149(2) sentence 1 AO, version 31.12.2024.
   */
  @Test
  @DisplayName("§ 149(2) sentence 1 AO, version 31.12.2024: seven months, 31.07.2025 is a workday")
  void mandatoryDeadline() {
    assertThat(LocalDate.of(2025, 7, 31).getDayOfWeek()).isEqualTo(DayOfWeek.THURSDAY);
    assertThat(pack.mandatoryDeadline()).isEqualTo(LocalDate.of(2025, 7, 31));
  }

  @Test
  @DisplayName(
      "§ 169(2) sentence 1 no. 2 and § 170(1) AO, versions 30.06.2024 and 31.12.2024: four years"
          + " from the end of the tax year")
  void voluntaryDeadline() {
    assertThat(pack.voluntaryDeadline()).isEqualTo(LocalDate.of(2028, 12, 31));
  }
}
