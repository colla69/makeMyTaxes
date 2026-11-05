package io.github.colla69.makemytaxes.core.form;

import io.github.colla69.makemytaxes.core.GermanFormats;
import io.github.colla69.makemytaxes.core.expense.Money;
import java.util.Map;

/** The answers to one form, as text. A blank answer is not in the map. */
public record Answers(Map<String, String> values) {

  public String text(String fieldId) {
    return values.getOrDefault(fieldId, "");
  }

  public boolean has(String fieldId) {
    return values.containsKey(fieldId);
  }

  public long wholeNumber(String fieldId) {
    return has(fieldId) ? Long.parseLong(text(fieldId)) : 0;
  }

  public Money money(String fieldId) {
    return has(fieldId) ? GermanFormats.parseMoney(text(fieldId)) : Money.ofCents(0);
  }
}
