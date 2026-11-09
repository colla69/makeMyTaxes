package io.github.colla69.makemytaxes.core.expense;

/**
 * A real cost that the user paid. Every number traces to its evidence. The evidence is the linked
 * document, and the document name is null if the expense has no document. The receipt reference is
 * only a note that says where the receipt is, for example a file name.
 */
public record Expense(
    Payment payment, Purpose purpose, String receiptReference, String documentName) {

  public Expense(Payment payment, Purpose purpose, String receiptReference) {
    this(payment, purpose, receiptReference, null);
  }

  public boolean hasEvidence() {
    return documentName != null;
  }

  public boolean hasReceiptNote() {
    return receiptReference != null && !receiptReference.isBlank();
  }
}
