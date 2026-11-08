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
  COMMUTE_TRANSPORT,
  PAYMENT;

  public record Option(String value, String label) {}

  public static final String YES = "YES";
  public static final String NO = "NO";
  public static final String UNSURE = "UNSURE";
  public static final String CAR = "CAR";
  public static final String OTHER_TRANSPORT = "OTHER";
  public static final String TRANSFER = "TRANSFER";
  public static final String CASH = "CASH";

  public List<Option> options() {
    return switch (this) {
      case YES_NO_UNSURE ->
          List.of(new Option(YES, "Ja"), new Option(NO, "Nein"), new Option(UNSURE, "Weiß nicht"));
      case COMMUTE_TRANSPORT ->
          List.of(
              new Option(CAR, "Eigener oder zur Nutzung überlassener Kraftwagen"),
              new Option(OTHER_TRANSPORT, "Öffentliche Verkehrsmittel, Fahrrad, zu Fuß oder Carsharing"));
      case PAYMENT ->
          List.of(new Option(TRANSFER, "Überweisung auf ein Konto"), new Option(CASH, "Bar"));
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
          throw new IllegalArgumentException("Geben Sie eine ganze Zahl ein. Eingabe: \"" + value + "\".");
        }
      }
      case TAX_ID -> {
        if (!Identifiers.isTaxId(value)) {
          throw new IllegalArgumentException(
              "Eine Identifikationsnummer hat 11 Ziffern und eine gültige Prüfziffer. Prüfen Sie die"
                  + " Nummer auf Ihrem Schreiben.");
        }
      }
      case IBAN -> {
        if (!Identifiers.isIban(value)) {
          throw new IllegalArgumentException(
              "Diese IBAN ist ungültig. Prüfen Sie die Buchstaben, die Ziffern und die Prüfziffern.");
        }
      }
      case YES_NO_UNSURE, COMMUTE_TRANSPORT, PAYMENT -> {
        if (options().stream().noneMatch(option -> option.value().equals(value))) {
          throw new IllegalArgumentException("Wählen Sie eine der Möglichkeiten.");
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
