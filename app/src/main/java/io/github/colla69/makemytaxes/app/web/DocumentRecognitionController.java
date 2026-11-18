package io.github.colla69.makemytaxes.app.web;

import io.github.colla69.makemytaxes.app.storage.DocumentStore;
import io.github.colla69.makemytaxes.app.storage.DocumentStore.StoredDocument;
import io.github.colla69.makemytaxes.core.document.DocumentKind;
import java.time.Month;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

/** The kind and the month that the app recognized in a stored document, and their correction. */
@Controller
class DocumentRecognitionController {

  record Recognized(long id, String fileName, String text) {}

  private final DocumentsPage page;
  private final DocumentStore documents;

  DocumentRecognitionController(DocumentsPage page, DocumentStore documents) {
    this.page = page;
    this.documents = documents;
  }

  /** What the app recognized in the file that it just stored, so the user can check it. */
  @GetMapping(value = "/returns/{returnId}/documents", params = "recognized")
  String recognized(@PathVariable long returnId, @RequestParam long recognized, Model model) {
    StoredDocument stored = page.storedDocument(returnId, recognized);
    model.addAttribute(
        "recognized", new Recognized(stored.id(), stored.fileName(), recognizedText(returnId, stored)));
    return page.show(returnId, model);
  }

  /** The form for the kind and the month of a stored document, to correct what the app found. */
  @GetMapping(value = "/returns/{returnId}/documents", params = "correct")
  String correct(@PathVariable long returnId, @RequestParam long correct, Model model) {
    StoredDocument stored = correctable(returnId, correct);
    String month =
        documents.payslipsOf(returnId).stream()
            .filter(payslip -> payslip.documentId() == stored.id())
            .map(payslip -> String.valueOf(payslip.month().getValue()))
            .findFirst()
            .orElse("");
    String href = "/returns/" + returnId + "/documents/" + stored.id();
    model.addAttribute(
        "kindForm",
        new KindForm(
            stored.fileName(),
            href,
            stored.mediaType().startsWith("image/"),
            href + "/kind",
            null,
            stored.kind().name(),
            month));
    return page.show(returnId, model);
  }

  /** Gives a stored document the kind and the month that the user chose. */
  @PostMapping("/returns/{returnId}/documents/{documentId}/kind")
  String changeKind(
      @PathVariable long returnId,
      @PathVariable long documentId,
      @RequestParam DocumentKind kind,
      @RequestParam(defaultValue = "0") int month,
      Model model) {
    StoredDocument stored = correctable(returnId, documentId);
    if (kind == DocumentKind.PAYSLIP && (month < 1 || month > 12)) {
      model.addAttribute("kindError", "Bitte den Monat der Lohnabrechnung wählen.");
      return correct(returnId, stored.id(), model);
    }
    // The replace flow deletes the new file on a cancel. This file is already stored, so a
    // correction into a month with another payslip stops here instead.
    if (kind == DocumentKind.PAYSLIP) {
      var other =
          documents
              .payslipOf(returnId, Month.of(month))
              .filter(payslip -> payslip.documentId() != stored.id());
      if (other.isPresent()) {
        model.addAttribute(
            "kindError",
            DocumentStore.monthName(Month.of(month))
                + " hat schon eine Lohnabrechnung: „"
                + other.get().fileName()
                + "“. Bitte zuerst diese löschen.");
        return correct(returnId, stored.id(), model);
      }
    }
    documents.changeKind(returnId, stored.id(), kind);
    if (kind == DocumentKind.PAYSLIP) {
      return page.storeAsPayslip(returnId, stored.id(), Month.of(month));
    }
    return page.recognized(returnId, stored.id());
  }

  /** The kind, such as "Lohnabrechnung März 2025" or "Lohnsteuerbescheinigung 2025". */
  private String recognizedText(long returnId, StoredDocument stored) {
    int year = page.taxYearOf(returnId).value();
    Optional<String> payslipMonth =
        documents.payslipsOf(returnId).stream()
            .filter(payslip -> payslip.documentId() == stored.id())
            .map(payslip -> DocumentStore.monthName(payslip.month()))
            .findFirst();
    if (payslipMonth.isPresent()) {
      return stored.kind().label() + " " + payslipMonth.get() + " " + year;
    }
    if (stored.kind() == DocumentKind.WAGE_TAX_CERTIFICATE) {
      return stored.kind().label() + " " + year;
    }
    return stored.kind().label();
  }

  /** Only a document that no entry uses yet can change its kind, so no entry loses its meaning. */
  private StoredDocument correctable(long returnId, long documentId) {
    StoredDocument stored = page.storedDocument(returnId, documentId);
    if (!documents.usesOf(returnId, documentId).isEmpty()) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Ein Eintrag verwendet das Dokument schon als Beleg.");
    }
    return stored;
  }
}
