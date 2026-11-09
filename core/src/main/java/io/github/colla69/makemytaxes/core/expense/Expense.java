package io.github.colla69.makemytaxes.core.expense;

/** A real cost that the user paid. Every number traces to its evidence. */
public record Expense(Payment payment, Purpose purpose, Evidence evidence) {

  public Expense(Payment payment, Purpose purpose, String receiptNote) {
    this(payment, purpose, new Evidence(receiptNote, null, ""));
  }

  public boolean hasEvidence() {
    return evidence.isPresent();
  }
}
