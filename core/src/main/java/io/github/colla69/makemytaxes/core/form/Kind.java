package io.github.colla69.makemytaxes.core.form;

import io.github.colla69.makemytaxes.core.GermanFormats;
import java.util.List;

/** The kind of value that a field holds. Values are text, as the user typed them. */
public enum Kind {
  TEXT,
  MONEY,
  WHOLE_NUMBER,
  DATE,
  TAX_ID,
  IBAN,
  YES_NO_UNSURE,
  COMMUTE_TRANSPORT;

  public record Option(String value, String label) {}

  public static final String YES = "YES";
  public static final String NO = "NO";
  public static final String UNSURE = "UNSURE";
  public static final String CAR = "CAR";
  public static final String OTHER_TRANSPORT = "OTHER";

  public List<Option> options() {
    return switch (this) {
      case YES_NO_UNSURE ->
          List.of(new Option(YES, "Yes"), new Option(NO, "No"), new Option(UNSURE, "Not sure"));
      case COMMUTE_TRANSPORT ->
          List.of(
              new Option(CAR, "Own car or a car for my use"),
              new Option(OTHER_TRANSPORT, "Public transport, bicycle, on foot or car sharing"));
      default -> List.of();
    };
  }

  public boolean isChoice() {
    return !options().isEmpty();
  }

  /** Throws an exception with a message for the user if the value is not valid. */
  public void validate(String value) {
    switch (this) {
      case TEXT -> {}
      case MONEY -> GermanFormats.parseMoney(value);
      case DATE -> GermanFormats.parseDate(value);
      case WHOLE_NUMBER -> {
        if (!value.matches("\\d{1,6}")) {
          throw new IllegalArgumentException("Write a whole number, but got: \"" + value + "\".");
        }
      }
      case TAX_ID -> {
        if (!Identifiers.isTaxId(value)) {
          throw new IllegalArgumentException(
              "A tax ID has 11 digits and a valid check digit. Check this number on your letter.");
        }
      }
      case IBAN -> {
        if (!Identifiers.isIban(value)) {
          throw new IllegalArgumentException(
              "This IBAN is not valid. Check the letters, the digits and the check digits.");
        }
      }
      case YES_NO_UNSURE, COMMUTE_TRANSPORT -> {
        if (options().stream().noneMatch(option -> option.value().equals(value))) {
          throw new IllegalArgumentException("Choose one of the options.");
        }
      }
    }
  }

  /** Shows a valid value in the one format of the app, whatever form the user typed. */
  public String display(String value) {
    if (this == MONEY) {
      return GermanFormats.format(GermanFormats.parseMoney(value));
    }
    if (this == DATE) {
      return GermanFormats.format(GermanFormats.parseDate(value));
    }
    return options().stream()
        .filter(option -> option.value().equals(value))
        .map(Option::label)
        .findFirst()
        .orElse(value);
  }
}
