package io.github.colla69.makemytaxes.app.web;

import io.github.colla69.makemytaxes.app.storage.DocumentStore;
import io.github.colla69.makemytaxes.app.storage.DocumentStore.NewDocument;
import io.github.colla69.makemytaxes.app.storage.DocumentStore.NewPayslip;
import io.github.colla69.makemytaxes.app.storage.DocumentStore.AnswerUse;
import io.github.colla69.makemytaxes.app.storage.DocumentStore.ExpenseUse;
import io.github.colla69.makemytaxes.app.storage.DocumentStore.StoredDocument;
import io.github.colla69.makemytaxes.app.storage.DocumentStore.Uses;
import io.github.colla69.makemytaxes.app.storage.HouseholdStore;
import io.github.colla69.makemytaxes.core.GermanFormats;
import io.github.colla69.makemytaxes.core.TaxYear;
import io.github.colla69.makemytaxes.core.document.DocumentKind;
import io.github.colla69.makemytaxes.core.form.Field;
import io.github.colla69.makemytaxes.core.form.FormModule;
import io.github.colla69.makemytaxes.core.form.Forms;
import io.github.colla69.makemytaxes.core.form.Kind;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
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
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.server.ResponseStatusException;

@Controller
class DocumentController {

  static final long MAX_BYTES = 20L * 1024 * 1024;
  static final String TOO_LARGE = "Die Datei ist größer als 20 MB.";

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
  private final DocumentReplacement replacement;

  DocumentController(
      HouseholdStore household,
      DocumentStore documents,
      PayslipOverview payslips,
      DocumentReplacement replacement) {
    this.household = household;
    this.documents = documents;
    this.payslips = payslips;
    this.replacement = replacement;
  }

  @GetMapping("/returns/{returnId}/documents")
  String page(@PathVariable long returnId, Model model) {
    fillPage(returnId, model);
    return "documents";
  }

  @PostMapping("/returns/{returnId}/documents")
  String upload(
      @PathVariable long returnId,
      @RequestParam DocumentKind kind,
      @RequestParam MultipartFile file,
      Model model)
      throws IOException {
    taxYearOf(returnId);
    try {
      if (kind == DocumentKind.PAYSLIP) {
        throw new IllegalArgumentException("Eine Lohnabrechnung gehört in die Liste der Monate.");
      }
      documents.add(returnId, toNewDocument(kind, file), LocalDate.now());
    } catch (IllegalArgumentException e) {
      model.addAttribute("error", e.getMessage());
      fillPage(returnId, model);
      return "documents";
    }
    return "redirect:/returns/" + returnId + "/documents";
  }

  @PostMapping("/returns/{returnId}/payslips")
  String uploadPayslip(
      @PathVariable long returnId,
      @RequestParam int month,
      @RequestParam MultipartFile file,
      Model model)
      throws IOException {
    taxYearOf(returnId);
    try {
      if (month < 1 || month > 12) {
        throw new IllegalArgumentException("Bitte einen Monat wählen.");
      }
      NewPayslip payslip =
          new NewPayslip(Month.of(month), toNewDocument(DocumentKind.PAYSLIP, file));
      documents.addPayslip(returnId, payslip, LocalDate.now());
    } catch (IllegalArgumentException e) {
      model.addAttribute("payslipError", e.getMessage());
      fillPage(returnId, model);
      return "documents";
    }
    return "redirect:/returns/" + returnId + "/documents";
  }

  /**
   * The page asks once more before it deletes. For a document that no entry uses, it asks in the
   * row of the document. For a document that is evidence, it asks in a popup that names the
   * entries.
   */
  @GetMapping(value = "/returns/{returnId}/documents", params = "delete")
  String confirmDelete(@PathVariable long returnId, @RequestParam long delete, Model model) {
    StoredDocument stored = storedDocument(returnId, delete);
    Uses uses = documents.usesOf(returnId, delete);
    if (uses.isEmpty()) {
      model.addAttribute("confirmDelete", stored.id());
    } else {
      model.addAttribute("deleteDocument", stored);
      model.addAttribute("deleteUses", useViews(returnId, uses));
    }
    fillPage(returnId, model);
    return "documents";
  }

  /** An entry that uses a document as its evidence, with the page that shows the entry. */
  record UseView(String label, String href) {}

  /** A panel in the page shows one document, so that the user stays on the list. */
  @GetMapping(value = "/returns/{returnId}/documents", params = "preview")
  String preview(@PathVariable long returnId, @RequestParam long preview, Model model) {
    model.addAttribute("preview", Preview.of(storedDocument(returnId, preview)));
    fillPage(returnId, model);
    return "documents";
  }

  record Preview(long id, String fileName, boolean image) {

    static Preview of(StoredDocument stored) {
      return new Preview(stored.id(), stored.fileName(), stored.mediaType().startsWith("image/"));
    }
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
    StoredDocument stored = storedDocument(returnId, documentId);
    Uses uses = documents.usesOf(returnId, documentId);
    if (!uses.isEmpty() && !anyway) {
      model.addAttribute("deleteDocument", stored);
      model.addAttribute("deleteUses", useViews(returnId, uses));
      fillPage(returnId, model);
      return "documents";
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
    StoredDocument old = storedDocument(returnId, documentId);
    long newId;
    try {
      if (file == null) {
        throw new IllegalArgumentException("Bitte eine Datei wählen. Eine leere Datei geht nicht.");
      }
      newId = documents.add(returnId, toNewDocument(old.kind(), file), LocalDate.now());
    } catch (IllegalArgumentException e) {
      model.addAttribute("error", e.getMessage());
      fillPage(returnId, model);
      return "documents";
    }
    StoredDocument version = storedDocument(returnId, newId);
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
    StoredDocument old = storedDocument(returnId, documentId);
    StoredDocument version = storedDocument(returnId, newId);
    model.addAttribute("replaceOld", old);
    model.addAttribute("replaceNew", version);
    model.addAttribute("replaceRows", replacement.rows(returnId, old, version));
    fillPage(returnId, model);
    return "documents";
  }

  /** "Übernehmen": the new version replaces the old document, with the values read from it. */
  @PostMapping("/returns/{returnId}/documents/{documentId}/replace/{newId}")
  String confirmVersion(
      @PathVariable long returnId, @PathVariable long documentId, @PathVariable long newId) {
    StoredDocument old = storedDocument(returnId, documentId);
    StoredDocument version = newVersion(returnId, old, newId);
    documents.replace(returnId, replacement.replacement(returnId, old, version));
    return "redirect:/returns/" + returnId + "/documents";
  }

  /** "Abbrechen": the new version goes, and the old document and all values stay. */
  @PostMapping("/returns/{returnId}/documents/{documentId}/replace/{newId}/cancel")
  String cancelVersion(
      @PathVariable long returnId, @PathVariable long documentId, @PathVariable long newId) {
    StoredDocument old = storedDocument(returnId, documentId);
    documents.delete(returnId, newVersion(returnId, old, newId).id());
    return "redirect:/returns/" + returnId + "/documents";
  }

  /**
   * The new version must be another document of the same kind that no entry uses yet, so that a
   * confirm or a cancel never takes the evidence of an entry.
   */
  private StoredDocument newVersion(long returnId, StoredDocument old, long newId) {
    StoredDocument version = storedDocument(returnId, newId);
    if (version.id() == old.id()
        || version.kind() != old.kind()
        || !documents.usesOf(returnId, newId).isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    }
    return version;
  }

  private List<UseView> useViews(long returnId, Uses uses) {
    TaxYear taxYear = taxYearOf(returnId);
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

  private StoredDocument storedDocument(long returnId, long documentId) {
    return documents
        .find(returnId, documentId)
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Kein Dokument mit der ID " + documentId + "."));
  }

  /** The browser shows the document with its own viewer, because the response says "inline". */
  @GetMapping("/returns/{returnId}/documents/{documentId}")
  void open(
      @PathVariable long returnId, @PathVariable long documentId, HttpServletResponse response)
      throws IOException {
    StoredDocument stored = storedDocument(returnId, documentId);
    response.setContentType(stored.mediaType());
    response.setContentLengthLong(stored.sizeBytes());
    response.setHeader(
        HttpHeaders.CONTENT_DISPOSITION,
        ContentDisposition.inline()
            .filename(stored.fileName(), StandardCharsets.UTF_8)
            .build()
            .toString());
    response.setHeader("X-Content-Type-Options", "nosniff");
    response.getOutputStream().write(documents.content(stored.id()));
  }

  /**
   * The server stops a file over its own limit before the upload method runs. The upload is
   * parsed lazily, so this handler still knows the return and can show the page with the message.
   */
  @ExceptionHandler(MaxUploadSizeExceededException.class)
  String tooLarge(HttpServletRequest request, Model model) {
    model.addAttribute("error", TOO_LARGE);
    fillPage(returnIdOf(request), model);
    return "documents";
  }

  static long returnIdOf(HttpServletRequest request) {
    return Long.parseLong(pathVariable(request, "returnId"));
  }

  /** An exception handler cannot take a path variable, so it reads the value from the request. */
  static String pathVariable(HttpServletRequest request, String name) {
    @SuppressWarnings("unchecked")
    var path =
        (Map<String, String>)
            request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
    return path.get(name);
  }

  /** The type comes from the first bytes of the file, not from the name or from the browser. */
  static NewDocument toNewDocument(DocumentKind kind, MultipartFile file) throws IOException {
    if (file.isEmpty()) {
      throw new IllegalArgumentException("Bitte eine Datei wählen. Eine leere Datei geht nicht.");
    }
    if (file.getSize() > MAX_BYTES) {
      throw new IllegalArgumentException(TOO_LARGE);
    }
    byte[] bytes = file.getBytes();
    String mediaType = mediaTypeOf(bytes);
    if (mediaType == null) {
      throw new IllegalArgumentException("Nur PDF, JPEG und PNG sind möglich.");
    }
    return new NewDocument(kind, fileNameOf(file.getOriginalFilename()), mediaType, bytes);
  }

  static String mediaTypeOf(byte[] bytes) {
    if (startsWith(bytes, "%PDF-".getBytes(StandardCharsets.US_ASCII))) {
      return "application/pdf";
    }
    if (startsWith(bytes, new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF})) {
      return "image/jpeg";
    }
    if (startsWith(bytes, new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'})) {
      return "image/png";
    }
    return null;
  }

  private static boolean startsWith(byte[] bytes, byte[] prefix) {
    return bytes.length >= prefix.length
        && Arrays.equals(bytes, 0, prefix.length, prefix, 0, prefix.length);
  }

  /** Some browsers send a path with the name. Only the last part is the name of the file. */
  private static String fileNameOf(String original) {
    if (original == null) {
      return "Dokument";
    }
    String name = original.substring(Math.max(original.lastIndexOf('/'), original.lastIndexOf('\\')) + 1);
    return name.isBlank() ? "Dokument" : name.strip();
  }

  static String sizeText(long bytes) {
    if (bytes < 1024 * 1024) {
      return Math.ceilDiv(bytes, 1024) + " KB";
    }
    return String.format(Locale.GERMANY, "%.1f MB", bytes / (1024.0 * 1024));
  }

  private TaxYear taxYearOf(long returnId) {
    return household
        .taxYearOf(returnId)
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Keine Erklärung mit der ID " + returnId + "."));
  }

  private void fillPage(long returnId, Model model) {
    TaxYear taxYear = taxYearOf(returnId);
    model.addAttribute("returnId", returnId);
    model.addAttribute("tab", "documents");
    model.addAttribute("forms", Forms.of(taxYear));
    model.addAttribute("taxYear", taxYear.value());
    model.addAttribute("kinds", kindOptions());
    model.addAttribute(
        "rows", documents.documentsOf(returnId).stream().map(DocumentRow::of).toList());
    payslips.fill(returnId, taxYear, model);
  }

  private static List<Kind.Option> kindOptions() {
    return Arrays.stream(DocumentKind.values())
        .filter(kind -> kind != DocumentKind.PAYSLIP)
        .map(kind -> new Kind.Option(kind.name(), kind.label()))
        .toList();
  }
}
