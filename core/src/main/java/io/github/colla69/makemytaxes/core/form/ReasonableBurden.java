package io.github.colla69.makemytaxes.core.form;

import io.github.colla69.makemytaxes.core.expense.Money;

/**
 * The zumutbare Belastung of § 33(3) EStG, versions of 30.06.2022 to 05.11.2026, which § 52(33c)
 * EStG applies from 2021. The text and the amounts are the same in each of these versions, so the
 * table holds for 2022 to 2026. H 33.1-33.4 EStH, editions 2022 to 2025, "Zumutbare Belastung":
 * each step of the Gesamtbetrag der Einkünfte has its own percentage, and only the part above a
 * step gets the higher one.
 */
public final class ReasonableBurden {

  /** The end of the first step of § 33(3) sentence 1 EStG. */
  static final Money FIRST_STEP = Money.ofCents(15_340_00);

  /** The end of the second step of § 33(3) sentence 1 EStG. */
  static final Money SECOND_STEP = Money.ofCents(51_130_00);

  /** The rows of the table, with the percentage of each step. */
  public enum Group {
    /** Row 1a: no children, tariff of § 32a(1) EStG. */
    BASIC_TARIFF("ohne Kinder, Grundtarif (§ 32a Abs. 1 EStG)", 5, 6, 7),
    /** Row 1b: no children, splitting tariff of § 32a(5) or (6) EStG. */
    SPLITTING_TARIFF("ohne Kinder, Splitting-Verfahren (§ 32a Abs. 5 oder 6 EStG)", 4, 5, 6),
    /** Row 2a: one or two children. */
    ONE_OR_TWO_CHILDREN("mit einem Kind oder zwei Kindern", 2, 3, 4),
    /** Row 2b: three or more children. */
    THREE_OR_MORE_CHILDREN("mit drei oder mehr Kindern", 1, 1, 2);

    private final String label;
    private final int[] percents;

    Group(String label, int... percents) {
      this.label = label;
      this.percents = percents;
    }

    /** The row of the table. With children, the tariff does not matter. */
    public static Group of(long children, boolean splitting) {
      if (children >= 3) {
        return THREE_OR_MORE_CHILDREN;
      }
      if (children >= 1) {
        return ONE_OR_TWO_CHILDREN;
      }
      return splitting ? SPLITTING_TARIFF : BASIC_TARIFF;
    }

    public String label() {
      return label;
    }

    /** The percentages of the three steps, as the table prints them, for example "5 / 6 / 7". */
    public String percents() {
      return percents[0] + " / " + percents[1] + " / " + percents[2];
    }
  }

  private ReasonableBurden() {}

  /**
   * The zumutbare Belastung for this Gesamtbetrag der Einkünfte. The law has no rounding rule.
   * The app drops the fractions of a cent of each step, which changes the result by less than 3
   * cents.
   */
  public static Money of(Money totalIncome, Group group) {
    long income = Math.max(0, totalIncome.cents());
    long first = Math.min(income, FIRST_STEP.cents());
    long second = Math.min(income, SECOND_STEP.cents()) - first;
    long third = income - first - second;
    return Money.ofCents(
        first * group.percents[0] / 100
            + second * group.percents[1] / 100
            + third * group.percents[2] / 100);
  }
}
