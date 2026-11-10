package io.github.colla69.makemytaxes.core.year;

import io.github.colla69.makemytaxes.core.TaxYear;
import io.github.colla69.makemytaxes.core.expense.Money;
import java.time.LocalDate;
import java.time.Year;
import java.util.Map;
import java.util.Optional;

/**
 * The legal values of one tax year. YearPackTest cites the source of each value.
 *
 * @param benefitAndOtherIncomeLimit the 410 euro limit of § 46(2) no. 1 EStG
 * @param wageLimit the wage limit of § 46(2) no. 3 and no. 4 EStG for a single person
 * @param employeeLumpSum the lump sum for work expenses of § 9a sentence 1 no. 1a EStG
 * @param commutePerKmFirst20 the commuting rate for each of the first 20 km, § 9(1) no. 4 EStG
 * @param commutePerKmFrom21 the commuting rate from the 21st km, § 9(1) no. 4 EStG
 * @param commuteLimitWithoutCar the yearly commuting limit without a car, § 9(1) no. 4 EStG
 * @param homeOfficePerDay the home office amount for each day, § 4(5) no. 6b EStG in 2022 and
 *     no. 6c EStG from 2023
 * @param homeOfficeLimit the yearly home office limit, in the same provision
 * @param mandatoryDeadline the last day of a mandatory return without an adviser
 */
public record YearPack(
    TaxYear taxYear,
    Money benefitAndOtherIncomeLimit,
    Money wageLimit,
    Money employeeLumpSum,
    Money commutePerKmFirst20,
    Money commutePerKmFrom21,
    Money commuteLimitWithoutCar,
    Money homeOfficePerDay,
    Money homeOfficeLimit,
    LocalDate mandatoryDeadline) {

  private static final YearPack YEAR_2022 =
      new YearPack(
          new TaxYear(2022),
          Money.ofCents(410_00),
          Money.ofCents(13_150_00),
          Money.ofCents(1_200_00),
          Money.ofCents(30),
          Money.ofCents(38),
          Money.ofCents(4_500_00),
          Money.ofCents(5_00),
          Money.ofCents(600_00),
          LocalDate.of(2023, 10, 2));

  private static final YearPack YEAR_2025 =
      new YearPack(
          new TaxYear(2025),
          Money.ofCents(410_00),
          Money.ofCents(13_362_00),
          Money.ofCents(1_230_00),
          Money.ofCents(30),
          Money.ofCents(38),
          Money.ofCents(4_500_00),
          Money.ofCents(6_00),
          Money.ofCents(1_260_00),
          LocalDate.of(2026, 7, 31));

  private static final YearPack YEAR_2026 =
      new YearPack(
          new TaxYear(2026),
          Money.ofCents(410_00),
          Money.ofCents(13_614_00),
          Money.ofCents(1_230_00),
          Money.ofCents(38),
          Money.ofCents(38),
          Money.ofCents(4_500_00),
          Money.ofCents(6_00),
          Money.ofCents(1_260_00),
          LocalDate.of(2027, 8, 2));

  private static final Map<TaxYear, YearPack> PACKS =
      Map.of(
          YEAR_2022.taxYear, YEAR_2022,
          YEAR_2025.taxYear, YEAR_2025,
          YEAR_2026.taxYear, YEAR_2026);

  public static Optional<YearPack> of(TaxYear taxYear) {
    return Optional.ofNullable(PACKS.get(taxYear));
  }

  /** The tax can be assessed for four years after the end of the tax year: §§ 169, 170 AO. */
  public LocalDate voluntaryDeadline() {
    return LocalDate.of(taxYear.value() + 4, 12, 31);
  }

  public int daysInYear() {
    return Year.of(taxYear.value()).length();
  }
}
