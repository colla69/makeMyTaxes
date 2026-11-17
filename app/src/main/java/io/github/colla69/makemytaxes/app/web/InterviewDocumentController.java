package io.github.colla69.makemytaxes.app.web;

import static io.github.colla69.makemytaxes.app.web.InterviewContext.interviewHref;

import io.github.colla69.makemytaxes.app.storage.DocumentStore;
import io.github.colla69.makemytaxes.app.storage.DocumentStore.StoredDocument;
import io.github.colla69.makemytaxes.app.storage.FormStore;
import io.github.colla69.makemytaxes.app.web.InterviewContext.FieldTarget;
import io.github.colla69.makemytaxes.app.web.InterviewDocuments.CertificateField;
import io.github.colla69.makemytaxes.core.document.DocumentKind;
import io.github.colla69.makemytaxes.core.form.AnlageN;
import io.github.colla69.makemytaxes.core.form.Answers;
import io.github.colla69.makemytaxes.core.form.CertificateEvidence;
import io.github.colla69.makemytaxes.core.form.Interview;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

/**
 * The step "Dokumente hochladen" of the interview: the upload of the certificates, and the check of
 * the values that the app read from them.
 */
@Controller
class InterviewDocumentController {

  private final InterviewContext context;
  private final InterviewDocuments certificates;
  private final FormStore forms;
  private final DocumentStore documents;

  InterviewDocumentController(
      InterviewContext context,
      InterviewDocuments certificates,
      FormStore forms,
      DocumentStore documents) {
    this.context = context;
    this.certificates = certificates;
    this.forms = forms;
    this.documents = documents;
  }

  /** Uploads one Lohnsteuerbescheinigung in the step "Dokumente hochladen". */
  @PostMapping("/returns/{returnId}/interview/documents")
  String uploadDocument(
      @PathVariable long returnId, @RequestParam(required = false) MultipartFile file, Model model)
      throws IOException {
    context.definitionOf(returnId);
    try {
      if (file == null) {
        throw new IllegalArgumentException("Bitte eine Datei wählen. Eine leere Datei geht nicht.");
      }
      documents.add(
          returnId,
          DocumentController.toNewDocument(DocumentKind.WAGE_TAX_CERTIFICATE, file),
          LocalDate.now());
    } catch (IllegalArgumentException e) {
      model.addAttribute("documentError", e.getMessage());
      return certificates.documentsScreen(returnId, model);
    }
    return "redirect:" + interviewHref(returnId);
  }

  /** The server stops a file over its own limit before the method for the upload runs. */
  @ExceptionHandler(MaxUploadSizeExceededException.class)
  String tooLarge(HttpServletRequest request, Model model) {
    model.addAttribute("documentError", DocumentController.TOO_LARGE);
    return certificates.documentsScreen(DocumentController.returnIdOf(request), model);
  }

  /** Leaves the step "Dokumente hochladen" without values from documents. */
  @PostMapping("/returns/{returnId}/interview/documents/skip")
  String skipDocuments(@PathVariable long returnId) {
    context.definitionOf(returnId);
    Map<String, String> values = new LinkedHashMap<>(forms.answers(returnId, Interview.ID).values());
    values.put(CertificateEvidence.DOCUMENTS_STEP, Interview.DONE);
    forms.save(returnId, Interview.ID, new Answers(values));
    return "redirect:" + interviewHref(returnId);
  }

  /**
   * Takes the checked values of the uploaded certificates: certificate {@code n} is the certificate
   * of employer {@code n}. Each value that the user confirmed answers its question, with the
   * certificate as its source. An empty or invalid value leaves its question open.
   */
  @PostMapping("/returns/{returnId}/interview/documents/confirm")
  String confirmDocuments(
      @PathVariable long returnId, @RequestParam Map<String, String> submitted) {
    context.definitionOf(returnId);
    List<StoredDocument> stored = certificates.certificatesOf(returnId);
    Map<String, String> fromCertificates =
        CertificateEvidence.certificateAnswers(stored.stream().map(StoredDocument::id).toList());
    Map<String, String> values = new LinkedHashMap<>(forms.answers(returnId, Interview.ID).values());
    values.putAll(fromCertificates);
    values.put(CertificateEvidence.DOCUMENTS_STEP, Interview.DONE);
    // The interview names the certificates first, so each value below takes its certificate as
    // its source.
    forms.save(returnId, Interview.ID, new Answers(values));
    Set<String> prefilled = new LinkedHashSet<>(fromCertificates.keySet());
    for (int employer = 1; employer <= stored.size(); employer++) {
      long document = stored.get(employer - 1).id();
      for (CertificateField target : InterviewDocuments.CERTIFICATE_FIELDS) {
        String value =
            submitted
                .getOrDefault(InterviewDocuments.inputName(document, target.field().id()), "")
                .strip();
        String fieldId = AnlageN.employerFieldId(employer, target.field().id());
        if (!value.isEmpty()
            && context.saveField(returnId, new FieldTarget(target.formId(), fieldId), value)
                == null) {
          prefilled.add(fieldId);
          values.put(fieldId, Interview.DONE);
        }
      }
    }
    values.put(CertificateEvidence.PREFILLED, CertificateEvidence.prefilledText(prefilled));
    forms.save(returnId, Interview.ID, new Answers(values));
    return "redirect:" + interviewHref(returnId);
  }
}
