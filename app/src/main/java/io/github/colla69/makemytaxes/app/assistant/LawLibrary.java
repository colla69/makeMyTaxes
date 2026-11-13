package io.github.colla69.makemytaxes.app.assistant;

import io.github.colla69.makemytaxes.core.TaxYear;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Reads the law library in {@code law/} for the assistant, as {@code scripts/law.sh}, {@code
 * scripts/handbook.sh} and {@code scripts/guidance.sh} do. Each reading is for the tax year of the
 * returns of the question. The library is only read, never written.
 */
@Component
class LawLibrary {

  /** A wanted text is not in the library. The message says why, in German, for the assistant. */
  static class NotFound extends Exception {
    NotFound(String message) {
      super(message);
    }
  }

  /** What a reading gives: a text, or a PDF that the assistant reads with its Read tool. */
  sealed interface Reading permits Text, Pdf {}

  record Text(String text) implements Reading {}

  record Pdf(String header, String fileName, Path file) implements Reading {}

  private static final int MAX_HITS = 40;
  private static final int MAX_LINE = 300;
  private static final Pattern LAW_NAME = Pattern.compile("[a-z0-9_]+");
  private static final Pattern HANDBOOK_REFERENCE =
      Pattern.compile("([RH] \\S+(?: \\S+)*) (EStR|EStH|LStR|LStH)");
  private static final Pattern HANDBOOK_ID_LINE =
      Pattern.compile("[RH] [0-9]+[a-z]?(\\.[0-9]+[a-z]?)*\\.?");
  private static final Pattern PARAGRAPH = Pattern.compile("\\([0-9]+[a-z]?\\) ");
  private static final Pattern GUIDANCE_ID =Pattern.compile("[a-z0-9-]+");

  private final Path root;

  LawLibrary(@Value("${makemytaxes.law.path}") Path root) {
    this.root = root;
  }

  /**
   * The lines of the law and the handbooks of the year that hold the query, without regard to
   * case. A law is searched in its last version of the year.
   */
  String search(TaxYear year, String query) throws NotFound {
    String wanted = query == null ? "" : query.strip().toLowerCase(Locale.GERMAN);
    if (wanted.length() < 3) {
      throw new NotFound("Gib einen Suchbegriff mit mindestens drei Zeichen an.");
    }
    List<Path> files = new ArrayList<>();
    snapshots(year).stream()
        .reduce((first, second) -> second)
        .ifPresent(last -> files.addAll(lawFiles(last)));
    for (String handbook : List.of("esth", "lsth")) {
      Path file = text().resolve(handbook + "-" + year.value() + ".md");
      if (Files.isRegularFile(file)) {
        files.add(file);
      }
    }
    if (files.isEmpty()) {
      throw new NotFound("Die Rechtsbibliothek hat keinen Text für " + year.value() + ".");
    }
    List<String> hits = new ArrayList<>();
    int more = 0;
    for (Path file : files) {
      String place = text().relativize(file).toString();
      String heading = "";
      for (String line : lines(file)) {
        if (line.startsWith("#") || line.startsWith("Source: ")) {
          heading = line.replaceFirst("^#+ ", "");
        }
        if (!line.toLowerCase(Locale.GERMAN).contains(wanted)) {
          continue;
        }
        if (hits.size() == MAX_HITS) {
          more++;
          continue;
        }
        hits.add(place + " | " + heading + " | " + shorten(line.strip()));
      }
    }
    if (hits.isEmpty()) {
      return "Kein Treffer für \"" + query.strip() + "\" im Recht von " + year.value() + ".";
    }
    StringBuilder answer = new StringBuilder(String.join("\n", hits)).append("\n");
    if (more > 0) {
      answer.append("Dazu ").append(more).append(" weitere Treffer. Suche genauer.\n");
    }
    return answer.toString();
  }

  /**
   * One provision in each version of the year, with the Stand and Hinweis lines of each version.
   * For the EStG, the paragraphs of § 52 EStG that name the provision follow, with § 52 Absatz 1.
   */
  String provision(TaxYear year, String law, String provision) throws NotFound {
    if (law == null || !LAW_NAME.matcher(law).matches()) {
      throw new NotFound("Gib das Gesetz klein geschrieben an, wie estg oder ao_1977.");
    }
    if (provision == null || provision.isBlank()) {
      throw new NotFound("Gib die Vorschrift an, wie \"§ 35a\".");
    }
    String wanted = provision.strip();
    List<Path> files = new ArrayList<>();
    for (Path snapshot : snapshots(year)) {
      Path file = snapshot.resolve(law + ".md");
      if (Files.isRegularFile(file)) {
        files.add(file);
      }
    }
    if (files.isEmpty()) {
      throw new NotFound("Die Rechtsbibliothek hat keinen Text von " + law + " für " + year.value() + ".");
    }
    StringBuilder answer = new StringBuilder();
    boolean found = false;
    String previous = null;
    for (Path file : files) {
      List<String> lines = lines(file);
      String text = section(lines, wanted);
      answer.append("--- Fassung ").append(snapshot(file)).append("\n");
      for (String line : lines) {
        if (line.startsWith("## ")) {
          break;
        }
        if (line.startsWith("Stand:") || line.startsWith("Hinweis:")) {
          answer.append(line).append("\n");
        }
      }
      if (text.isEmpty()) {
        answer.append(wanted).append(" steht nicht in dieser Fassung.\n");
      } else if (text.equals(previous)) {
        answer.append("Derselbe Text wie in der Fassung davor.\n");
        found = true;
      } else {
        answer.append(text);
        found = true;
      }
      previous = text;
    }
    if (!found) {
      throw new NotFound(wanted + " steht in keiner Fassung von " + law + " für " + year.value() + ".");
    }
    if (law.equals("estg") && !wanted.equals("§ 52")) {
      answer.append(applicationRule(files, wanted));
    }
    return answer.toString();
  }

  /** A guideline or a note of the handbooks of the year, such as "R 9.13 LStR". */
  String handbook(TaxYear year, String reference) throws NotFound {
    var matcher = HANDBOOK_REFERENCE.matcher(reference == null ? "" : reference.strip());
    if (!matcher.matches()) {
      throw new NotFound("Gib die Stelle wie \"R 9.13 LStR\" oder \"H 35a EStH\" an.");
    }
    String id = matcher.group(1);
    String handbook = matcher.group(2).startsWith("ESt") ? "esth" : "lsth";
    Path file = text().resolve(handbook + "-" + year.value() + ".md");
    if (!Files.isRegularFile(file)) {
      throw new NotFound(
          "Es gibt keinen Text von " + handbook + " für " + year.value() + ". Diese Ausgabe ist eine Lücke.");
    }
    StringBuilder answer = new StringBuilder();
    String page = "";
    boolean on = false;
    for (String line : lines(file)) {
      if (line.startsWith("Source: ")) {
        page = line;
        on = false;
        continue;
      }
      String heading = null;
      if (HANDBOOK_ID_LINE.matcher(line).matches()) {
        heading = line;
      } else if (line.matches("#+ .*")) {
        heading = line.replaceFirst("^#+ ", "");
      }
      if (heading != null) {
        if (startsWithId(heading, id)) {
          on = true;
          answer.append(page).append("\n").append(line).append("\n");
          continue;
        }
        if (heading == line || heading.matches("[RH] [0-9].*") || heading.startsWith("§")) {
          on = false;
        }
      }
      if (on) {
        answer.append(line).append("\n");
      }
    }
    if (answer.isEmpty()) {
      throw new NotFound(reference.strip() + " steht nicht in " + handbook + "-" + year.value() + ".");
    }
    return answer.toString();
  }

  /**
   * A BMF letter or a form instruction from {@code law/guidance.tsv}, for the year only. A letter is
   * read from the handbook. A form instruction is the PDF in {@code law/guidance/}.
   */
  Reading guidance(TaxYear year, String id) throws NotFound {
    String wanted = id == null ? "" : id.strip();
    List<String[]> entries = guidanceEntries(year);
    for (String[] entry : entries) {
      if (entry[0].equals(wanted)) {
        return read(entry);
      }
    }
    StringBuilder list =
        new StringBuilder("Diesen Eintrag gibt es für ").append(year.value()).append(" nicht. Es gibt:\n");
    for (String[] entry : entries) {
      list.append(entry[0]).append(": ").append(entry[5]).append("\n");
    }
    throw new NotFound(list.toString());
  }

  private Reading read(String[] entry) throws NotFound {
    String id = entry[0];
    String header =
        entry[5] + "\nDatum: " + entry[3] + ". Aktenzeichen: " + entry[4] + ". Quelle: " + entry[6]
            + ". Jahr: " + entry[2] + ".\nURL: " + entry[7] + "\n";
    return switch (entry[8]) {
      case "handbook" -> new Text(header + "---\n" + annexPages(entry[7]));
      case "browser" -> {
        Path file = root.resolve("guidance").resolve(id + ".pdf");
        if (!GUIDANCE_ID.matcher(id).matches() || !Files.isRegularFile(file)) {
          throw new NotFound("Die Datei von " + id + " fehlt in der Rechtsbibliothek.");
        }
        yield new Pdf(header, id + ".pdf", file);
      }
      default -> throw new NotFound("Keine amtliche Quelle druckt " + id + " bisher. " + entry[6]);
    };
  }

  /** The pages of the handbook in the folder of the annex page of a letter. */
  private String annexPages(String url) throws NotFound {
    String path = url.replaceFirst("^https://[^/]+/", "");
    String[] parts = path.split("/");
    if (parts.length < 3) {
      throw new NotFound("Die URL " + url + " nennt kein Handbuch.");
    }
    Path file = text().resolve(parts[0] + "-" + parts[1] + ".md");
    if (!GUIDANCE_ID.matcher(parts[0] + "-" + parts[1]).matches() || !Files.isRegularFile(file)) {
      throw new NotFound("Der Text des Handbuchs " + parts[0] + "-" + parts[1] + " fehlt.");
    }
    String folder = url.substring(0, url.lastIndexOf('/') + 1);
    StringBuilder pages = new StringBuilder();
    boolean on = false;
    for (String line : lines(file)) {
      if (line.startsWith("Source: ")) {
        on = line.substring("Source: ".length()).startsWith(folder);
      }
      if (on) {
        pages.append(line).append("\n");
      }
    }
    if (pages.isEmpty()) {
      throw new NotFound("Das Handbuch " + parts[0] + "-" + parts[1] + " druckt " + url + " nicht.");
    }
    return pages.toString();
  }

  private List<String[]> guidanceEntries(TaxYear year) throws NotFound {
    Path file = root.resolve("guidance.tsv");
    if (!Files.isRegularFile(file)) {
      throw new NotFound("Die Liste der BMF-Schreiben und Anleitungen fehlt.");
    }
    List<String[]> entries = new ArrayList<>();
    for (String line : lines(file)) {
      if (line.isBlank() || line.startsWith("#")) {
        continue;
      }
      String[] fields = line.split("\t", -1);
      if (fields.length >= 9 && fields[2].equals(String.valueOf(year.value()))) {
        entries.add(fields);
      }
    }
    return entries;
  }

  /**
   * § 52 Absatz 1 and each paragraph of § 52 that names the provision, in each version. A
   * paragraph runs from its number, such as "(34)", to the next number, so that its lists stay
   * whole. A paragraph names the provision also in a list such as "§§ 33 und 33b".
   */
  private static String applicationRule(List<Path> files, String provision) {
    String number = Pattern.quote(provision.replaceFirst("^§\\s*", ""));
    Pattern names =
        Pattern.compile(
            "(" + Pattern.quote(provision) + "|§§ [^§]{0,80}?(?<![0-9a-z])" + number + ")(?![0-9a-z])");
    StringBuilder answer = new StringBuilder();
    String previous = null;
    for (Path file : files) {
      StringBuilder rule = new StringBuilder();
      for (String paragraph : paragraphs(section(lines(file), "§ 52"))) {
        if (paragraph.startsWith("(1) ") || names.matcher(paragraph).find()) {
          rule.append(paragraph);
        }
      }
      answer.append("--- § 52 EStG, Fassung ").append(snapshot(file)).append("\n");
      if (rule.toString().equals(previous)) {
        answer.append("Derselbe Text wie in der Fassung davor.\n");
      } else {
        answer.append(rule);
      }
      previous = rule.toString();
    }
    return answer.toString();
  }

  /** The numbered paragraphs of a provision, each with the lines that follow its number. */
  private static List<String> paragraphs(String section) {
    List<StringBuilder> paragraphs = new ArrayList<>();
    for (String line : section.lines().toList()) {
      if (PARAGRAPH.matcher(line).lookingAt()) {
        paragraphs.add(new StringBuilder());
      }
      if (!paragraphs.isEmpty()) {
        paragraphs.getLast().append(line).append("\n");
      }
    }
    return paragraphs.stream().map(StringBuilder::toString).toList();
  }

  /** The lines from the heading of the provision to the next heading, as scripts/law.sh does. */
  private static String section(List<String> lines, String provision) {
    String want = "## " + provision;
    StringBuilder text = new StringBuilder();
    boolean on = false;
    for (String line : lines) {
      if (line.startsWith("#")) {
        on = line.equals(want) || line.startsWith(want + " ");
      }
      if (on) {
        text.append(line).append("\n");
      }
    }
    return text.toString();
  }

  private static boolean startsWithId(String line, String id) {
    if (!line.startsWith(id)) {
      return false;
    }
    String rest = line.substring(id.length());
    return rest.isEmpty() || rest.matches("[ .:].*") && !rest.matches("\\.[0-9].*");
  }

  /** The version folders of the year, such as law/text/2022-06-30, from old to new. */
  private List<Path> snapshots(TaxYear year) {
    List<Path> snapshots = new ArrayList<>();
    if (!Files.isDirectory(text())) {
      return snapshots;
    }
    try (DirectoryStream<Path> folders = Files.newDirectoryStream(text(), year.value() + "-*")) {
      folders.forEach(
          folder -> {
            if (Files.isDirectory(folder)) {
              snapshots.add(folder);
            }
          });
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    snapshots.sort(null);
    return snapshots;
  }

  private static List<Path> lawFiles(Path snapshot) {
    try (var files = Files.list(snapshot)) {
      return files.filter(file -> file.toString().endsWith(".md")).sorted().toList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private Path text() {
    return root.resolve("text");
  }

  private static String snapshot(Path file) {
    return Optional.ofNullable(file.getParent()).map(Path::getFileName).map(Path::toString).orElse("");
  }

  private static String shorten(String line) {
    return line.length() <= MAX_LINE ? line : line.substring(0, MAX_LINE) + " …";
  }

  private static List<String> lines(Path file) {
    try {
      return Files.readAllLines(file, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
