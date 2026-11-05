import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Builds the local law library in law/text from the daily archive of gesetze-im-internet.de.
 *
 * <p>Usage, from the repository root:
 *
 * <pre>
 *   java scripts/FetchLaws.java         download the pinned versions in law/sources.tsv
 *   java scripts/FetchLaws.java --pin   pin law/laws.txt and law/snapshots.txt, then download
 *   java scripts/FetchLaws.java --rebuild   download and convert every file again
 * </pre>
 *
 * <p>Downloaded XML is untrusted data. The parser loads no DTD and no external entity, and the
 * download must match the SHA-256 value in law/sources.tsv.
 */
public class FetchLaws {

  private static final String REPOSITORY = "QuantLaw/gesetze-im-internet";
  private static final Path LAWS = Path.of("law/laws.txt");
  private static final Path SNAPSHOTS = Path.of("law/snapshots.txt");
  private static final Path SOURCES = Path.of("law/sources.tsv");
  private static final Path TEXT = Path.of("law/text");
  private static final DateTimeFormatter GERMAN_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
  private static final Pattern SHA = Pattern.compile("\"sha\":\\s*\"([0-9a-f]{40})\"");
  private static final Pattern XML_NAME = Pattern.compile("\"name\":\\s*\"([^\"]+\\.xml)\"");

  private static boolean REBUILD = false;

  private static final HttpClient HTTP =
      HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();

  record Source(String date, String law, String commit, String file, String sha256) {}

  public static void main(String[] args) throws Exception {
    if (args.length == 1 && args[0].equals("--pin")) {
      pin();
    } else if (args.length == 1 && args[0].equals("--rebuild")) {
      REBUILD = true;
    } else if (args.length != 0) {
      throw new IllegalArgumentException("Usage: java scripts/FetchLaws.java [--pin | --rebuild]");
    }
    int written = 0;
    for (String line : Files.readAllLines(SOURCES)) {
      String[] cells = line.split("\t");
      if (build(new Source(cells[0], cells[1], cells[2], cells[3], cells[4]))) {
        written++;
      }
    }
    System.out.println("Wrote " + written + " files to " + TEXT);
  }

  private static void pin() throws Exception {
    List<String> laws = entries(LAWS);
    Map<String, String> files = new HashMap<>();
    List<String> rows = new ArrayList<>();
    for (String date : entries(SNAPSHOTS)) {
      String commit = commitAt(date);
      for (String law : laws) {
        String file = files.get(law);
        if (file == null) {
          file = xmlFile(commit, law);
          files.put(law, file);
        }
        HttpResponse<byte[]> response = get(rawUrl(commit, law, file));
        if (response.statusCode() == 404) {
          file = xmlFile(commit, law);
          files.put(law, file);
          response = get(rawUrl(commit, law, file));
        }
        rows.add(
            String.join(
                "\t", date, law, commit, file, sha256(checked(response, rawUrl(commit, law, file)))));
      }
      System.out.println("Pinned " + date + " to " + commit);
    }
    Files.write(SOURCES, rows);
  }

  private static boolean build(Source source) throws Exception {
    Path target = TEXT.resolve(source.date()).resolve(source.law() + ".md");
    if (!REBUILD
        && Files.exists(target)
        && Files.readString(target).contains("SHA-256: " + source.sha256())) {
      return false;
    }
    String url = rawUrl(source.commit(), source.law(), source.file());
    byte[] xml = checked(get(url), url);
    if (!sha256(xml).equals(source.sha256())) {
      throw new IllegalStateException("SHA-256 does not match for " + url);
    }
    Files.createDirectories(target.getParent());
    Files.writeString(target, toMarkdown(xml, source, url));
    return true;
  }

  private static String toMarkdown(byte[] xml, Source source, String url) throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
    factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
    factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
    factory.setExpandEntityReferences(false);
    Element root =
        factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml)).getDocumentElement();

    StringBuilder out = new StringBuilder();
    boolean first = true;
    for (Element norm : children(root, "norm")) {
      Element meta = child(norm, "metadaten");
      if (first) {
        first = false;
        out.append("# ")
            .append(text(meta, "jurabk"))
            .append(": ")
            .append(text(meta, "langue"))
            .append("\n\n");
        out.append("Snapshot: ")
            .append(LocalDate.parse(source.date()).format(GERMAN_DATE))
            .append("\n");
        out.append("Source: ").append(url).append("\n");
        out.append("SHA-256: ").append(source.sha256()).append("\n");
        for (Element stand : children(meta, "standangabe")) {
          out.append(text(stand, "standtyp"))
              .append(": ")
              .append(text(stand, "standkommentar"))
              .append("\n");
        }
        out.append("\n");
      }
      heading(meta, out);
      Element textData = child(norm, "textdaten");
      if (textData != null) {
        for (String part : List.of("text", "fussnoten")) {
          Element holder = child(textData, part);
          if (holder != null) {
            StringBuilder body = new StringBuilder();
            render(holder, body, 0);
            String clean = tidy(body.toString());
            if (!clean.isEmpty()) {
              out.append(part.equals("fussnoten") ? "Fußnote:\n" : "").append(clean).append("\n\n");
            }
          }
        }
      }
    }
    return out.toString();
  }

  private static void heading(Element meta, StringBuilder out) {
    String number = text(meta, "enbez");
    String title = text(meta, "titel");
    Element section = child(meta, "gliederungseinheit");
    if (!number.isEmpty()) {
      out.append("## ").append(number);
      if (!title.isEmpty()) {
        out.append(" ").append(title);
      }
      out.append("\n\n");
    } else if (section != null) {
      out.append("# ")
          .append((text(section, "gliederungsbez") + " " + text(section, "gliederungstitel")).strip())
          .append("\n\n");
    } else if (!title.isEmpty()) {
      out.append("## ").append(title).append("\n\n");
    }
  }

  private static void render(Node node, StringBuilder out, int depth) {
    for (Node n = node.getFirstChild(); n != null; n = n.getNextSibling()) {
      if (n.getNodeType() == Node.TEXT_NODE || n.getNodeType() == Node.CDATA_SECTION_NODE) {
        out.append(n.getNodeValue().replace('\u00a0', ' ').replaceAll("\\s+", " "));
      } else if (n instanceof Element e) {
        switch (e.getTagName()) {
          case "SUP" -> {
            String value = e.getTextContent().replace('\u00a0', ' ').strip();
            out.append(e.getAttribute("class").equals("Rec") ? "[" + value + "] " : "^" + value);
          }
          case "BR" -> out.append("\n").append("  ".repeat(depth));
          case "P", "Title", "Subtitle", "row", "Revision" -> {
            out.append("\n").append("  ".repeat(depth));
            render(e, out, depth);
            out.append("\n");
          }
          case "LA" -> {
            out.append(" ");
            render(e, out, depth);
          }
          case "DL" -> render(e, out, depth + 1);
          case "DT" -> {
            out.append("\n").append("  ".repeat(depth));
            render(e, out, depth);
            out.append(" ");
          }
          case "entry" -> {
            render(e, out, depth);
            out.append(" | ");
          }
          case "IMG" -> out.append("[image: ").append(e.getAttribute("SRC")).append("]");
          default -> render(e, out, depth);
        }
      }
    }
  }

  private static String tidy(String text) {
    List<String> lines = new ArrayList<>();
    for (String line : text.split("\n")) {
      String clean = line.replaceAll("(?<=\\S) {2,}", " ").stripTrailing();
      if (!clean.isBlank()) {
        lines.add(clean);
      }
    }
    return String.join("\n", lines);
  }

  private static List<String> entries(Path file) throws IOException {
    return Files.readAllLines(file).stream()
        .map(String::strip)
        .filter(line -> !line.isEmpty() && !line.startsWith("#"))
        .toList();
  }

  private static String commitAt(String date) throws Exception {
    String url =
        "https://api.github.com/repos/" + REPOSITORY + "/commits?sha=data&per_page=1&until=" + date
            + "T23:59:59Z";
    var match = SHA.matcher(new String(checked(get(url), url)));
    if (!match.find()) {
      throw new IllegalStateException("No commit for " + date);
    }
    return match.group(1);
  }

  private static String xmlFile(String commit, String law) throws Exception {
    String url =
        "https://api.github.com/repos/" + REPOSITORY + "/contents/data/items/" + law + "?ref=" + commit;
    var match = XML_NAME.matcher(new String(checked(get(url), url)));
    if (!match.find()) {
      throw new IllegalStateException("No XML file for " + law + " at " + commit);
    }
    return match.group(1);
  }

  private static String rawUrl(String commit, String law, String file) {
    return "https://raw.githubusercontent.com/" + REPOSITORY + "/" + commit + "/data/items/" + law
        + "/" + file;
  }

  private static HttpResponse<byte[]> get(String url) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(url)).header("User-Agent", "makeMyTaxes-law-library").build();
    return HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
  }

  private static byte[] checked(HttpResponse<byte[]> response, String url) {
    if (response.statusCode() != 200) {
      throw new IllegalStateException("HTTP " + response.statusCode() + " for " + url);
    }
    return response.body();
  }

  private static String sha256(byte[] bytes) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
  }

  private static List<Element> children(Element parent, String name) {
    List<Element> found = new ArrayList<>();
    for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
      if (n instanceof Element e && e.getTagName().equals(name)) {
        found.add(e);
      }
    }
    return found;
  }

  private static Element child(Element parent, String name) {
    List<Element> found = children(parent, name);
    return found.isEmpty() ? null : found.get(0);
  }

  private static String text(Element parent, String name) {
    Element found = child(parent, name);
    return found == null ? "" : found.getTextContent().replace('\u00a0', ' ').replaceAll("\\s+", " ").strip();
  }
}
