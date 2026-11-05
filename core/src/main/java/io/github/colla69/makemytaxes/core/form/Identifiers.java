package io.github.colla69.makemytaxes.core.form;

import java.math.BigInteger;
import java.util.Locale;

/** Checks the check digits of numbers on a return. A typing error fails here, not at ELSTER. */
final class Identifiers {

  private static final BigInteger NINETY_SEVEN = BigInteger.valueOf(97);

  private Identifiers() {}

  /** 11 digits with the check digit of ISO 7064 MOD 11,10. */
  static boolean isTaxId(String value) {
    String digits = value.replace(" ", "");
    if (!digits.matches("[1-9]\\d{10}")) {
      return false;
    }
    int product = 10;
    for (int i = 0; i < 10; i++) {
      int sum = (digits.charAt(i) - '0' + product) % 10;
      product = ((sum == 0 ? 10 : sum) * 2) % 11;
    }
    int check = 11 - product;
    return (check == 10 ? 0 : check) == digits.charAt(10) - '0';
  }

  /** The check of ISO 13616: the number with its country code moved to the end, modulo 97. */
  static boolean isIban(String value) {
    String iban = value.replace(" ", "").toUpperCase(Locale.ROOT);
    boolean wrongGermanLength = iban.startsWith("DE") && iban.length() != 22;
    if (!iban.matches("[A-Z]{2}\\d{2}[A-Z0-9]{11,30}") || wrongGermanLength) {
      return false;
    }
    StringBuilder digits = new StringBuilder();
    for (char c : (iban.substring(4) + iban.substring(0, 4)).toCharArray()) {
      digits.append(Character.getNumericValue(c));
    }
    return new BigInteger(digits.toString()).mod(NINETY_SEVEN).equals(BigInteger.ONE);
  }
}
