package io.github.colla69.makemytaxes.app.web;

import io.github.colla69.makemytaxes.app.document.FileRecognizer;
import io.github.colla69.makemytaxes.app.storage.DocumentStore;
import io.github.colla69.makemytaxes.app.storage.DocumentStore.NewDocument;
import io.github.colla69.makemytaxes.app.storage.DocumentStore.StoredPayslip;
import io.github.colla69.makemytaxes.core.TaxYear;
import io.github.colla69.makemytaxes.core.document.DocumentKind;
import io.github.colla69.makemytaxes.core.document.Recognition;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Month;
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

/** The upload of one or many files, and the files that wait for their kind. */
@Controller
class DocumentUploadController {

  private final DocumentsPage page;
  private final DocumentStore documents;
  private final PendingUploads pendingUploads;

  DocumentUploadController(
      DocumentsPage page, DocumentStore documents, PendingUploads pendingUploads) {
    this.page = page;
    this.documents = documents;
    this.pendingUploads = pendingUploads;
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
      return page.showWithError(
          returnId, "Bitte eine Datei wählen. Eine leere Datei geht nicht.", model);
    }
    if (files.size() == 1) {
      return uploadOne(returnId, files.getFirst(), model);
    }
    TaxYear taxYear = page.taxYearOf(returnId);
    List<UploadResult> results = new ArrayList<>();
    for (MultipartFile file : files) {
      results.add(uploadOfMany(returnId, taxYear, file));
    }
    model.addAttribute(
        "uploadResults", results.stream().map(result -> stillWaiting(returnId, result)).toList());
    return page.show(returnId, model);
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
    String fileName = UploadedFiles.fileNameOf(file.getOriginalFilename());
    NewDocument document;
    try {
      document = UploadedFiles.toNewDocument(DocumentKind.OTHER, file);
    } catch (IllegalArgumentException e) {
      return new UploadResult(fileName, false, e.getMessage());
    }
    int year = taxYear.value();
    return switch (FileRecognizer.recognize(document.bytes(), document.mediaType(), taxYear)) {
      case Recognition.WageTaxCertificate certificate -> {
        page.store(returnId, document, DocumentKind.WAGE_TAX_CERTIFICATE);
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
        long documentId = page.store(returnId, document, DocumentKind.PAYSLIP);
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
    TaxYear taxYear = page.taxYearOf(returnId);
    NewDocument document;
    try {
      document = UploadedFiles.toNewDocument(DocumentKind.OTHER, file);
    } catch (IllegalArgumentException e) {
      return page.showWithError(returnId, e.getMessage(), model);
    }
    return switch (FileRecognizer.recognize(document.bytes(), document.mediaType(), taxYear)) {
      case Recognition.WageTaxCertificate certificate ->
          page.recognized(
              returnId, page.store(returnId, document, DocumentKind.WAGE_TAX_CERTIFICATE));
      case Recognition.Payslip payslip ->
          page.storeAsPayslip(
              returnId, page.store(returnId, document, DocumentKind.PAYSLIP), payslip.month());
      case Recognition.OtherYear other ->
          page.showWithError(
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
    return page.show(returnId, model);
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
    long documentId = page.store(returnId, document, kind);
    if (kind == DocumentKind.PAYSLIP) {
      return page.storeAsPayslip(returnId, documentId, Month.of(month));
    }
    return "redirect:/returns/" + returnId + "/documents";
  }

  /** "Abbrechen": the app forgets the file and stores nothing. */
  @PostMapping("/returns/{returnId}/uploads/{token}/cancel")
  String cancelPending(@PathVariable long returnId, @PathVariable String token) {
    pendingUploads.remove(token);
    return "redirect:/returns/" + returnId + "/documents";
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  String tooLarge(HttpServletRequest request, Model model) {
    return page.showUploadTooLarge(request, model);
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
}
