package io.github.colla69.makemytaxes.app.web;

import io.github.colla69.makemytaxes.app.storage.DocumentStore.NewDocument;
import io.github.colla69.makemytaxes.core.document.DocumentKind;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.HandlerMapping;

/** The checks of an uploaded file, which every page with an upload shares. */
final class UploadedFiles {

  static final long MAX_BYTES = 20L * 1024 * 1024;
  static final String TOO_LARGE = "Die Datei ist größer als 20 MB.";
  static final String UPLOAD_TOO_LARGE =
      "Eine Datei ist größer als 20 MB, oder alle Dateien zusammen sind größer als 100 MB.";

  private UploadedFiles() {}

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

  /** Some browsers send a path with the name. Only the last part is the name of the file. */
  static String fileNameOf(String original) {
    if (original == null) {
      return "Dokument";
    }
    String name = original.substring(Math.max(original.lastIndexOf('/'), original.lastIndexOf('\\')) + 1);
    return name.isBlank() ? "Dokument" : name.strip();
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

  private static boolean startsWith(byte[] bytes, byte[] prefix) {
    return bytes.length >= prefix.length
        && Arrays.equals(bytes, 0, prefix.length, prefix, 0, prefix.length);
  }
}
