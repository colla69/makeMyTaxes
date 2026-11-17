package io.github.colla69.makemytaxes.app.web;

import io.github.colla69.makemytaxes.app.document.CertificatePdfReader;
import io.github.colla69.makemytaxes.app.storage.DocumentStore;
import io.github.colla69.makemytaxes.app.storage.DocumentStore.StoredDocument;
import io.github.colla69.makemytaxes.app.storage.FormStore;
import io.github.colla69.makemytaxes.core.document.DocumentKind;
import io.github.colla69.makemytaxes.core.form.AnlageN;
import io.github.colla69.makemytaxes.core.form.AnlageVorsorgeaufwand;
import io.github.colla69.makemytaxes.core.form.Answers;
import io.github.colla69.makemytaxes.core.form.CertificateValues;
import io.github.colla69.makemytaxes.core.form.Field;
import io.github.colla69.makemytaxes.core.form.Sections;
import io.github.colla69.makemytaxes.core.form.WageTaxCertificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

/**
 * The uploaded certificates of the interview and the values that the app reads from them. The
 * interview shows the step "Dokumente hochladen" when it starts, and the step shows it again after
 * an upload fails, so both controllers use this component.
 */
@Component
class InterviewDocuments {

  /** A field of the certificate, and the form that holds it. */
  record CertificateField(String formId, Field field) {}

  /** The value read from the certificate, and the value of the input of its check row. */
  record CheckValues(String read, String input) {}

  /** One row of the check of an uploaded certificate. The input name holds the document. */
  record CheckRow(
      String name, String label, String certificateLine, String kind, CheckValues values) {}

  /** An uploaded certificate with the values that the app read from it. */
  record CertificateCheck(long id, String fileName, String href, List<CheckRow> rows) {}

  /** The certificate values come from Anlage N and from Anlage Vorsorgeaufwand. */
  static final List<CertificateField> CERTIFICATE_FIELDS =
      Stream.concat(
              Sections.EMPLOYER.fields().stream()
                  .map(field -> new CertificateField(AnlageN.ID, field)),
              Sections.SOCIAL_INSURANCE.fields().stream()
                  .map(field -> new CertificateField(AnlageVorsorgeaufwand.ID, field)))
          .toList();

  private final InterviewContext context;
  private final FormStore forms;
  private final DocumentStore documents;

  InterviewDocuments(InterviewContext context, FormStore forms, DocumentStore documents) {
    this.context = context;
    this.forms = forms;
    this.documents = documents;
  }

  /**
   * The step "Dokumente hochladen" before the first question: the uploaded certificates, each with
   * the values that the app read from it for a check.
   */
  String documentsScreen(long returnId, Model model) {
    context.addCommon(returnId, model);
    model.addAttribute("upload", true);
    List<StoredDocument> certificates = certificatesOf(returnId);
    List<CertificateCheck> checks = new ArrayList<>();
    for (int employer = 1; employer <= certificates.size(); employer++) {
      checks.add(checkOf(returnId, certificates.get(employer - 1), employer));
    }
    model.addAttribute("certificates", checks);
    return "interview";
  }

  /**
   * The rows of the check of the certificate of an employer. An input shows the value that the
   * form already holds, or else the read value. A value that the app did not read stays empty.
   */
  private CertificateCheck checkOf(long returnId, StoredDocument stored, int employer) {
    WageTaxCertificate certificate = context.definitionOf(returnId).certificate();
    CertificateValues read = CertificatePdfReader.read(documents.content(stored.id()), certificate);
    Map<String, Answers> saved =
        Map.of(
            AnlageN.ID, forms.answers(returnId, AnlageN.ID),
            AnlageVorsorgeaufwand.ID, forms.answers(returnId, AnlageVorsorgeaufwand.ID));
    List<CheckRow> rows =
        CERTIFICATE_FIELDS.stream()
            .filter(target -> certificate.fieldIds().contains(target.field().id()))
            .map(
                target -> {
                  String id = target.field().id();
                  String readValue = read.value(id).orElse("");
                  String onForm =
                      saved.get(target.formId()).text(AnlageN.employerFieldId(employer, id));
                  return new CheckRow(
                      inputName(stored.id(), id),
                      target.field().label(),
                      certificate.lineOf(id).orElse("ohne Zeile"),
                      target.field().kind().name(),
                      new CheckValues(readValue, onForm.isEmpty() ? readValue : onForm));
                })
            .toList();
    return new CertificateCheck(
        stored.id(), stored.fileName(), "/returns/" + returnId + "/documents/" + stored.id(), rows);
  }

  static String inputName(long document, String fieldId) {
    return "d" + document + "-" + fieldId;
  }

  List<StoredDocument> certificatesOf(long returnId) {
    return documents.documentsOf(returnId).stream()
        .filter(document -> document.kind() == DocumentKind.WAGE_TAX_CERTIFICATE)
        .toList();
  }

  /**
   * The value of the field that the app reads from the stored certificate, or empty if it finds
   * none. The page shows it beside the answer and marks an answer that differs. It is never saved.
   */
  String readValue(long returnId, long document, Field field) {
    return documents
        .find(returnId, document)
        .map(
            stored ->
                CertificatePdfReader.read(
                    documents.content(stored.id()), context.definitionOf(returnId).certificate()))
        .flatMap(read -> read.value(field.hintId()))
        .orElse("");
  }
}
