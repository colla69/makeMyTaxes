package io.github.colla69.makemytaxes.app.assistant;

import io.github.colla69.makemytaxes.app.storage.DocumentStore;
import io.github.colla69.makemytaxes.app.storage.DocumentStore.StoredDocument;
import io.github.colla69.makemytaxes.app.storage.FormStore;
import io.github.colla69.makemytaxes.app.storage.HouseholdStore;
import io.github.colla69.makemytaxes.app.storage.HouseholdStore.StoredPerson;
import io.github.colla69.makemytaxes.app.storage.HouseholdStore.StoredReturn;
import io.github.colla69.makemytaxes.core.GermanFormats;
import io.github.colla69.makemytaxes.core.TaxYear;
import io.github.colla69.makemytaxes.core.form.Answers;
import io.github.colla69.makemytaxes.core.form.Field;
import io.github.colla69.makemytaxes.core.form.FormModule;
import io.github.colla69.makemytaxes.core.form.Forms;
import io.github.colla69.makemytaxes.core.form.Interview;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * What the assistant knows about a return: the person, the year, the answers, the form values and
 * the documents. The linked return of the other adult of the same year joins only while both
 * adults have the assistant on.
 */
@Component
class ReturnContext {

  /** A return that the assistant sees. */
  record SeenReturn(long returnId, String person, TaxYear taxYear) {

    String label() {
      return "Steuererklärung " + taxYear.value() + " von " + person;
    }
  }

  private final HouseholdStore household;
  private final FormStore forms;
  private final DocumentStore documents;

  ReturnContext(HouseholdStore household, FormStore forms, DocumentStore documents) {
    this.household = household;
    this.forms = forms;
    this.documents = documents;
  }

  /**
   * The open return first, then the linked returns of the year. The list is empty while the person
   * of the open return has the assistant off.
   */
  List<SeenReturn> seenReturns(long returnId) {
    List<SeenReturn> seen = new ArrayList<>();
    List<StoredPerson> persons = household.persons();
    for (StoredPerson person : persons) {
      for (StoredReturn stored : person.returns()) {
        if (stored.id() == returnId && household.assistantOn(person.id())) {
          seen.add(new SeenReturn(stored.id(), person.name(), stored.taxYear()));
        }
      }
    }
    if (seen.isEmpty()) {
      return seen;
    }
    TaxYear taxYear = seen.getFirst().taxYear();
    for (StoredPerson person : persons) {
      if (!household.assistantOn(person.id())) {
        continue;
      }
      for (StoredReturn stored : person.returns()) {
        if (stored.id() != returnId && stored.taxYear().equals(taxYear)) {
          seen.add(new SeenReturn(stored.id(), person.name(), stored.taxYear()));
        }
      }
    }
    return seen;
  }

  /** The text of the context, with each return that the assistant sees. */
  String text(List<SeenReturn> seen) {
    StringBuilder text = new StringBuilder();
    for (int i = 0; i < seen.size(); i++) {
      SeenReturn stored = seen.get(i);
      text.append("## ")
          .append(stored.label())
          .append(i == 0 ? " (die offene Erklärung)" : " (die verknüpfte Erklärung)")
          .append("\n");
      appendReturn(stored, text);
      text.append("\n");
    }
    return text.toString();
  }

  private void appendReturn(SeenReturn stored, StringBuilder text) {
    text.append(
        answersText("Antworten im Fragebogen", forms.answers(stored.returnId(), Interview.ID), null));
    for (FormModule form : Forms.of(stored.taxYear())) {
      text.append(answersText(form.title(), forms.answers(stored.returnId(), form.id()), form));
    }
    text.append("### Belege\n");
    List<StoredDocument> storedDocuments = documents.documentsOf(stored.returnId());
    if (storedDocuments.isEmpty()) {
      text.append("Keine Belege.\n");
    }
    for (StoredDocument document : storedDocuments) {
      text.append("- ")
          .append(document.fileName())
          .append(" (")
          .append(document.kind().label())
          .append(", hochgeladen am ")
          .append(GermanFormats.format(document.uploadedOn()))
          .append(")\n");
    }
  }

  /** The answers under a title. A field of the form shows with its label. */
  private static String answersText(String title, Answers answers, FormModule form) {
    StringBuilder text = new StringBuilder();
    text.append("### ").append(title).append("\n");
    if (answers.values().isEmpty()) {
      text.append("Keine Angaben.\n");
    }
    for (Map.Entry<String, String> answer : answers.values().entrySet()) {
      String fieldId = answer.getKey();
      String label =
          form == null ? fieldId : form.field(fieldId).map(Field::label).orElse(fieldId);
      text.append("- ").append(label);
      if (!label.equals(fieldId)) {
        text.append(" [").append(fieldId).append("]");
      }
      text.append(": ").append(answer.getValue()).append("\n");
    }
    return text.toString();
  }
}
