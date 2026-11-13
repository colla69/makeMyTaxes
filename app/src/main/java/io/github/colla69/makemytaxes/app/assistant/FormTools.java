package io.github.colla69.makemytaxes.app.assistant;

import io.github.colla69.makemytaxes.app.storage.FormStore;
import io.github.colla69.makemytaxes.app.storage.HouseholdStore;
import io.github.colla69.makemytaxes.app.storage.HouseholdStore.StoredPerson;
import io.github.colla69.makemytaxes.app.storage.HouseholdStore.StoredReturn;
import io.github.colla69.makemytaxes.core.form.Answers;
import io.github.colla69.makemytaxes.core.form.Field;
import io.github.colla69.makemytaxes.core.form.FormModule;
import io.github.colla69.makemytaxes.core.form.Forms;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * The MCP tool that reads the forms of the year of the question: each form with its fields, their
 * lines and the values of each return that the question sees.
 */
@Component
class FormTools {

  static final String READ_FORMS = "read_forms";

  static final Map<String, Object> DEFINITION =
      Map.of(
          "name",
          READ_FORMS,
          "description",
          "Liest die Vordrucke des Jahres der Erklärung, je Erklärung dieser Frage: jeden Vordruck"
              + " mit seinen Feldern, der Zeile, dem Abschnitt, ob das Feld Pflicht ist, und dem"
              + " eingetragenen Wert.",
          "inputSchema",
          Map.of("type", "object", "properties", Map.of()));

  private final HouseholdStore household;
  private final FormStore forms;

  FormTools(HouseholdStore household, FormStore forms) {
    this.household = household;
    this.forms = forms;
  }

  Map<String, Object> call(DocumentAccess access) {
    if (access.taxYear() == null) {
      return McpController.toolText("Diese Frage sieht keine Erklärung, also kein Jahr.", true);
    }
    List<FormModule> modules = Forms.of(access.taxYear());
    if (modules.isEmpty()) {
      return McpController.toolText(
          "Für das Jahr " + access.taxYear().value() + " hat die App noch keine Vordrucke.", true);
    }
    StringBuilder text = new StringBuilder();
    for (StoredPerson person : household.persons()) {
      for (StoredReturn stored : person.returns()) {
        if (access.returnIds().contains(stored.id())) {
          appendReturn(text, person.name(), stored, modules);
        }
      }
    }
    return McpController.toolText(text.toString(), false);
  }

  private void appendReturn(
      StringBuilder text, String person, StoredReturn stored, List<FormModule> modules) {
    text.append("# Steuererklärung ")
        .append(stored.taxYear().value())
        .append(" von ")
        .append(person)
        .append("\n");
    for (FormModule form : modules) {
      Answers answers = forms.answers(stored.id(), form.id());
      text.append("## ").append(form.title()).append(" [").append(form.id()).append("]\n");
      String section = null;
      for (Field field : form.fields(answers)) {
        if (!field.section().equals(section)) {
          section = field.section();
          text.append("### ").append(section).append("\n");
        }
        appendField(text, field, answers);
      }
      for (String message : form.check(answers)) {
        text.append("- Prüfung: ").append(message).append("\n");
      }
    }
    text.append("\n");
  }

  private static void appendField(StringBuilder text, Field field, Answers answers) {
    text.append("- ")
        .append(line(field.line()))
        .append(": ")
        .append(field.label())
        .append(" [")
        .append(field.id())
        .append(field.required() ? ", Pflicht" : "")
        .append("]: ")
        .append(answers.has(field.id()) ? value(field, answers.text(field.id())) : "leer")
        .append("\n");
  }

  /** A line on the form, or the legal source of a question that is on no form. */
  private static String line(String line) {
    if (line.isBlank()) {
      return "ohne Zeile";
    }
    return line.startsWith("§") ? "Quelle " + line : "Zeile " + line;
  }

  private static String value(Field field, String value) {
    try {
      return field.kind().display(value);
    } catch (RuntimeException e) {
      return value;
    }
  }
}
