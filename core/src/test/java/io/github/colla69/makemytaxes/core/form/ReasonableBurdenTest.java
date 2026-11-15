package io.github.colla69.makemytaxes.core.form;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.colla69.makemytaxes.core.expense.Money;
import io.github.colla69.makemytaxes.core.form.ReasonableBurden.Group;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * § 33(3) EStG, versions of 30.06.2022, 31.12.2022, 30.06.2025 and 31.12.2025 (the same text in
 * each version from 30.06.2022 to 05.11.2026), applied from 2021 by § 52(33c) EStG. The table has a
 * percentage for each row and each income band. H 33.1-33.4 EStH, editions 2022 and 2025,
 * "Zumutbare Belastung": the bands are steps, and only the part above a step gets its higher
 * percentage.
 */
class ReasonableBurdenTest {

  /**
   * One case for each row and each band. In the first band the whole income has the first
   * percentage. 30.000 EUR is in the second band, 60.000 EUR in the third.
   */
  @ParameterizedTest(name = "{0}, {1} EUR: {2} EUR")
  @CsvSource({
    // Row 1a, § 32a(1): 5, 6, 7 percent.
    "BASIC_TARIFF, 10000_00, 500_00",
    "BASIC_TARIFF, 30000_00, 1646_60", // 15.340 x 5 % + 14.660 x 6 % = 767,00 + 879,60
    "BASIC_TARIFF, 60000_00, 3535_30", // 767,00 + 35.790 x 6 % + 8.870 x 7 % = + 2.147,40 + 620,90
    // Row 1b, § 32a(5) or (6): 4, 5, 6 percent.
    "SPLITTING_TARIFF, 10000_00, 400_00",
    "SPLITTING_TARIFF, 30000_00, 1346_60", // 613,60 + 733,00
    "SPLITTING_TARIFF, 60000_00, 2935_30", // 613,60 + 1.789,50 + 532,20
    // Row 2a, one or two children: 2, 3, 4 percent.
    "ONE_OR_TWO_CHILDREN, 10000_00, 200_00",
    "ONE_OR_TWO_CHILDREN, 30000_00, 746_60", // 306,80 + 439,80
    "ONE_OR_TWO_CHILDREN, 60000_00, 1735_30", // 306,80 + 1.073,70 + 354,80
    // Row 2b, three or more children: 1, 1, 2 percent.
    "THREE_OR_MORE_CHILDREN, 10000_00, 100_00",
    "THREE_OR_MORE_CHILDREN, 30000_00, 300_00", // 153,40 + 146,60
    "THREE_OR_MORE_CHILDREN, 60000_00, 688_70", // 153,40 + 357,90 + 177,40
  })
  void eachRowAndBandHasThePercentageOfTheTable(Group group, String income, String burden) {
    assertThat(ReasonableBurden.of(money(income), group)).isEqualTo(money(burden));
  }

  /** "bis 15 340 EUR" includes the limit, "über 51 130 EUR" starts after it. */
  @ParameterizedTest
  @CsvSource({
    "15340_00, 767_00",
    "51130_00, 2914_40", // 767,00 + 35.790 x 6 %
    "51130_01, 2914_40", // one cent at 7 %, rounded down
  })
  void theLimitsOfTheBandsBelongToTheLowerBand(String income, String burden) {
    assertThat(ReasonableBurden.of(money(income), Group.BASIC_TARIFF)).isEqualTo(money(burden));
  }

  @ParameterizedTest
  @CsvSource({
    "0, false, BASIC_TARIFF",
    "0, true, SPLITTING_TARIFF",
    "1, false, ONE_OR_TWO_CHILDREN",
    "2, true, ONE_OR_TWO_CHILDREN",
    "3, false, THREE_OR_MORE_CHILDREN",
    "5, true, THREE_OR_MORE_CHILDREN",
  })
  void theRowDependsOnTheChildrenAndWithoutChildrenOnTheTariff(
      long children, boolean splitting, Group group) {
    assertThat(Group.of(children, splitting)).isEqualTo(group);
  }

  @ParameterizedTest
  @CsvSource({"0", "-100_00"})
  void noIncomeHasNoBurden(String income) {
    assertThat(ReasonableBurden.of(money(income), Group.BASIC_TARIFF)).isEqualTo(Money.ofCents(0));
  }

  private static Money money(String cents) {
    return Money.ofCents(Long.parseLong(cents.replace("_", "")));
  }
}
