package io.github.colla69.makemytaxes.core.expense;

/**
 * A real cost that the user paid. The receipt reference says where the receipt is, for example a
 * file name. See docs/adr/0009: every number traces to its evidence.
 */
public record Expense(Payment payment, Purpose purpose, String receiptReference) {

  public boolean hasEvidence() {
    return receiptReference != null && !receiptReference.isBlank();
  }
}
