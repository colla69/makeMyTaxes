package io.github.colla69.makemytaxes.app.assistant;

import io.github.colla69.makemytaxes.app.storage.DocumentStore;
import io.github.colla69.makemytaxes.app.storage.ProposalStore;
import io.github.colla69.makemytaxes.app.storage.ProposalStore.Proposal;
import io.github.colla69.makemytaxes.core.form.Field;
import io.github.colla69.makemytaxes.core.form.Forms;
import io.github.colla69.makemytaxes.core.form.Kind;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * The values that the assistant proposes for the fields of the open return. The MCP tool only
 * stores a proposal. A value enters the return only when the user confirms it in the panel, in
 * {@code ProposalReview} of the web package.
 */
@Component
class FieldProposals {

  static final String PROPOSE_VALUES = "propose_values";

  static final Map<String, Object> DEFINITION =
      Map.of(
          "name",
          PROPOSE_VALUES,
          "description",
          "Schlägt dem Nutzer Werte für Felder der offenen Erklärung vor, je mit seiner Quelle. Der"
              + " Nutzer bestätigt oder verwirft jeden Wert. Erst ein bestätigter Wert steht in der"
              + " Erklärung. Nimm die Vordruck-ID und die Feld-ID aus read_forms. Schreib den Wert,"
              + " wie ein Nutzer ihn tippt: Beträge als 1.234,56, Daten als 03.02.2022. Nenne als"
              + " Quelle den Beleg und die Zeile darauf, etwa \"Lohnsteuerbescheinigung, Zeile"
              + " 3\", und die Beleg-Nr., wenn der Wert aus einem Beleg kommt. Alle Werte eines"
              + " Belegs kannst du mit einem Aufruf vorschlagen.",
          "inputSchema",
          Map.of(
              "type",
              "object",
              "properties",
              Map.of(
                  "values",
                  Map.of(
                      "type",
                      "array",
                      "items",
                      Map.of(
                          "type",
                          "object",
                          "properties",
                          Map.of(
                              "form_id", Map.of("type", "string"),
                              "field_id", Map.of("type", "string"),
                              "value", Map.of("type", "string"),
                              "source", Map.of("type", "string"),
                              "document_id",
                                  Map.of(
                                      "type",
                                      "integer",
                                      "description",
                                      "Die Beleg-Nr. aus dem Kontext, wenn der Wert aus einem"
                                          + " Beleg kommt")),
                          "required",
                          List.of("form_id", "field_id", "value", "source")))),
              "required",
              List.of("values")));

  private final DocumentStore documents;
  private final ProposalStore proposals;

  FieldProposals(DocumentStore documents, ProposalStore proposals) {
    this.documents = documents;
    this.proposals = proposals;
  }

  /** The MCP tool: stores each valid proposal for the open return and names each invalid one. */
  Map<String, Object> call(DocumentAccess access, Map<String, Object> arguments) {
    long returnId = access.openReturnId();
    if (access.taxYear() == null || !access.returnIds().contains(returnId)) {
      return McpController.toolText("Diese Frage sieht keine Erklärung.", true);
    }
    if (!(arguments.get("values") instanceof List<?> values) || values.isEmpty()) {
      return McpController.toolText("Gib die Werte als Liste unter values an.", true);
    }
    List<Proposal> valid = new ArrayList<>();
    List<String> errors = new ArrayList<>();
    for (Object value : values) {
      Checked checked =
          value instanceof Map<?, ?> entry
              ? read(access, entry)
              : new Checked(null, "Ein Wert ist kein Objekt.");
      if (checked.proposal() != null) {
        valid.add(checked.proposal());
      } else {
        errors.add(checked.error());
      }
    }
    proposals.add(returnId, valid);
    StringBuilder text =
        new StringBuilder(valid.size() + " Werte warten auf die Bestätigung des Nutzers.");
    for (String error : errors) {
      text.append("\n- Nicht vorgeschlagen: ").append(error);
    }
    return McpController.toolText(text.toString(), valid.isEmpty());
  }

  /** A valid proposal, or the reason why the entry is not valid. */
  private record Checked(Proposal proposal, String error) {

    static Checked invalid(String error) {
      return new Checked(null, error);
    }
  }

  private Checked read(DocumentAccess access, Map<?, ?> entry) {
    String formId = text(entry.get("form_id"));
    String fieldId = text(entry.get("field_id"));
    String value = text(entry.get("value")).strip();
    String source = text(entry.get("source")).strip();
    String name = formId + "/" + fieldId;
    Optional<Field> field = Forms.find(access.taxYear(), formId).flatMap(form -> form.field(fieldId));
    if (field.isEmpty()) {
      return Checked.invalid(name + ": Dieses Feld gibt es nicht. Lies die IDs mit read_forms.");
    }
    if (value.isEmpty() || source.isEmpty()) {
      return Checked.invalid(name + ": Gib einen Wert und seine Quelle an.");
    }
    String stored = optionValue(field.get().kind(), value);
    try {
      field.get().kind().validate(stored);
    } catch (IllegalArgumentException e) {
      return Checked.invalid(name + ": " + e.getMessage());
    }
    Long documentId = null;
    if (entry.get("document_id") instanceof Number number) {
      documentId = number.longValue();
      if (documents.find(access.openReturnId(), documentId).isEmpty()) {
        return Checked.invalid(name + ": Diesen Beleg gibt es in der offenen Erklärung nicht.");
      }
    }
    return new Checked(new Proposal(formId, fieldId, stored, source, documentId), null);
  }

  /** A choice can come as its stored value or as the label that read_forms shows. */
  private static String optionValue(Kind kind, String value) {
    return kind.options().stream()
        .filter(option -> option.label().equals(value))
        .map(Kind.Option::value)
        .findFirst()
        .orElse(value);
  }

  private static String text(Object value) {
    return value instanceof String text ? text : "";
  }
}
