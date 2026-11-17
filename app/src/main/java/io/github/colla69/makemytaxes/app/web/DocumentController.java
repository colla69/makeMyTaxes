package io.github.colla69.makemytaxes.app.web;

import io.github.colla69.makemytaxes.app.document.FileRecognizer;
import io.github.colla69.makemytaxes.app.storage.DocumentStore;
import io.github.colla69.makemytaxes.app.storage.DocumentStore.NewDocument;
import io.github.colla69.makemytaxes.app.storage.DocumentStore.AnswerUse;
import io.github.colla69.makemytaxes.app.storage.DocumentStore.ExpenseUse;
import io.github.colla69.makemytaxes.app.storage.DocumentStore.StoredDocument;
import io.github.colla69.makemytaxes.app.storage.DocumentStore.StoredPayslip;
import io.github.colla69.makemytaxes.app.storage.DocumentStore.Uses;
import io.github.colla69.makemytaxes.app.storage.HouseholdStore;
import io.github.colla69.makemytaxes.core.GermanFormats;
import io.github.colla69.makemytaxes.core.TaxYear;
import io.github.colla69.makemytaxes.core.document.DocumentKind;
import io.github.colla69.makemytaxes.core.document.Recognition;
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
  static final String UPLOAD_TOO_LARGE =
      "Eine Datei ist größer als 20 MB, oder alle Dateien zusammen sind größer als 100 MB.";

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
  private final PendingUploads pendingUploads;

  DocumentController(
      HouseholdStore household,
      DocumentStore documents,
      PayslipOverview payslips,
      DocumentReplacement replacement,
      PendingUploads pendingUploads) {
    this.household = household;
    this.documents = documents;
    this.payslips = payslips;
    this.replacement = replacement;
    this.pendingUploads = pendingUploads;
  }

  @GetMapping("/returns/{returnId}/documents")
  String page(@PathVariable long returnId, Model model) {
    fillPage(returnId, model);
    return "documents";
  }

  /**
   * Stores each file where it belongs, if the app recognizes it. For one file, the page goes on
   * as for a single upload. For several files, the page shows one result line per file, and each
   * file that the app cannot recognize waits in the list of files without a kind.
   */
  @PostMapping("/returns/{returnId}/documents")
  String upload(
      @PathVariable long returnId,
      @RequestParam(name = "file", required = false) List<MultipartFile> files,
      Model model)
      throws IOException {
    if (files == null || files.isEmpty()) {
      return pageWithError(
          returnId, "Bitte eine Datei wählen. Eine leere Datei geht nicht.", model);
    }
    if (files.size() == 1) {
      return uploadOne(returnId, files.getFirst(), model);
    }
    TaxYear taxYear = taxYearOf(returnId);
    List<UploadResult> results = new ArrayList<>();
    for (MultipartFile file : files) {
      results.add(uploadOfMany(returnId, taxYear, file));
    }
    model.addAttribute(
        "uploadResults", results.stream().map(result -> stillWaiting(returnId, result)).toList());
    fillPage(returnId, model);
    return "documents";
  }

  /**
   * What became of one file of an upload of several files.
   *
   * @param waitingToken the token of a file that waits for its kind, or null
   */
  record UploadResult(String fileName, boolean stored, String text, String waitingToken) {

    UploadResult(String fileName, boolean stored, String text) {
      this(fileName, stored, text, null);
    }
  }

  /** The list of waiting files keeps only the newest few, so a later file can push one out. */
  private UploadResult stillWaiting(long returnId, UploadResult result) {
    if (result.waitingToken() == null
        || pendingUploads.find(returnId, result.waitingToken()).isPresent()) {
      return result;
    }
    return new UploadResult(
        result.fileName(),
        false,
        "Nicht erkannt. Zu viele Dateien warten auf ihre Art, daher hat die App die Datei nicht"
            + " behalten. Bitte später noch einmal hochladen.");
  }

  /**
   * Stores one file of several. Unlike a single upload, a payslip for a month that has one
   * already is not stored, because the page cannot ask about each file in turn.
   */
  private UploadResult uploadOfMany(long returnId, TaxYear taxYear, MultipartFile file)
      throws IOException {
    String fileName = fileNameOf(file.getOriginalFilename());
    NewDocument document;
    try {
      document = toNewDocument(DocumentKind.OTHER, file);
    } catch (IllegalArgumentException e) {
      return new UploadResult(fileName, false, e.getMessage());
    }
    int year = taxYear.value();
    return switch (FileRecognizer.recognize(document.bytes(), document.mediaType(), taxYear)) {
      case Recognition.WageTaxCertificate certificate -> {
        store(returnId, document, DocumentKind.WAGE_TAX_CERTIFICATE);
        yield new UploadResult(
            fileName, true, DocumentKind.WAGE_TAX_CERTIFICATE.label() + " " + year);
      }
      case Recognition.Payslip payslip -> {
        String monthName = DocumentStore.monthName(payslip.month());
        Optional<StoredPayslip> existing = documents.payslipOf(returnId, payslip.month());
        if (existing.isPresent()) {
          yield new UploadResult(
              fileName,
              false,
              monthName + " hat schon eine Lohnabrechnung: „" + existing.get().fileName()
                  + "“. Die App hat die Datei nicht gespeichert.");
        }
        long documentId = store(returnId, document, DocumentKind.PAYSLIP);
        documents.fileAsPayslip(returnId, documentId, payslip.month());
        yield new UploadResult(
            fileName, true, DocumentKind.PAYSLIP.label() + " " + monthName + " " + year);
      }
      case Recognition.OtherYear other ->
          new UploadResult(
              fileName,
              false,
              "Gehört zum Jahr " + other.year() + ", nicht zu " + year
                  + ". Die App hat die Datei nicht gespeichert.");
      case Recognition.Unknown unknown -> {
        String token = pendingUploads.put(returnId, document);
        yield new UploadResult(
            fileName, false, "Nicht erkannt. Die Datei wartet unten auf ihre Art.", token);
      }
    };
  }

  /**
   * Stores the file where it belongs, if the app recognizes it. A file of another year is not
   * stored. For a file that the app cannot recognize, the page asks for the kind.
   */
  private String uploadOne(long returnId, MultipartFile file, Model model) throws IOException {
    TaxYear taxYear = taxYearOf(returnId);
    NewDocument document;
    try {
      document = toNewDocument(DocumentKind.OTHER, file);
    } catch (IllegalArgumentException e) {
      return pageWithError(returnId, e.getMessage(), model);
    }
    return switch (FileRecognizer.recognize(document.bytes(), document.mediaType(), taxYear)) {
      case Recognition.WageTaxCertificate certificate ->
          recognized(
              returnId, store(returnId, document, DocumentKind.WAGE_TAX_CERTIFICATE));
      case Recognition.Payslip payslip ->
          storeAsPayslip(returnId, store(returnId, document, DocumentKind.PAYSLIP), payslip.month());
      case Recognition.OtherYear other ->
          pageWithError(
              returnId,
              "„" + document.fileName() + "“ gehört zum Jahr " + other.year() + ", nicht zu "
                  + taxYear.value() + ". Die App hat die Datei nicht gespeichert.",
              model);
      case Recognition.Unknown unknown ->
          "redirect:/returns/" + returnId + "/documents?pending="
              + pendingUploads.put(returnId, document);
    };
  }

  /** The form for the kind of a file that the app could not recognize, with the file beside it. */
  @GetMapping(value = "/returns/{returnId}/documents", params = "pending")
  String pending(@PathVariable long returnId, @RequestParam String pending, Model model) {
    NewDocument document = pendingDocument(returnId, pending);
    model.addAttribute(
        "kindForm",
        new KindForm(
            document.fileName(),
            "/returns/" + returnId + "/uploads/" + pending,
            document.mediaType().startsWith("image/"),
            "/returns/" + returnId + "/uploads/" + pending,
            "/returns/" + returnId + "/uploads/" + pending + "/cancel",
            "",
            ""));
    fillPage(returnId, model);
    return "documents";
  }

  /** The bytes of a file that waits for its kind, for the preview beside the form. */
  @GetMapping("/returns/{returnId}/uploads/{token}")
  void openPending(
      @PathVariable long returnId, @PathVariable String token, HttpServletResponse response)
      throws IOException {
    NewDocument document = pendingDocument(returnId, token);
    response.setContentType(document.mediaType());
    response.setHeader("X-Content-Type-Options", "nosniff");
    response.getOutputStream().write(document.bytes());
  }

  /** Stores a file that waits for its kind, with the kind and the month that the user chose. */
  @PostMapping("/returns/{returnId}/uploads/{token}")
  String storePending(
      @PathVariable long returnId,
      @PathVariable String token,
      @RequestParam DocumentKind kind,
      @RequestParam(defaultValue = "0") int month,
      Model model) {
    NewDocument document = pendingDocument(returnId, token);
    if (kind == DocumentKind.PAYSLIP && (month < 1 || month > 12)) {
      model.addAttribute("kindError", "Bitte den Monat der Lohnabrechnung wählen.");
      return pending(returnId, token, model);
    }
    pendingUploads.remove(token);
    long documentId = store(returnId, document, kind);
    if (kind == DocumentKind.PAYSLIP) {
      return storeAsPayslip(returnId, documentId, Month.of(month));
    }
    return "redirect:/returns/" + returnId + "/documents";
  }

  /** "Abbrechen": the app forgets the file and stores nothing. */
  @PostMapping("/returns/{returnId}/uploads/{token}/cancel")
  String cancelPending(@PathVariable long returnId, @PathVariable String token) {
    pendingUploads.remove(token);
    return "redirect:/returns/" + returnId + "/documents";
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
    fillPage(returnId, model);
    return "documents";
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
      return storeAsPayslip(returnId, stored.id(), Month.of(month));
    }
    return "redirect:/returns/" + returnId + "/documents?recognized=" + stored.id();
  }

  /** What the app recognized in the file that it just stored, so the user can check it. */
  @GetMapping(value = "/returns/{returnId}/documents", params = "recognized")
  String recognized(@PathVariable long returnId, @RequestParam long recognized, Model model) {
    StoredDocument stored = storedDocument(returnId, recognized);
    model.addAttribute(
        "recognized", new Recognized(stored.id(), stored.fileName(), recognizedText(returnId, stored)));
    fillPage(returnId, model);
    return "documents";
  }

  /**
   * The form for the kind of one file.
   *
   * @param fileHref the address of the file, for the preview
   * @param action where the form sends the kind and the month
   * @param cancelAction where "Abbrechen" sends a file that waits for its kind, so the app forgets
   *     it, or null for a stored document
   * @param kind the kind that the form shows first, or empty
   * @param month the number of the month that the form shows first, or empty
   */
  record KindForm(
      String fileName,
      String fileHref,
      boolean image,
      String action,
      String cancelAction,
      String kind,
      String month) {}

  record Recognized(long id, String fileName, String text) {}

  /** The kind, such as "Lohnabrechnung März 2025" or "Lohnsteuerbescheinigung 2025". */
  private String recognizedText(long returnId, StoredDocument stored) {
    int year = taxYearOf(returnId).value();
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

  private String recognized(long returnId, long documentId) {
    return "redirect:/returns/" + returnId + "/documents?recognized=" + documentId;
  }

  /**
   * Makes the stored payslip the payslip of the month. If the month has a payslip already, the
   * page asks first: "Übernehmen" replaces the old payslip, and "Abbrechen" deletes the new file.
   */
  private String storeAsPayslip(long returnId, long documentId, Month month) {
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

  private long store(long returnId, NewDocument document, DocumentKind kind) {
    return documents.add(
        returnId,
        new NewDocument(kind, document.fileName(), document.mediaType(), document.bytes()),
        LocalDate.now());
  }

  private NewDocument pendingDocument(long returnId, String token) {
    return pendingUploads
        .find(returnId, token)
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Die Datei ist nicht mehr da. Bitte laden Sie sie noch einmal hoch."));
  }

  /** Only a document that no entry uses yet can change its kind, so no entry loses its meaning. */
  private StoredDocument correctable(long returnId, long documentId) {
    StoredDocument stored = storedDocument(returnId, documentId);
    if (!documents.usesOf(returnId, documentId).isEmpty()) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Ein Eintrag verwendet das Dokument schon als Beleg.");
    }
    return stored;
  }

  private String pageWithError(long returnId, String error, Model model) {
    model.addAttribute("error", error);
    fillPage(returnId, model);
    return "documents";
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
    documents.payslipsOf(returnId).stream()
        .filter(payslip -> payslip.documentId() == old.id())
        .findFirst()
        .ifPresent(
            payslip ->
                model.addAttribute("replaceMonth", DocumentStore.monthName(payslip.month())));
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
    model.addAttribute("error", UPLOAD_TOO_LARGE);
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
    model.addAttribute("waiting", pendingUploads.of(returnId));
    payslips.fill(returnId, taxYear, model);
  }

  private static List<Kind.Option> kindOptions() {
    return Arrays.stream(DocumentKind.values())
        .map(kind -> new Kind.Option(kind.name(), kind.label()))
        .toList();
  }
}
