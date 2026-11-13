package io.github.colla69.makemytaxes.app.web;

import io.github.colla69.makemytaxes.app.document.CertificatePdfReader;
import io.github.colla69.makemytaxes.app.storage.DocumentStore;
import io.github.colla69.makemytaxes.app.storage.DocumentStore.AnswerUse;
import io.github.colla69.makemytaxes.app.storage.DocumentStore.Replacement;
import io.github.colla69.makemytaxes.app.storage.DocumentStore.StoredDocument;
import io.github.colla69.makemytaxes.app.storage.FormStore;
import io.github.colla69.makemytaxes.app.storage.HouseholdStore;
import io.github.colla69.makemytaxes.core.document.DocumentKind;
import io.github.colla69.makemytaxes.core.form.AnlageN;
import io.github.colla69.makemytaxes.core.form.AnlageVorsorgeaufwand;
import io.github.colla69.makemytaxes.core.form.CertificateValues;
import io.github.colla69.makemytaxes.core.form.Field;
import io.github.colla69.makemytaxes.core.form.FormModule;
import io.github.colla69.makemytaxes.core.form.WageTaxCertificate;
import io.github.colla69.makemytaxes.core.form.YearDefinition;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * The values that a new version of a document changes. The app reads each value of the old
 * document again from the new version, so the user can compare both before anything is saved.
 * Today the app reads only the Lohnsteuerbescheinigung.
 */
@Component
class DocumentReplacement {

  /** The forms that hold values of the Lohnsteuerbescheinigung. */
  private static final List<String> CERTIFICATE_FORMS =
      List.of(AnlageN.ID, AnlageVorsorgeaufwand.ID);

  /**
   * One value that the new version changes: the shown value now, and the shown new value.
   *
   * @param missing true if the new version has no value, so the field becomes empty without
   *     evidence
   */
  record Row(String fieldId, String label, String current, String replacement, boolean missing) {}

  /** A value of the old document, read again from the new version. */
  private record ReadAgain(AnswerUse use, Field field, String current, String value) {}

  private final HouseholdStore household;
  private final DocumentStore documents;
  private final FormStore forms;

  DocumentReplacement(HouseholdStore household, DocumentStore documents, FormStore forms) {
    this.household = household;
    this.documents = documents;
    this.forms = forms;
  }

  /** The rows of the check, in the order of the forms. */
  List<Row> rows(long returnId, StoredDocument old, StoredDocument replacement) {
    List<Row> rows = new ArrayList<>();
    for (ReadAgain value : readAgain(returnId, old, replacement)) {
      rows.add(
          new Row(
              value.use().fieldId(),
              value.field().label(),
              value.field().kind().display(value.current()),
              value.value().isEmpty() ? "" : value.field().kind().display(value.value()),
              value.value().isEmpty()));
    }
    return rows;
  }

  /** The replacement with the values that the app reads from the new version now. */
  Replacement replacement(long returnId, StoredDocument old, StoredDocument replacement) {
    Map<AnswerUse, String> values = new LinkedHashMap<>();
    for (ReadAgain value : readAgain(returnId, old, replacement)) {
      values.put(value.use(), value.value());
    }
    return new Replacement(old.id(), replacement.id(), values);
  }

  private List<ReadAgain> readAgain(
      long returnId, StoredDocument old, StoredDocument replacement) {
    Optional<YearDefinition> definition = household.taxYearOf(returnId).flatMap(YearDefinition::of);
    if (old.kind() != DocumentKind.WAGE_TAX_CERTIFICATE || definition.isEmpty()) {
      return List.of();
    }
    WageTaxCertificate certificate = definition.get().certificate();
    CertificateValues read =
        CertificatePdfReader.read(documents.content(replacement.id()), certificate);
    List<ReadAgain> values = new ArrayList<>();
    for (AnswerUse use : documents.usesOf(returnId, old.id()).answers()) {
      fieldOf(definition.get(), use)
          .filter(field -> certificate.fieldIds().contains(field.hintId()))
          .ifPresent(
              field ->
                  values.add(
                      new ReadAgain(
                          use,
                          field,
                          forms.answers(returnId, use.formId()).text(use.fieldId()),
                          validOrEmpty(field, read.value(field.hintId()).orElse("")))));
    }
    return values;
  }

  private static Optional<Field> fieldOf(YearDefinition definition, AnswerUse use) {
    if (!CERTIFICATE_FORMS.contains(use.formId())) {
      return Optional.empty();
    }
    return definition.forms().stream()
        .filter(form -> form.id().equals(use.formId()))
        .findFirst()
        .flatMap((FormModule form) -> form.field(use.fieldId()));
  }

  /** A read value that does not fit its field counts as not found, as the app never guesses. */
  private static String validOrEmpty(Field field, String value) {
    if (value.isEmpty()) {
      return value;
    }
    try {
      field.kind().validate(value);
      return value;
    } catch (IllegalArgumentException e) {
      return "";
    }
  }
}
