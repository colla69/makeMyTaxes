package io.github.colla69.makemytaxes.core.expense;

/**
 * A real cost that the user paid. Every number traces to its evidence.
 *
 * @param refunded the part that an insurer or another party paid back. Only medical costs have a
 *     refund, because only the part that nobody refunded is a burden: H 33.1-33.4 EStH, editions 2022 to 2025, "Ersatz von dritter Seite".
 */
public record Expense(Payment payment, Purpose purpose, Evidence evidence, Money refunded) {

  public Expense {
    if (refunded.cents() < 0) {
      throw new IllegalArgumentException("Eine Erstattung kann nicht unter null liegen.");
    }
    if (refunded.isPositive() && purpose.topic() != ExpenseTopic.MEDICAL) {
      throw new IllegalArgumentException(
          "Eine Erstattung erfasst die App nur bei Krankheitskosten.");
    }
    if (refunded.isGreaterThan(payment.amount())) {
      throw new IllegalArgumentException("Die Erstattung ist höher als der bezahlte Betrag.");
    }
  }

  public Expense(Payment payment, Purpose purpose, Evidence evidence) {
    this(payment, purpose, evidence, Money.ofCents(0));
  }

  public Expense(Payment payment, Purpose purpose, String receiptNote) {
    this(payment, purpose, new Evidence(receiptNote, null, ""));
  }

  public boolean hasEvidence() {
    return evidence.isPresent();
  }
}
