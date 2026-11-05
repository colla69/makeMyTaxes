package io.github.colla69.makemytaxes.core;

public record TaxYear(int value) {

  public TaxYear {
    if (value < 1000 || value > 9999) {
      throw new IllegalArgumentException("A tax year needs four digits, but got: " + value);
    }
  }
}
