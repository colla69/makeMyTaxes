package io.github.colla69.makemytaxes.app.web;

import io.github.colla69.makemytaxes.app.storage.DocumentStore;
import io.github.colla69.makemytaxes.app.storage.DocumentStore.NewDocument;
import io.github.colla69.makemytaxes.app.storage.DocumentStore.StoredDocument;
import io.github.colla69.makemytaxes.app.storage.DocumentStore.StoredPayslip;
import io.github.colla69.makemytaxes.app.storage.HouseholdStore;
import io.github.colla69.makemytaxes.core.GermanFormats;
import io.github.colla69.makemytaxes.core.TaxYear;
import io.github.colla69.makemytaxes.core.document.DocumentKind;
import io.github.colla69.makemytaxes.core.form.Forms;
import io.github.colla69.makemytaxes.core.form.Kind;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.time.Month;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;
import org.springframework.web.server.ResponseStatusException;

/**
 * What the document controllers share: the documents page, the lookups, and the steps that store
 * a new document and file it as the payslip of a month.
 */
@Component
class DocumentsPage {

  static final String VIEW = "documents";

  record DocumentRow(long id, String kind, String fileName, String size, String uploadedOn) {

    static DocumentRow of(StoredDocument stored) {
      return new DocumentRow(
          stored.id(),
          stored.kind().label(),
          stored.fileName(),
          sizeText(stored.sizeBytes()),
          GermanFormats.format(stored.uploadedOn()));
    }
  }

  private final HouseholdStore household;
  private final DocumentStore documents;
  private final PayslipOverview payslips;
  private final PendingUploads pendingUploads;

  DocumentsPage(
      HouseholdStore household,
      DocumentStore documents,
      PayslipOverview payslips,
      PendingUploads pendingUploads) {
    this.household = household;
    this.documents = documents;
    this.payslips = payslips;
    this.pendingUploads = pendingUploads;
  }

  /** Fills the model with the list of documents and returns the view. */
  String show(long returnId, Model model) {
    TaxYear taxYear = taxYearOf(returnId);
    model.addAttribute("returnId", returnId);
    model.addAttribute("tab", "documents");
    model.addAttribute("forms", Forms.of(taxYear));
    model.addAttribute("taxYear", taxYear.value());
    model.addAttribute("kinds", kindOptions());
    model.addAttribute(
        "rows", documents.documentsOf(returnId).stream().map(DocumentRow::of).toList());
    model.addAttribute("waiting", pendingUploads.of(returnId));
    payslips.fill(returnId, taxYear, model);
    return VIEW;
  }

  String showWithError(long returnId, String error, Model model) {
    model.addAttribute("error", error);
    return show(returnId, model);
  }

  /**
   * The server stops a file over its own limit before the upload method runs. The upload is
   * parsed lazily, so the handler still knows the return and can show the page with the message.
   */
  String showUploadTooLarge(HttpServletRequest request, Model model) {
    return showWithError(UploadedFiles.returnIdOf(request), UploadedFiles.UPLOAD_TOO_LARGE, model);
  }

  TaxYear taxYearOf(long returnId) {
    return household
        .taxYearOf(returnId)
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Keine Erklärung mit der ID " + returnId + "."));
  }

  StoredDocument storedDocument(long returnId, long documentId) {
    return documents
        .find(returnId, documentId)
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Kein Dokument mit der ID " + documentId + "."));
  }

  long store(long returnId, NewDocument document, DocumentKind kind) {
    return documents.add(
        returnId,
        new NewDocument(kind, document.fileName(), document.mediaType(), document.bytes()),
        LocalDate.now());
  }

  /**
   * Makes the stored payslip the payslip of the month. If the month has a payslip already, the
   * page asks first: "Übernehmen" replaces the old payslip, and "Abbrechen" deletes the new file.
   */
  String storeAsPayslip(long returnId, long documentId, Month month) {
    Optional<StoredPayslip> existing = documents.payslipOf(returnId, month);
    if (existing.isPresent() && existing.get().documentId() != documentId) {
      return "redirect:/returns/" + returnId + "/documents?replace="
          + existing.get().documentId() + "&with=" + documentId;
    }
    if (existing.isEmpty()) {
      documents.fileAsPayslip(returnId, documentId, month);
    }
    return recognized(returnId, documentId);
  }

  String recognized(long returnId, long documentId) {
    return "redirect:/returns/" + returnId + "/documents?recognized=" + documentId;
  }

  static String sizeText(long bytes) {
    if (bytes < 1024 * 1024) {
      return Math.ceilDiv(bytes, 1024) + " KB";
    }
    return String.format(Locale.GERMANY, "%.1f MB", bytes / (1024.0 * 1024));
  }

  private static List<Kind.Option> kindOptions() {
    return Arrays.stream(DocumentKind.values())
        .map(kind -> new Kind.Option(kind.name(), kind.label()))
        .toList();
  }
}
