package io.github.colla69.makemytaxes.core.expense;

public record Purpose(ExpenseTopic topic, String description) {

  public Purpose {
    if (description == null || description.isBlank()) {
      throw new IllegalArgumentException("Eine Ausgabe braucht eine Beschreibung.");
    }
  }
}
