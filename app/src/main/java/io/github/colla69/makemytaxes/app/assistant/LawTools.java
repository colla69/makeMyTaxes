package io.github.colla69.makemytaxes.app.assistant;

import io.github.colla69.makemytaxes.app.assistant.LawLibrary.NotFound;
import io.github.colla69.makemytaxes.app.assistant.LawLibrary.Pdf;
import io.github.colla69.makemytaxes.app.assistant.LawLibrary.Text;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
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

  /**
   * The most characters of one answer. Claude Code cuts the output of an MCP tool at about 25,000
   * tokens, so a longer text comes in parts that stay well below that.
   */
  static final int MAX_TEXT = 40_000;

  private static final Map<String, Object> PART =
      Map.of(
          "type",
          "integer",
          "description",
          "Der Teil eines langen Textes, ab 1. Ohne Angabe kommt Teil 1.");

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
              "Liest eine Vorschrift oder einen ihrer Absätze in allen Fassungen des Jahres der"
                  + " Erklärung, mit den Zeilen Stand und Hinweis. Beim EStG folgen § 52 Absatz 1"
                  + " und die Absätze von § 52, die die Vorschrift nennen. Ein langer Text kommt in"
                  + " Teilen.",
              schema(
                  Map.of(
                      "law", text("Das Gesetz, wie estg, estdv_1955, ao_1977 oder solzg_1995"),
                      "provision",
                      text("Die Vorschrift, wie § 35a, oder ein Absatz, wie § 52 Abs. 34"),
                      "part", PART),
                  "law",
                  "provision")),
          tool(
              READ_GUIDANCE,
              "Liest für das Jahr der Erklärung eine Richtlinie oder einen Hinweis, wie R 9.13"
                  + " LStR oder H 35a EStH, oder ein BMF-Schreiben oder eine Anleitung zum"
                  + " Vordruck mit ihrer ID, wie bmf-35a-2022. Eine unbekannte ID nennt die IDs"
                  + " des Jahres. Ein langer Text kommt in Teilen.",
              schema(
                  Map.of("reference", text("Die Stelle oder die ID"), "part", PART),
                  "reference")));

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
      int part = partNumber(arguments);
      return switch (name) {
        case SEARCH_LAW -> found(library.search(access.taxYear(), string(arguments, "query")));
        case READ_PROVISION ->
            found(
                part(
                    library.provision(
                        access.taxYear(), string(arguments, "law"), string(arguments, "provision")),
                    part,
                    " Lies einen einzelnen Absatz mit provision wie \"§ 52 Abs. 34\"."));
        default -> guidance(access, string(arguments, "reference"), part);
      };
    } catch (NotFound e) {
      return McpController.toolText(e.getMessage(), true);
    }
  }

  private Map<String, Object> guidance(DocumentAccess access, String reference, int part)
      throws NotFound {
    if (reference != null && reference.strip().matches(".* (EStR|EStH|LStR|LStH)")) {
      return found(part(library.handbook(access.taxYear(), reference), part, ""));
    }
    return switch (library.guidance(access.taxYear(), reference)) {
      case Text text -> found(part(text.text(), part, ""));
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

  /**
   * One part of a text of at most {@link #MAX_TEXT} characters, cut at the end of a line. If the
   * text has more than one part, a last line names the part and how to read the next one.
   */
  static String part(String text, int part, String hint) throws NotFound {
    List<String> parts = new ArrayList<>();
    int start = 0;
    while (start < text.length()) {
      int end = Math.min(start + MAX_TEXT, text.length());
      if (end < text.length()) {
        int lineEnd = text.lastIndexOf('\n', end - 1);
        if (lineEnd >= start) {
          end = lineEnd + 1;
        }
      }
      parts.add(text.substring(start, end));
      start = end;
    }
    if (parts.size() <= 1 && part == 1) {
      return text;
    }
    if (part > parts.size()) {
      throw new NotFound("Der Text hat nur " + parts.size() + " Teile.");
    }
    StringBuilder answer =
        new StringBuilder(parts.get(part - 1))
            .append("\n[Teil ")
            .append(part)
            .append(" von ")
            .append(parts.size())
            .append(". Der Text ist hier gekürzt.");
    if (part < parts.size()) {
      answer.append(" Lies den nächsten Teil mit part ").append(part + 1).append(".");
    }
    return answer.append(hint).append("]\n").toString();
  }

  private static int partNumber(Map<String, Object> arguments) throws NotFound {
    Object value = arguments.get("part");
    if (value == null) {
      return 1;
    }
    if (value instanceof Number number && number.intValue() >= 1) {
      return number.intValue();
    }
    throw new NotFound("Gib den Teil als Zahl ab 1 an.");
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
