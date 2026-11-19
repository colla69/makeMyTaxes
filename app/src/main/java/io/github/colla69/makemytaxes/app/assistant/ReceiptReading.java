package io.github.colla69.makemytaxes.app.assistant;

import io.github.colla69.makemytaxes.core.expense.ExpenseTopic;
import io.github.colla69.makemytaxes.core.expense.ReceiptFields;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** The question that reads a receipt, and the checked fields of the answer. */
final class ReceiptReading {

  /** {@code %s} are the path of the file and the names of the topics. */
  static final String QUESTION =
      """
      Lies den Beleg in der Datei %s mit Read. Es ist eine Rechnung oder Quittung für eine \
      Ausgabe, die in eine deutsche Einkommensteuererklärung gehört.
      Antworte nur mit einem JSON-Objekt, ohne weiteren Text, mit diesen Schlüsseln:
      "betrag": der bezahlte Gesamtbetrag brutto in Euro, im Format 1.234,56.
      "bezahltAm": das Zahlungsdatum, wenn der Beleg eines nennt, sonst das Rechnungsdatum, im \
      Format TT.MM.JJJJ mit vierstelligem Jahr. Schreibe das Datum auch dann so, wenn der Beleg \
      es anders schreibt, zum Beispiel 03.02.22 oder 3. Februar 2022 als 03.02.2022. Das ist \
      dasselbe Datum und keine Umrechnung.
      "beschreibung": der Aussteller und was gekauft wurde, kurz, zum Beispiel \
      "Optiker Muster – Brille".
      "thema": eines von %s, oder "".
      "arbeitskosten": nur beim Thema HOUSEHOLD_SERVICES und nur wenn die Rechnung die \
      Arbeitskosten getrennt ausweist: dieser Betrag, wie die Rechnung ihn nennt, im Format \
      1.234,56, sonst "".
      "unsicher": eine Liste der Schlüssel, bei denen du dir nicht sicher bist.
      Schreibe "" für einen Wert, den der Beleg nicht nennt. Rate nie und rechne keinen Wert um.
      """;

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private ReceiptReading() {}

  static String question(String path) {
    String topics =
        Arrays.stream(ExpenseTopic.values()).map(Enum::name).collect(Collectors.joining(", "));
    return QUESTION.formatted(path, topics);
  }

  /** The checked fields of the answer. A field that the assistant was unsure about stays empty. */
  static ReceiptFields fields(String answer) throws AssistantException {
    int start = answer.indexOf('{');
    int end = answer.lastIndexOf('}');
    if (start < 0 || end < start) {
      throw new AssistantException("The answer holds no fields of the receipt.");
    }
    JsonNode root;
    try {
      // Claude Code can wrap the object in a code block, so the object starts at the first brace.
      root = JSON.readTree(answer.substring(start, end + 1));
    } catch (JacksonException e) {
      throw new AssistantException("The answer holds no readable fields of the receipt.", e);
    }
    Set<String> unsure = new HashSet<>();
    root.path("unsicher").forEach(name -> unsure.add(name.isString() ? name.stringValue() : ""));
    return ReceiptFields.checked(
        text(root, "betrag", unsure),
        text(root, "bezahltAm", unsure),
        text(root, "beschreibung", unsure),
        text(root, "thema", unsure),
        text(root, "arbeitskosten", unsure));
  }

  private static String text(JsonNode root, String name, Set<String> unsure) {
    JsonNode value = root.path(name);
    return unsure.contains(name) || !value.isString() ? "" : value.stringValue();
  }
}
