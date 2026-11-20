package io.github.colla69.makemytaxes.app.assistant;

import io.github.colla69.makemytaxes.core.expense.ExpenseTopic;
import io.github.colla69.makemytaxes.core.expense.ReceiptFields;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
      dasselbe Datum und keine Umrechnung. Ein anderes Datum, etwa ein Bestelldatum, gehört \
      nicht hierher.
      "ohneZahlungsdatum": true, wenn der Beleg weder ein Zahlungsdatum noch ein Rechnungsdatum \
      nennt, sonst false.
      "andereDaten": eine Liste der anderen Daten auf dem Beleg, etwa Bestelldatum oder \
      Lieferdatum, je als {"name": "Bestelldatum", "datum": "TT.MM.JJJJ"}, sonst [].
      "beschreibung": der Aussteller und was gekauft wurde, kurz, zum Beispiel \
      "Optiker Muster – Brille".
      "thema": eines von %s, oder "".
      "arbeitskosten": nur beim Thema HOUSEHOLD_SERVICES und nur wenn die Rechnung die \
      Arbeitskosten getrennt ausweist: dieser Betrag, wie die Rechnung ihn nennt, im Format \
      1.234,56, sonst "".
      "unsicher": eine Liste der Schlüssel, deren Wert du gelesen hast, bei denen du dir aber \
      nicht sicher bist.
      Schreibe "" für einen Wert, den der Beleg nicht nennt. Rate nie und rechne keinen Wert um.
      """;

  /** The keys of the answer with the names of the fields that they fill. */
  private static final Map<String, String> FIELDS =
      Map.of(
          "betrag", "amount",
          "bezahltAm", "paidOn",
          "beschreibung", "description",
          "thema", "topic",
          "arbeitskosten", "labourCost");

  private static final Logger LOG = LoggerFactory.getLogger(ReceiptReading.class);

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private ReceiptReading() {}

  static String question(String path) {
    String topics =
        Arrays.stream(ExpenseTopic.values()).map(Enum::name).collect(Collectors.joining(", "));
    return QUESTION.formatted(path, topics);
  }

  /**
   * The checked fields of the answer. A field that the assistant was unsure about keeps its value
   * and is marked as unsure, so the user decides.
   */
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
    // Only known keys count, so a value that the assistant put in the list never reaches the log.
    Set<String> unsureKeys = new TreeSet<>();
    root.path("unsicher")
        .forEach(
            name -> {
              if (name.isString() && FIELDS.containsKey(name.stringValue())) {
                unsureKeys.add(name.stringValue());
              }
            });
    logKeys(root, unsureKeys);
    return ReceiptFields.checked(
            text(root, "betrag"),
            text(root, "bezahltAm"),
            text(root, "beschreibung"),
            text(root, "thema"),
            text(root, "arbeitskosten"))
        .withUnsure(unsureKeys.stream().map(FIELDS::get).collect(Collectors.toSet()))
        .withDates(root.path("ohneZahlungsdatum").asBoolean(false), otherDates(root));
  }

  /** Only the names of the keys, never their values, so the log holds no data of the receipt. */
  private static void logKeys(JsonNode root, Set<String> unsureKeys) {
    List<String> empty =
        FIELDS.keySet().stream().filter(key -> text(root, key).isBlank()).sorted().toList();
    LOG.atDebug()
        .addKeyValue("emptyKeys", empty)
        .addKeyValue("unsureKeys", List.copyOf(unsureKeys))
        .log("Read a receipt");
  }

  private static List<ReceiptFields.NamedDate> otherDates(JsonNode root) {
    List<ReceiptFields.NamedDate> dates = new ArrayList<>();
    root.path("andereDaten")
        .forEach(date -> dates.add(new ReceiptFields.NamedDate(text(date, "name"), text(date, "datum"))));
    return dates;
  }

  private static String text(JsonNode node, String name) {
    JsonNode value = node.path(name);
    return value.isString() ? value.stringValue() : "";
  }
}
