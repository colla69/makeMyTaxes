package io.github.colla69.makemytaxes.app.web;

import io.github.colla69.makemytaxes.app.storage.DocumentStore;
import io.github.colla69.makemytaxes.app.storage.DocumentStore.AnswerUse;
import io.github.colla69.makemytaxes.app.storage.DocumentStore.ExpenseUse;
import io.github.colla69.makemytaxes.app.storage.DocumentStore.StoredDocument;
import io.github.colla69.makemytaxes.app.storage.DocumentStore.Uses;
import io.github.colla69.makemytaxes.core.GermanFormats;
import io.github.colla69.makemytaxes.core.TaxYear;
import io.github.colla69.makemytaxes.core.form.Field;
import io.github.colla69.makemytaxes.core.form.FormModule;
import io.github.colla69.makemytaxes.core.form.Forms;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** The delete of a document and its replacement by a new version. */
@Controller
class DocumentVersionController {

  /** An entry that uses a document as its evidence, with the page that shows the entry. */
  record UseView(String label, String href) {}

  private final DocumentsPage page;
  private final DocumentStore documents;
  private final DocumentReplacement replacement;

  DocumentVersionController(
      DocumentsPage page, DocumentStore documents, DocumentReplacement replacement) {
    this.page = page;
    this.documents = documents;
    this.replacement = replacement;
  }

  /**
   * The page asks once more before it deletes. For a document that no entry uses, it asks in the
   * row of the document. For a document that is evidence, it asks in a popup that names the
   * entries.
   */
  @GetMapping(value = "/returns/{returnId}/documents", params = "delete")
  String confirmDelete(@PathVariable long returnId, @RequestParam long delete, Model model) {
    StoredDocument stored = page.storedDocument(returnId, delete);
    Uses uses = documents.usesOf(returnId, delete);
    if (uses.isEmpty()) {
      model.addAttribute("confirmDelete", stored.id());
    } else {
      model.addAttribute("deleteDocument", stored);
      model.addAttribute("deleteUses", useViews(returnId, uses));
    }
    return page.show(returnId, model);
  }

  /**
   * Deletes the document. If an entry of the return uses it as its evidence, the page asks in a
   * popup first and names those entries. Only "Trotzdem löschen" sends {@code anyway}, and then
   * the entries keep their values and lose their evidence.
   */
  @PostMapping("/returns/{returnId}/documents/{documentId}/delete")
  String delete(
      @PathVariable long returnId,
      @PathVariable long documentId,
      @RequestParam(defaultValue = "false") boolean anyway,
      Model model) {
    StoredDocument stored = page.storedDocument(returnId, documentId);
    Uses uses = documents.usesOf(returnId, documentId);
    if (!uses.isEmpty() && !anyway) {
      model.addAttribute("deleteDocument", stored);
      model.addAttribute("deleteUses", useViews(returnId, uses));
      return page.show(returnId, model);
    }
    documents.delete(returnId, documentId);
    return "redirect:/returns/" + returnId + "/documents";
  }

  /**
   * Stores a new version of a document, with the kind of the old one. If the app reads values of
   * the old document again from the new version, the page shows them for a check first. Otherwise
   * the new version takes the place of the old one at once.
   */
  @PostMapping("/returns/{returnId}/documents/{documentId}/replace")
  String uploadVersion(
      @PathVariable long returnId,
      @PathVariable long documentId,
      @RequestParam(required = false) MultipartFile file,
      Model model)
      throws IOException {
    StoredDocument old = page.storedDocument(returnId, documentId);
    long newId;
    try {
      if (file == null) {
        throw new IllegalArgumentException("Bitte eine Datei wählen. Eine leere Datei geht nicht.");
      }
      newId =
          documents.add(
              returnId, UploadedFiles.toNewDocument(old.kind(), file), LocalDate.now());
    } catch (IllegalArgumentException e) {
      return page.showWithError(returnId, e.getMessage(), model);
    }
    StoredDocument version = page.storedDocument(returnId, newId);
    if (replacement.rows(returnId, old, version).isEmpty()) {
      documents.replace(returnId, replacement.replacement(returnId, old, version));
      return "redirect:/returns/" + returnId + "/documents";
    }
    return "redirect:/returns/" + returnId + "/documents?replace=" + documentId + "&with=" + newId;
  }

  /** The check of a new version: each value of the old document beside its new value. */
  @GetMapping(value = "/returns/{returnId}/documents", params = {"replace", "with"})
  String checkVersion(
      @PathVariable long returnId,
      @RequestParam("replace") long documentId,
      @RequestParam("with") long newId,
      Model model) {
    StoredDocument old = page.storedDocument(returnId, documentId);
    StoredDocument version = page.storedDocument(returnId, newId);
    model.addAttribute("replaceOld", old);
    model.addAttribute("replaceNew", version);
    model.addAttribute("replaceRows", replacement.rows(returnId, old, version));
    documents.payslipsOf(returnId).stream()
        .filter(payslip -> payslip.documentId() == old.id())
        .findFirst()
        .ifPresent(
            payslip ->
                model.addAttribute("replaceMonth", DocumentStore.monthName(payslip.month())));
    return page.show(returnId, model);
  }

  /** "Übernehmen": the new version replaces the old document, with the values read from it. */
  @PostMapping("/returns/{returnId}/documents/{documentId}/replace/{newId}")
  String confirmVersion(
      @PathVariable long returnId, @PathVariable long documentId, @PathVariable long newId) {
    StoredDocument old = page.storedDocument(returnId, documentId);
    StoredDocument version = newVersion(returnId, old, newId);
    documents.replace(returnId, replacement.replacement(returnId, old, version));
    return "redirect:/returns/" + returnId + "/documents";
  }

  /** "Abbrechen": the new version goes, and the old document and all values stay. */
  @PostMapping("/returns/{returnId}/documents/{documentId}/replace/{newId}/cancel")
  String cancelVersion(
      @PathVariable long returnId, @PathVariable long documentId, @PathVariable long newId) {
    StoredDocument old = page.storedDocument(returnId, documentId);
    documents.delete(returnId, newVersion(returnId, old, newId).id());
    return "redirect:/returns/" + returnId + "/documents";
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  String tooLarge(HttpServletRequest request, Model model) {
    return page.showUploadTooLarge(request, model);
  }

  /**
   * The new version must be another document of the same kind that no entry uses yet, so that a
   * confirm or a cancel never takes the evidence of an entry.
   */
  private StoredDocument newVersion(long returnId, StoredDocument old, long newId) {
    StoredDocument version = page.storedDocument(returnId, newId);
    if (version.id() == old.id()
        || version.kind() != old.kind()
        || !documents.usesOf(returnId, newId).isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    }
    return version;
  }

  private List<UseView> useViews(long returnId, Uses uses) {
    TaxYear taxYear = page.taxYearOf(returnId);
    String returnHref = "/returns/" + returnId;
    List<UseView> views = new ArrayList<>();
    for (ExpenseUse expense : uses.expenses()) {
      views.add(
          new UseView(
              "Ausgabe vom "
                  + GermanFormats.format(expense.paidOn())
                  + ": "
                  + expense.description(),
              returnHref));
    }
    for (AnswerUse answer : uses.answers()) {
      Optional<FormModule> form = Forms.find(taxYear, answer.formId());
      String title = form.map(FormModule::title).orElse(answer.formId());
      String label =
          form.flatMap(f -> f.field(answer.fieldId())).map(Field::label).orElse(answer.fieldId());
      String href =
          form.map(f -> returnHref + "/forms/" + f.id()).orElse(returnHref + "/interview");
      views.add(new UseView(title + ": " + label, href));
    }
    return views;
  }
}
