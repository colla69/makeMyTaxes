package io.github.colla69.makemytaxes.core.expense;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class ExpenseSummary {

  private static final Money ZERO = Money.ofCents(0);

  private final Map<ExpenseTopic, Money> totals;
  private final long expensesWithoutEvidence;

  private ExpenseSummary(Map<ExpenseTopic, Money> totals, long expensesWithoutEvidence) {
    this.totals = totals;
    this.expensesWithoutEvidence = expensesWithoutEvidence;
  }

  public static ExpenseSummary of(List<Expense> expenses) {
    Map<ExpenseTopic, Money> totals = new EnumMap<>(ExpenseTopic.class);
    for (Expense expense : expenses) {
      totals.merge(expense.purpose().topic(), expense.payment().amount(), Money::plus);
    }
    long withoutEvidence = expenses.stream().filter(expense -> !expense.hasEvidence()).count();
    return new ExpenseSummary(totals, withoutEvidence);
  }

  public Money totalFor(ExpenseTopic topic) {
    return totals.getOrDefault(topic, ZERO);
  }

  public long expensesWithoutEvidence() {
    return expensesWithoutEvidence;
  }
}
