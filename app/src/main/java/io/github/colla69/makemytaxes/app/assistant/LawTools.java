package io.github.colla69.makemytaxes.app.assistant;

import io.github.colla69.makemytaxes.app.assistant.LawLibrary.NotFound;
import io.github.colla69.makemytaxes.app.assistant.LawLibrary.Pdf;
import io.github.colla69.makemytaxes.app.assistant.LawLibrary.Text;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** The MCP tools that read the law library, always for the tax year of the question. */
@Component
class LawTools {

  static final String SEARCH_LAW = "search_law";
  static final String READ_PROVISION = "read_provision";
  static final String READ_GUIDANCE = "read_guidance";
  static final List<String> NAMES = List.of(SEARCH_LAW, READ_PROVISION, READ_GUIDANCE);

  static final List<Map<String, Object>> DEFINITIONS =
      List.of(
          tool(
              SEARCH_LAW,
              "Sucht einen Begriff in den Gesetzen und den amtlichen Handbüchern (EStR, EStH, LStR,"
                  + " LStH) des Jahres der Erklärung. Nennt je Treffer die Datei, die Überschrift"
                  + " und die Zeile.",
              schema(Map.of("query", text("Der Suchbegriff, wie haushaltsnahe")), "query")),
          tool(
              READ_PROVISION,
              "Liest eine Vorschrift in allen Fassungen des Jahres der Erklärung, mit den Zeilen"
                  + " Stand und Hinweis. Beim EStG folgen § 52 Absatz 1 und die Absätze von § 52,"
                  + " die die Vorschrift nennen.",
              schema(
                  Map.of(
                      "law", text("Das Gesetz, wie estg, estdv_1955, ao_1977 oder solzg_1995"),
                      "provision", text("Die Vorschrift, wie § 35a")),
                  "law",
                  "provision")),
          tool(
              READ_GUIDANCE,
              "Liest für das Jahr der Erklärung eine Richtlinie oder einen Hinweis, wie R 9.13"
                  + " LStR oder H 35a EStH, oder ein BMF-Schreiben oder eine Anleitung zum"
                  + " Vordruck mit ihrer ID, wie bmf-35a-2022. Eine unbekannte ID nennt die IDs"
                  + " des Jahres.",
              schema(Map.of("reference", text("Die Stelle oder die ID")), "reference")));

  private final LawLibrary library;
  private final QuestionTokens tokens;

  LawTools(LawLibrary library, QuestionTokens tokens) {
    this.library = library;
    this.tokens = tokens;
  }

  Map<String, Object> call(DocumentAccess access, String name, Map<String, Object> arguments) {
    if (access.taxYear() == null) {
      return McpController.toolText("Diese Frage sieht keine Erklärung, also kein Jahr.", true);
    }
    try {
      return switch (name) {
        case SEARCH_LAW -> found(library.search(access.taxYear(), string(arguments, "query")));
        case READ_PROVISION ->
            found(
                library.provision(
                    access.taxYear(), string(arguments, "law"), string(arguments, "provision")));
        default -> guidance(access, string(arguments, "reference"));
      };
    } catch (NotFound e) {
      return McpController.toolText(e.getMessage(), true);
    }
  }

  private Map<String, Object> guidance(DocumentAccess access, String reference) throws NotFound {
    if (reference != null && reference.strip().matches(".* (EStR|EStH|LStR|LStH)")) {
      return found(library.handbook(access.taxYear(), reference));
    }
    return switch (library.guidance(access.taxYear(), reference)) {
      case Text text -> found(text.text());
      case Pdf pdf ->
          tokens
              .write(access, pdf.fileName(), bytes(pdf))
              .map(
                  file ->
                      found(
                          pdf.header()
                              + "Die Anleitung liegt unter "
                              + file
                              + ". Lies die Datei mit dem Werkzeug Read."))
              .orElseGet(() -> McpController.toolText("Die Frage ist schon beendet.", true));
    };
  }

  private static byte[] bytes(Pdf pdf) {
    try {
      return Files.readAllBytes(pdf.file());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static Map<String, Object> found(String text) {
    return McpController.toolText(text, false);
  }

  private static String string(Map<String, Object> arguments, String key) {
    Object value = arguments.get(key);
    return value instanceof String text ? text : null;
  }

  private static Map<String, Object> text(String description) {
    return Map.of("type", "string", "description", description);
  }

  private static Map<String, Object> schema(Map<String, Object> properties, String... required) {
    return Map.of("type", "object", "properties", properties, "required", List.of(required));
  }

  private static Map<String, Object> tool(
      String name, String description, Map<String, Object> inputSchema) {
    return Map.of("name", name, "description", description, "inputSchema", inputSchema);
  }
}
