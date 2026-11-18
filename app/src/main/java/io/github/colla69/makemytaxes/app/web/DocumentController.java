package io.github.colla69.makemytaxes.app.web;

import io.github.colla69.makemytaxes.app.storage.DocumentStore;
import io.github.colla69.makemytaxes.app.storage.DocumentStore.StoredDocument;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/** The list of documents, the preview in the page and the single document. */
@Controller
class DocumentController {

  record Preview(long id, String fileName, boolean image) {

    static Preview of(StoredDocument stored) {
      return new Preview(stored.id(), stored.fileName(), stored.mediaType().startsWith("image/"));
    }
  }

  private final DocumentsPage page;
  private final DocumentStore documents;

  DocumentController(DocumentsPage page, DocumentStore documents) {
    this.page = page;
    this.documents = documents;
  }

  @GetMapping("/returns/{returnId}/documents")
  String list(@PathVariable long returnId, Model model) {
    return page.show(returnId, model);
  }

  /** A panel in the page shows one document, so that the user stays on the list. */
  @GetMapping(value = "/returns/{returnId}/documents", params = "preview")
  String preview(@PathVariable long returnId, @RequestParam long preview, Model model) {
    model.addAttribute("preview", Preview.of(page.storedDocument(returnId, preview)));
    return page.show(returnId, model);
  }

  /** The browser shows the document with its own viewer, because the response says "inline". */
  @GetMapping("/returns/{returnId}/documents/{documentId}")
  void open(
      @PathVariable long returnId, @PathVariable long documentId, HttpServletResponse response)
      throws IOException {
    StoredDocument stored = page.storedDocument(returnId, documentId);
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
}
