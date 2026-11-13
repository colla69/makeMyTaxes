package io.github.colla69.makemytaxes.app.assistant;

import java.util.ArrayList;
import java.util.List;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The check of a return for the best legal result: the question to the assistant, and the tips in
 * its answer. A tip without a source does not appear.
 */
public final class Advice {

  /** The text of the check in the conversation. */
  static final String LABEL = "Rat für das beste legale Ergebnis";

  /** The question of the check. The assistant answers with JSON only. */
  static final String QUESTION =
      """
      Prüfe die offene Erklärung wie ein erfahrener Steuerberater auf das beste legale Ergebnis \
      innerhalb der Fakten. Nenne:
      - Abzüge, die die Erklärung noch nicht geltend macht und die die Fakten der Erklärung \
      erlauben können, mit dem Beleg, den jeder braucht (Art "abzug"),
      - Wahlrechte, zum Beispiel eine Pauschale gegen die tatsächlichen Kosten, mit dem Ergebnis \
      jeder Möglichkeit (Art "wahl"),
      - Tipps für das nächste Jahr (Art "naechstes_jahr").
      Wenn du auch die verknüpfte Erklärung siehst, prüfe die gemeinsamen Ausgaben: in welche \
      Erklärung jede Ausgabe gehört und wie eine Aufteilung zwischen den beiden Erklärungen das \
      Ergebnis ändert (Art "aufteilung"). Die Aufteilung folgt den Fakten, zum Beispiel wer \
      gezahlt hat. Wenn eine Vorschrift den beiden Erwachsenen eine Wahl der Aufteilung lässt, \
      nenne diese Vorschrift und das Ergebnis jeder Möglichkeit. Für die kommenden Jahre darfst du \
      raten, wer was zahlen sollte.
      Schlage nie vor, einen Betrag, ein Datum, eine Zahl von Tagen oder eine andere Tatsache zu \
      ändern, um ein besseres Ergebnis zu erhalten. Dein Rat arbeitet innerhalb der Fakten.
      Jeder Tipp braucht eine Quelle aus der Bibliothek: die Vorschrift mit dem Datum ihrer \
      Fassung, die Richtlinie oder den Hinweis mit dem Jahr des Handbuchs, das BMF-Schreiben oder \
      die Anleitung zum Vordruck. Lass einen Tipp ohne Quelle weg.
      Antworte nur mit JSON in dieser Form, ohne weiteren Text:
      {"tipps": [{"art": "abzug", "titel": "…", "text": "…", "beleg": "…", "quelle": "…"}]}
      Das Feld "beleg" bleibt leer, wenn der Tipp keinen Beleg braucht.
      """;

  /** One tip with its source. {@code document} is empty if the tip needs no document. */
  public record Tip(String kind, String title, String text, String document, String source) {

    /** The kind of the tip in German. */
    public String kindLabel() {
      return switch (kind) {
        case "abzug" -> "Abzug";
        case "wahl" -> "Wahl";
        case "naechstes_jahr" -> "Nächstes Jahr";
        case "aufteilung" -> "Aufteilung";
        default -> "Hinweis";
      };
    }
  }

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private Advice() {}

  /**
   * The tips in the answer of the assistant, without the tips that have no source. The JSON may
   * stand inside other text, such as a code block.
   */
  static List<Tip> tips(String answer) throws AssistantException {
    int start = answer.indexOf('{');
    int end = answer.lastIndexOf('}');
    if (start < 0 || end < start) {
      throw new AssistantException("The answer holds no advice.");
    }
    JsonNode root;
    try {
      root = JSON.readTree(answer.substring(start, end + 1));
    } catch (JacksonException e) {
      throw new AssistantException("The answer holds no readable advice.", e);
    }
    List<Tip> tips = new ArrayList<>();
    for (JsonNode node : root.path("tipps")) {
      Tip tip =
          new Tip(
              text(node, "art"),
              text(node, "titel"),
              text(node, "text"),
              text(node, "beleg"),
              text(node, "quelle"));
      if (!tip.source().isBlank() && !(tip.title().isBlank() && tip.text().isBlank())) {
        tips.add(tip);
      }
    }
    return tips;
  }

  /** The tips as text for the conversation, so that a later question sees them. */
  static String text(List<Tip> tips) {
    if (tips.isEmpty()) {
      return "Der Assistent hat keinen Rat mit Quelle gefunden.";
    }
    StringBuilder text = new StringBuilder();
    for (Tip tip : tips) {
      text.append(tip.kindLabel()).append(": ").append(tip.title()).append("\n");
      if (!tip.text().isBlank()) {
        text.append(tip.text()).append("\n");
      }
      if (!tip.document().isBlank()) {
        text.append("Beleg: ").append(tip.document()).append("\n");
      }
      text.append("Quelle: ").append(tip.source()).append("\n\n");
    }
    return text.toString().strip();
  }

  private static String text(JsonNode node, String name) {
    JsonNode value = node.path(name);
    return value.isString() ? value.stringValue().strip() : "";
  }
}
