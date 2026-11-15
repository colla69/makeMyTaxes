package io.github.colla69.makemytaxes.core.form;

import io.github.colla69.makemytaxes.core.expense.Expense;
import io.github.colla69.makemytaxes.core.expense.Money;
import io.github.colla69.makemytaxes.core.year.YearPack;
import java.util.List;

/**
 * Estimates the part of the medical costs that reduces the income: § 33(1) EStG. Only the part
 * above the {@link ReasonableBurden} counts. This is an estimate, because the tax office computes
 * the amounts.
 *
 * <p>The app knows only the income from employment. So the Gesamtbetrag der Einkünfte of § 2(3)
 * EStG is the gross wage of all employers less the work expenses, or less the lump sum of § 9a
 * sentence 1 no. 1a EStG if that is higher. Only the lump sum stops at the wage (§ 9a sentence 2
 * EStG). The estimate does not deduct the Altersentlastungsbetrag or the Entlastungsbetrag für
 * Alleinerziehende, so for a person with one of them it is too high.
 *
 * @param costs the paid medical costs
 * @param refunds the refunds of these costs
 * @param burden the costs that nobody refunded
 * @param totalIncome the estimated Gesamtbetrag der Einkünfte
 * @param group the row of the table of § 33(3) EStG
 * @param reasonableBurden the zumutbare Belastung
 * @param deductible the part of the burden above the zumutbare Belastung
 * @param wageKnown false while Anlage N has no gross wage: then the estimate has no income, and
 *     the deductible part would be too high
 */
public record MedicalCostEstimate(
    Money costs,
    Money refunds,
    Money burden,
    Money totalIncome,
    ReasonableBurden.Group group,
    Money reasonableBurden,
    Money deductible,
    boolean wageKnown) {

  public static MedicalCostEstimate of(
      YearPack pack, Answers anlageN, Answers burdens, List<Expense> expenses) {
    Money costs = Money.ofCents(0);
    Money refunds = Money.ofCents(0);
    for (Expense expense : ExtraordinaryBurdensForm.medicalCosts(expenses)) {
      costs = costs.plus(expense.payment().amount());
      refunds = refunds.plus(expense.refunded());
    }
    Money burden = costs.minusDownToZero(refunds);
    Money grossWage = grossWage(anlageN);
    WorkExpenseEstimate work = WorkExpenseEstimate.of(pack, anlageN, expenses);
    Money totalIncome =
        work.isAboveLumpSum()
            ? Money.ofCents(grossWage.cents() - work.total().cents())
            : grossWage.minusDownToZero(work.lumpSum());
    ReasonableBurden.Group group =
        ReasonableBurden.Group.of(
            burdens.wholeNumber(ExtraordinaryBurdensForm.CHILDREN),
            Kind.YES.equals(burdens.text(ExtraordinaryBurdensForm.SPLITTING)));
    Money reasonableBurden = ReasonableBurden.of(totalIncome, group);
    return new MedicalCostEstimate(
        costs,
        refunds,
        burden,
        totalIncome,
        group,
        reasonableBurden,
        burden.minusDownToZero(reasonableBurden),
        grossWage.isPositive());
  }

  private static Money grossWage(Answers anlageN) {
    Money grossWage = Money.ofCents(0);
    for (int employer = 1; employer <= AnlageN.employerCount(anlageN); employer++) {
      grossWage = grossWage.plus(anlageN.money(AnlageN.employerFieldId(employer, "grossWage")));
    }
    return grossWage;
  }

  public boolean hasCosts() {
    return costs.isPositive();
  }
}
