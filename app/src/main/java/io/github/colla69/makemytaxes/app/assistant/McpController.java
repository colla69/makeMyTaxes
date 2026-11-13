package io.github.colla69.makemytaxes.app.assistant;

import io.github.colla69.makemytaxes.app.storage.DocumentStore;
import io.github.colla69.makemytaxes.app.storage.DocumentStore.StoredDocument;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * The MCP server of the app, for Claude Code during one question. It speaks JSON-RPC over HTTP and
 * answers each request with one JSON object, without a stream. The app binds to 127.0.0.1, and
 * each request needs the token of a question that still runs.
 */
@RestController
class McpController {

  static final String SERVER_NAME = "makemytaxes";
  static final String READ_DOCUMENT = "read_document";
  /** The name of the tool in the rules of Claude Code. */
  static final String READ_DOCUMENT_TOOL = "mcp__" + SERVER_NAME + "__" + READ_DOCUMENT;

  private static final Map<String, Object> TOOL =
      Map.of(
          "name",
          READ_DOCUMENT,
          "description",
          "Legt einen Beleg der Erklärung als Datei ab und nennt ihren Pfad. Lies die Datei danach"
              + " mit dem Werkzeug Read.",
          "inputSchema",
          Map.of(
              "type",
              "object",
              "properties",
              Map.of(
                  "document_id",
                  Map.of("type", "integer", "description", "Die Beleg-Nr. aus dem Kontext")),
              "required",
              List.of("document_id")));

  private final QuestionTokens tokens;
  private final DocumentStore documents;

  McpController(QuestionTokens tokens, DocumentStore documents) {
    this.tokens = tokens;
    this.documents = documents;
  }

  @PostMapping(path = "/mcp", produces = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<Map<String, Object>> handle(
      @RequestHeader(name = "Authorization", required = false) String authorization,
      @RequestBody Map<String, Object> message) {
    Optional<DocumentAccess> access = tokens.find(bearer(authorization));
    if (access.isEmpty()) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    Object id = message.get("id");
    if (id == null) {
      // A notification gets no answer.
      return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }
    Object method = message.get("method");
    Map<String, Object> params = asMap(message.get("params"));
    return ResponseEntity.ok(
        switch (String.valueOf(method)) {
          case "initialize" -> result(id, initialize(params));
          case "ping" -> result(id, Map.of());
          case "tools/list" -> result(id, Map.of("tools", List.of(TOOL)));
          case "tools/call" -> result(id, callTool(access.get(), params));
          default -> error(id, -32601, "Unknown method");
        });
  }

  private static Map<String, Object> initialize(Map<String, Object> params) {
    Object version = params.getOrDefault("protocolVersion", "2025-06-18");
    return Map.of(
        "protocolVersion",
        version,
        "capabilities",
        Map.of("tools", Map.of()),
        "serverInfo",
        Map.of("name", SERVER_NAME, "version", "1"));
  }

  private Map<String, Object> callTool(DocumentAccess access, Map<String, Object> params) {
    if (!READ_DOCUMENT.equals(params.get("name"))) {
      return toolText("Dieses Werkzeug gibt es nicht.", true);
    }
    Object documentId = asMap(params.get("arguments")).get("document_id");
    if (!(documentId instanceof Number number)) {
      return toolText("Gib die Beleg-Nr. als Zahl an.", true);
    }
    Optional<StoredDocument> document = find(access, number.longValue());
    if (document.isEmpty()) {
      return toolText("Diesen Beleg gibt es in den Erklärungen dieser Frage nicht.", true);
    }
    String fileName = "beleg-" + document.get().id() + extension(document.get());
    return tokens
        .write(access, fileName, documents.content(document.get().id()))
        .map(
            file ->
                toolText(
                    "Der Beleg "
                        + document.get().fileName()
                        + " liegt unter "
                        + file
                        + ". Lies die Datei mit dem Werkzeug Read.",
                    false))
        .orElseGet(() -> toolText("Die Frage ist schon beendet.", true));
  }

  /** The document only if it belongs to a return of the question. */
  private Optional<StoredDocument> find(DocumentAccess access, long documentId) {
    for (long returnId : access.returnIds()) {
      Optional<StoredDocument> document = documents.find(returnId, documentId);
      if (document.isPresent()) {
        return document;
      }
    }
    return Optional.empty();
  }

  private static String extension(StoredDocument document) {
    return switch (document.mediaType()) {
      case "application/pdf" -> ".pdf";
      case "image/png" -> ".png";
      case "image/jpeg" -> ".jpg";
      default -> ".bin";
    };
  }

  private static Map<String, Object> toolText(String text, boolean isError) {
    return Map.of("content", List.of(Map.of("type", "text", "text", text)), "isError", isError);
  }

  private static Map<String, Object> result(Object id, Map<String, Object> result) {
    Map<String, Object> answer = new LinkedHashMap<>();
    answer.put("jsonrpc", "2.0");
    answer.put("id", id);
    answer.put("result", result);
    return answer;
  }

  private static Map<String, Object> error(Object id, int code, String text) {
    Map<String, Object> answer = new LinkedHashMap<>();
    answer.put("jsonrpc", "2.0");
    answer.put("id", id);
    answer.put("error", Map.of("code", code, "message", text));
    return answer;
  }

  private static String bearer(String authorization) {
    String prefix = "Bearer ";
    if (authorization == null || !authorization.startsWith(prefix)) {
      return null;
    }
    return authorization.substring(prefix.length());
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> asMap(Object value) {
    return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
  }
}
