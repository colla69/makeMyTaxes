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

  DocumentController(HouseholdStore household, DocumentStore documents, PayslipOverview payslips) {
    this.household = household;
    this.documents = documents;
    this.payslips = payslips;
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

  /** The page asks once more before it deletes, in the row of the document. */
  @GetMapping(value = "/returns/{returnId}/documents", params = "delete")
  String confirmDelete(@PathVariable long returnId, @RequestParam long delete, Model model) {
    model.addAttribute("confirmDelete", storedDocument(returnId, delete).id());
    fillPage(returnId, model);
    return "documents";
  }

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
   * Deletes the document, unless an entry of the return uses it as its evidence. Then the page
   * names those entries, so that the user can change them first.
   */
  @PostMapping("/returns/{returnId}/documents/{documentId}/delete")
  String delete(@PathVariable long returnId, @PathVariable long documentId, Model model) {
    StoredDocument stored = storedDocument(returnId, documentId);
    Uses uses = documents.usesOf(returnId, documentId);
    if (!uses.isEmpty()) {
      model.addAttribute(
          "deleteError",
          "„"
              + stored.fileName()
              + "“ ist der Beleg dieser Einträge. Bitte zuerst die Einträge ändern.");
      model.addAttribute("deleteUses", useNames(uses, taxYearOf(returnId)));
      fillPage(returnId, model);
      return "documents";
    }
    documents.delete(returnId, documentId);
    return "redirect:/returns/" + returnId + "/documents";
  }

  private static List<String> useNames(Uses uses, TaxYear taxYear) {
    List<String> names = new ArrayList<>();
    for (ExpenseUse expense : uses.expenses()) {
      names.add(
          "Ausgabe vom " + GermanFormats.format(expense.paidOn()) + ": " + expense.description());
    }
    for (AnswerUse answer : uses.answers()) {
      Optional<FormModule> form = Forms.find(taxYear, answer.formId());
      String title = form.map(FormModule::title).orElse(answer.formId());
      String label =
          form.flatMap(f -> f.field(answer.fieldId())).map(Field::label).orElse(answer.fieldId());
      names.add(title + ": " + label);
    }
    return names;
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
