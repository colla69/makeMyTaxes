package io.github.colla69.makemytaxes.core.form;

import io.github.colla69.makemytaxes.core.expense.Expense;
import io.github.colla69.makemytaxes.core.expense.Money;
import io.github.colla69.makemytaxes.core.year.YearPack;
import java.util.List;

/**
 * Compares the work expenses with the lump sum, which applies unless the expenses are higher: § 9a
 * EStG. This is an estimate, because the tax office calculates the amounts. Employer payments for
 * commuting (line 39 of Anlage N) reduce the commuting amount: § 3 no. 15 sentence 3 and § 40(2)
 * EStG. With several employers, the payments of all employers reduce it.
 */
public record WorkExpenseEstimate(
    Money commute, Money homeOffice, Money listed, Money total, Money lumpSum) {

  public static WorkExpenseEstimate of(YearPack pack, Answers anlageN, List<Expense> expenses) {
    Money commute =
        commute(
            pack,
            anlageN.wholeNumber("commuteDays"),
            anlageN.wholeNumber("commuteKm"),
            Kind.CAR.equals(anlageN.text("commuteTransport")))
            .minusDownToZero(AnlageN.employerCommute(anlageN));
    Money homeOffice =
        pack.homeOfficePerDay().times(anlageN.wholeNumber("homeOfficeDays")).atMost(pack.homeOfficeLimit());
    Money listed = Money.ofCents(0);
    for (Expense expense : expenses) {
      if (expense.purpose().topic().isWorkExpense()) {
        listed = listed.plus(expense.payment().amount());
      }
    }
    return new WorkExpenseEstimate(
        commute, homeOffice, listed, commute.plus(homeOffice).plus(listed), pack.employeeLumpSum());
  }

  static Money commute(YearPack pack, long days, long km, boolean car) {
    Money perDay =
        pack.commutePerKmFirst20()
            .times(Math.min(km, 20))
            .plus(pack.commutePerKmFrom21().times(Math.max(km - 20, 0)));
    Money total = perDay.times(days);
    return car ? total : total.atMost(pack.commuteLimitWithoutCar());
  }

  public boolean isAboveLumpSum() {
    return total.isGreaterThan(lumpSum);
  }
}
