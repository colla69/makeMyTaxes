package io.github.colla69.makemytaxes.core.expense;

/** An amount in euros, kept in cents because tax amounts must not collect rounding errors. */
public record Money(long cents) {

  public static Money ofCents(long cents) {
    return new Money(cents);
  }

  public Money plus(Money other) {
    return new Money(Math.addExact(cents, other.cents));
  }

  public Money times(long factor) {
    return new Money(Math.multiplyExact(cents, factor));
  }

  /** Never below zero, because a reduction cannot turn a cost into income. */
  public Money minusDownToZero(Money other) {
    return new Money(Math.max(0, Math.subtractExact(cents, other.cents)));
  }

  public Money atMost(Money limit) {
    return cents > limit.cents ? limit : this;
  }

  public boolean isGreaterThan(Money other) {
    return cents > other.cents;
  }

  public boolean isPositive() {
    return cents > 0;
  }
}
