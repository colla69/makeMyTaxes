package io.github.colla69.makemytaxes.core;

public record TaxYear(int value) {

  public TaxYear {
    if (value < 1000 || value > 9999) {
      throw new IllegalArgumentException("Ein Steuerjahr hat vier Ziffern. Eingabe: " + value);
    }
  }
}
