import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds the official tax handbooks of the BMF (EStH with EStR, LStH with LStR) in law/text.
 *
 * <p>Usage, from the repository root:
 *
 * <pre>
 *   java scripts/FetchHandbooks.java          download the pinned pages in law/handbooks.tsv
 *   java scripts/FetchHandbooks.java --pin    crawl each edition in law/handbooks.txt, then pin
 *   java scripts/FetchHandbooks.java --convert page.html url   print the text and links of a page
 * </pre>
 *
 * <p>Each edition is HTML with one page per paragraph. The pin mode starts at home.html of an
 * edition and follows every link inside that edition, so it also finds the annexes. It writes one
 * row per page with the SHA-256 value of the page text. The pin is on the text and not on the raw
 * HTML, so that a change in the page frame does not break the check, but a change in the wording
 * does. An edition that does not answer is a gap: the pin file lists it, and no text is written.
 *
 * <p>Downloaded HTML is untrusted data. The script only reads it as text.
 */
public class FetchHandbooks {

  private static final Path EDITIONS = Path.of("law/handbooks.txt");
  private static final Path PINS = Path.of("law/handbooks.tsv");
  private static final Path TEXT = Path.of("law/text");
  private static final String GAP = "gap";
  private static final String USER_AGENT =
      "Mozilla/5.0 (X11; Linux x86_64; rv:140.0) Gecko/20100101 Firefox/140.0";

  private static final HttpClient HTTP =
      HttpClient.newBuilder()
          .followRedirects(HttpClient.Redirect.NORMAL)
          .connectTimeout(Duration.ofSeconds(20))
          .build();

  record Edition(String handbook, int year) {
    String host() {
      return "https://" + handbook + ".bundesfinanzministerium.de";
    }

    String prefix() {
      return "/" + handbook + "/" + year + "/";
    }

    String home() {
      return host() + prefix() + "home.html";
    }

    String title() {
      return handbook.equals("esth") ? "Amtliches Einkommensteuer-Handbuch" : "Amtliches Lohnsteuer-Handbuch";
    }
  }

  record Pin(Edition edition, String path, String sha256) {}

  record Page(String text, List<String> links) {}

  public static void main(String[] args) throws Exception {
    if (args.length == 3 && args[0].equals("--convert")) {
      Page page = convert(Files.readString(Path.of(args[1])), URI.create(args[2]));
      System.out.println(page.text());
      page.links().forEach(link -> System.out.println("Link: " + link));
      return;
    }
    if (args.length == 1 && args[0].equals("--pin")) {
      pin();
    } else if (args.length != 0) {
      throw new IllegalArgumentException(
          "Usage: java scripts/FetchHandbooks.java [--pin | --convert page.html url]");
    }
    build();
  }

  // ---- Pin mode: crawl each edition and write law/handbooks.tsv ----

  private static void pin() throws Exception {
    List<String> rows = new ArrayList<>();
    for (Edition edition : editions()) {
      Map<String, String> pages = crawl(edition);
      if (pages.isEmpty()) {
        System.out.println("Gap: no " + edition.handbook() + " edition for " + edition.year());
        rows.add(String.join("\t", edition.handbook(), String.valueOf(edition.year()), GAP, "-"));
        continue;
      }
      for (var page : pages.entrySet()) {
        rows.add(String.join(
            "\t", edition.handbook(), String.valueOf(edition.year()), page.getKey(), sha256(page.getValue())));
      }
      System.out.println("Pinned " + pages.size() + " pages of " + edition.handbook() + " " + edition.year());
    }
    Files.write(PINS, rows);
  }

  /** Returns the text of each page of an edition in reading order, or nothing if it has no home page. */
  private static Map<String, String> crawl(Edition edition) throws Exception {
    Map<String, String> order = new LinkedHashMap<>();
    if (!exists(edition)) {
      return order;
    }
    Set<String> seen = new HashSet<>();
    Deque<String> stack = new ArrayDeque<>();
    stack.push(edition.prefix() + "home.html");
    while (!stack.isEmpty()) {
      String path = stack.pop();
      if (!seen.add(path)) {
        continue;
      }
      HttpResponse<byte[]> response = get(edition.host() + path);
      if (response.statusCode() != 200) {
        System.out.println("Skipped HTTP " + response.statusCode() + " for " + path);
        continue;
      }
      Page page = convert(decode(response), URI.create(edition.host() + path));
      order.put(path, page.text());
      List<String> next = page.links().stream()
          .filter(link -> link.startsWith(edition.prefix()) && !seen.contains(link))
          .toList();
      for (int i = next.size() - 1; i >= 0; i--) {
        stack.push(next.get(i));
      }
    }
    return order;
  }

  /**
   * An edition exists if its home page answers without a redirect to another place and links to
   * other pages of the same edition. A start page or an error page in place of a missing edition
   * fails this check, so the edition is listed as a gap.
   */
  private static boolean exists(Edition edition) throws Exception {
    HttpResponse<byte[]> home = get(edition.home());
    if (home.statusCode() != 200 || !home.uri().getPath().equals(edition.prefix() + "home.html")) {
      return false;
    }
    return convert(decode(home), home.uri()).links().stream()
        .anyMatch(link -> link.startsWith(edition.prefix()) && !link.endsWith("/home.html"));
  }

  // ---- Check mode: download every pinned page and write one text file per edition ----

  private static void build() throws Exception {
    Map<Edition, List<Pin>> byEdition = new LinkedHashMap<>();
    List<Edition> gaps = new ArrayList<>();
    for (String line : Files.readAllLines(PINS)) {
      if (line.isBlank() || line.startsWith("#")) {
        continue;
      }
      String[] cells = line.split("\t");
      Edition edition = new Edition(cells[0], Integer.parseInt(cells[1]));
      if (cells[2].equals(GAP)) {
        gaps.add(edition);
      } else {
        byEdition.computeIfAbsent(edition, e -> new ArrayList<>()).add(new Pin(edition, cells[2], cells[3]));
      }
    }
    int written = 0;
    for (var entry : byEdition.entrySet()) {
      if (write(entry.getKey(), entry.getValue())) {
        written++;
      }
    }
    for (Edition gap : gaps) {
      System.out.println("Gap: no " + gap.handbook() + " edition for " + gap.year());
    }
    System.out.println("Wrote " + written + " handbook files to " + TEXT);
  }

  private static boolean write(Edition edition, List<Pin> pins) throws Exception {
    Path target = TEXT.resolve(edition.handbook() + "-" + edition.year() + ".md");
    StringBuilder all = new StringBuilder();
    pins.forEach(pin -> all.append(pin.path()).append(pin.sha256()).append('\n'));
    String pinHash = sha256(all.toString());
    if (Files.exists(target) && Files.readString(target).contains("Pins-SHA-256: " + pinHash)) {
      return false;
    }
    List<String> texts = fetchAll(pins);
    StringBuilder out = new StringBuilder();
    out.append("# ").append(edition.title()).append(" ").append(edition.year()).append("\n\n");
    out.append("Source: ").append(edition.home()).append("\n");
    out.append("Pins-SHA-256: ").append(pinHash).append("\n\n");
    for (int i = 0; i < pins.size(); i++) {
      out.append("Source: ").append(edition.host()).append(pins.get(i).path()).append("\n\n");
      out.append(texts.get(i)).append("\n\n");
    }
    Files.createDirectories(TEXT);
    Files.writeString(target, out.toString());
    return true;
  }

  private static List<String> fetchAll(List<Pin> pins) throws Exception {
    try (ExecutorService pool = Executors.newFixedThreadPool(4)) {
      List<Future<String>> futures = new ArrayList<>();
      for (Pin pin : pins) {
        futures.add(pool.submit(() -> checkedText(pin)));
      }
      List<String> texts = new ArrayList<>();
      for (Future<String> future : futures) {
        texts.add(future.get());
      }
      return texts;
    }
  }

  private static String checkedText(Pin pin) throws Exception {
    String url = pin.edition().host() + pin.path();
    String text = fetchText(url);
    if (!sha256(text).equals(pin.sha256())) {
      throw new IllegalStateException("SHA-256 of the text does not match for " + url);
    }
    return text;
  }

  // ---- HTML to text ----

  private static final Pattern DROP = Pattern.compile(
      "(?is)<!--.*?-->|<![^>]*>|<\\?[^>]*>|<(script|style|noscript|head|nav|header|footer|form)\\b.*?</\\1\\s*>");
  private static final Pattern MAIN = Pattern.compile("(?is)<main\\b[^>]*>(.*)</main\\s*>");
  private static final Pattern TAG = Pattern.compile("(?s)<(/?)([a-zA-Z][a-zA-Z0-9]*)([^>]*)>");
  private static final Pattern HREF = Pattern.compile("(?is)<a\\b[^>]*?\\bhref\\s*=\\s*[\"']([^\"']+)[\"']");
  private static final Pattern ENTITY = Pattern.compile("&(#x[0-9a-fA-F]+|#[0-9]+|[a-zA-Z]+);");
  private static final Set<String> BLOCKS = Set.of(
      "p", "div", "br", "tr", "table", "ul", "ol", "section", "article", "dl", "dd", "dt",
      "blockquote", "aside", "figure", "caption");
  private static final Map<String, String> ENTITIES = Map.ofEntries(
      Map.entry("amp", "&"), Map.entry("lt", "<"), Map.entry("gt", ">"), Map.entry("quot", "\""),
      Map.entry("apos", "'"), Map.entry("nbsp", " "), Map.entry("shy", ""),
      Map.entry("auml", "ä"), Map.entry("ouml", "ö"), Map.entry("uuml", "ü"),
      Map.entry("Auml", "Ä"), Map.entry("Ouml", "Ö"), Map.entry("Uuml", "Ü"), Map.entry("szlig", "ß"),
      Map.entry("sect", "§"), Map.entry("euro", "€"), Map.entry("ndash", "–"), Map.entry("mdash", "—"),
      Map.entry("bdquo", "„"), Map.entry("ldquo", "“"), Map.entry("rdquo", "”"),
      Map.entry("sbquo", "‚"), Map.entry("lsquo", "‘"), Map.entry("rsquo", "’"),
      Map.entry("hellip", "…"), Map.entry("bull", "•"), Map.entry("middot", "·"));

  /** Converts one handbook page to text and lists its links as absolute paths, in page order. */
  static Page convert(String html, URI url) {
    List<String> links = new ArrayList<>();
    Matcher href = HREF.matcher(html);
    while (href.find()) {
      String link = resolve(url, decodeEntities(href.group(1)));
      if (link != null && !links.contains(link)) {
        links.add(link);
      }
    }
    String body = DROP.matcher(html).replaceAll(" ");
    Matcher main = MAIN.matcher(body);
    if (main.find()) {
      body = main.group(1);
    }
    return new Page(tidy(render(body)), links);
  }

  private static String render(String html) {
    StringBuilder out = new StringBuilder();
    Matcher tag = TAG.matcher(html);
    int at = 0;
    while (tag.find()) {
      out.append(decodeEntities(html.substring(at, tag.start())).replaceAll("\\s+", " "));
      at = tag.end();
      String name = tag.group(2).toLowerCase();
      boolean closing = !tag.group(1).isEmpty();
      if (name.matches("h[1-6]")) {
        int level = Math.min(6, name.charAt(1) - '0' + 1);
        out.append(closing ? "\n" : "\n" + "#".repeat(level) + " ");
      } else if (name.equals("li")) {
        out.append(closing ? "\n" : "\n- ");
      } else if ((name.equals("td") || name.equals("th")) && closing) {
        out.append(" | ");
      } else if (BLOCKS.contains(name)) {
        out.append("\n");
      }
    }
    out.append(decodeEntities(html.substring(at)).replaceAll("\\s+", " "));
    return out.toString();
  }

  private static String tidy(String text) {
    List<String> lines = new ArrayList<>();
    for (String line : text.split("\n")) {
      String clean = line.replace('\u00a0', ' ').replaceAll(" {2,}", " ").strip();
      if (!clean.isEmpty() && !clean.matches("#+") && !clean.equals("-")) {
        lines.add(clean);
      }
    }
    return String.join("\n", lines);
  }

  private static String resolve(URI page, String href) {
    if (href.startsWith("javascript:") || href.startsWith("mailto:") || href.startsWith("#")) {
      return null;
    }
    try {
      URI target = page.resolve(href.replace(" ", "%20"));
      if (!page.getHost().equals(target.getHost())) {
        return null;
      }
      String path = target.normalize().getRawPath();
      return path != null && path.endsWith(".html") ? path : null;
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private static String decodeEntities(String text) {
    Matcher m = ENTITY.matcher(text);
    StringBuilder out = new StringBuilder();
    while (m.find()) {
      String name = m.group(1);
      String value;
      if (name.startsWith("#x")) {
        value = Character.toString(Integer.parseInt(name.substring(2), 16));
      } else if (name.startsWith("#")) {
        value = Character.toString(Integer.parseInt(name.substring(1)));
      } else {
        value = ENTITIES.getOrDefault(name, m.group());
      }
      m.appendReplacement(out, Matcher.quoteReplacement(value));
    }
    m.appendTail(out);
    return out.toString();
  }

  // ---- Helpers ----

  private static List<Edition> editions() throws Exception {
    List<Edition> editions = new ArrayList<>();
    for (String line : Files.readAllLines(EDITIONS)) {
      String clean = line.strip();
      if (clean.isEmpty() || clean.startsWith("#")) {
        continue;
      }
      String[] cells = clean.split("\\s+");
      editions.add(new Edition(cells[0], Integer.parseInt(cells[1])));
    }
    return editions;
  }

  private static String fetchText(String url) throws Exception {
    HttpResponse<byte[]> response = get(url);
    if (response.statusCode() != 200) {
      throw new IllegalStateException("HTTP " + response.statusCode() + " for " + url);
    }
    return convert(decode(response), URI.create(url)).text();
  }

  private static String decode(HttpResponse<byte[]> response) {
    String type = response.headers().firstValue("Content-Type").orElse("").toLowerCase();
    boolean latin = type.contains("iso-8859-1") || type.contains("windows-1252");
    return new String(response.body(), latin ? StandardCharsets.ISO_8859_1 : StandardCharsets.UTF_8);
  }

  private static HttpResponse<byte[]> get(String url) throws Exception {
    HttpRequest request = HttpRequest.newBuilder(URI.create(url))
        .header("User-Agent", USER_AGENT)
        .timeout(Duration.ofSeconds(60))
        .build();
    return HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
  }

  private static String sha256(String text) throws Exception {
    return HexFormat.of().formatHex(
        MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
  }
}
