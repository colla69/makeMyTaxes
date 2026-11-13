package io.github.colla69.makemytaxes.app.assistant;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.colla69.makemytaxes.app.storage.HouseholdStore;
import io.github.colla69.makemytaxes.app.storage.HouseholdStore.StoredPerson;
import io.github.colla69.makemytaxes.core.TaxYear;
import io.github.colla69.makemytaxes.core.form.Answers;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The law tools of the MCP server, with the fake assistant in place of Claude Code and a made-up
 * law library. The texts imitate the layout of the real library, but their wording is made up.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(McpLawToolTest.Fake.class)
class McpLawToolTest {

  @TestConfiguration
  static class Fake {
    @Bean
    @Primary
    FakeAssistant fakeAssistant() {
      return new FakeAssistant();
    }
  }

  private static final Pattern FILE = Pattern.compile("liegt unter (\\S+)\\. Lies");
  private static final byte[] PDF =
      "%PDF-1.4 made-up instructions".getBytes(StandardCharsets.UTF_8);

  @TempDir static Path databaseFolder;
  @TempDir static Path law;

  @LocalServerPort private int port;

  @Autowired private HouseholdStore household;

  @Autowired private PersonAssistant personAssistant;

  @Autowired private Assistant assistant;

  @Autowired private ReturnChat chat;

  @Autowired private QuestionTokens tokens;

  private final HttpClient client = HttpClient.newHttpClient();

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("makemytaxes.database.path", () -> databaseFolder.resolve("law.db").toString());
    registry.add("makemytaxes.database.passphrase", () -> "made-up-test-passphrase");
    registry.add("makemytaxes.law.path", () -> law.toString());
  }

  @BeforeAll
  static void writeMadeUpLibrary() throws IOException {
    estg("2022-06-30", "Mitte 2022", "(1) Made-up Wortlaut Mitte 2022.");
    estg("2022-12-31", "Ende 2022", "(1) Made-up Wortlaut Ende 2022.");
    estg("2023-06-30", "Mitte 2023", "(1) Made-up Wortlaut 2023.");
    write(
        "text/esth-2022.md",
        """
        Source: https://esth.bundesfinanzministerium.de/esth/2022/A-Einkommensteuergesetz/Paragraf-35a/inhalt.html
        # § 35a Made-up Steuerermäßigung
        R 35a.
        ### Richtlinie
        Made-up Richtlinie zur Steuerermäßigung.
        H 35a
        ### Hinweise
        Made-up Hinweis zur Steuerermäßigung.
        Source: https://esth.bundesfinanzministerium.de/esth/2022/C-Anhaenge/Anhang-27b/II/inhalt.html
        # Made-up Anhang 27b
        Made-up Text des Schreibens. BMF vom 09.11.2016
        Source: https://esth.bundesfinanzministerium.de/esth/2022/C-Anhaenge/Anhang-28/inhalt.html
        # Made-up Anhang 28
        Made-up Text eines anderen Anhangs.
        """);
    write(
        "guidance.tsv",
        """
        # Made-up entries
        made-up-letter-2022\tletter\t2022\t09.11.2016\tIV made-up\tMade-up Schreiben\tEStH 2022 Anhang 27b II\thttps://esth.bundesfinanzministerium.de/esth/2022/C-Anhaenge/Anhang-27b/II/inhalt.html\thandbook\t-
        made-up-instructions-2022\tinstructions\t2022\t-\t-\tMade-up Anleitung\tMade-up Quelle\thttps://example.invalid/made-up.pdf\tbrowser\t
        made-up-letter-2023\tletter\t2023\t-\t-\tMade-up Schreiben 2023\tMade-up Lücke\t-\tgap\t-
        """);
    Files.createDirectories(law.resolve("guidance"));
    Files.write(law.resolve("guidance/made-up-instructions-2022.pdf"), PDF);
    write("text/2022-12-31/made_up_long.md", "# Made-up langes Gesetz\n\n" + longText("## § 1 Lang"));
    write(
        "text/esth-2023.md",
        longText(
            "Source: https://esth.bundesfinanzministerium.de/esth/2023/C-Anhaenge/Anhang-99/inhalt.html"));
    Files.writeString(
        law.resolve("guidance.tsv"),
        "made-up-long-letter-2023\tletter\t2023\t-\t-\tMade-up langes Schreiben\tMade-up\t"
            + "https://esth.bundesfinanzministerium.de/esth/2023/C-Anhaenge/Anhang-99/inhalt.html"
            + "\thandbook\t-\n",
        StandardCharsets.UTF_8,
        java.nio.file.StandardOpenOption.APPEND);
  }

  /** A heading and 100 made-up paragraphs of about 1,000 characters, from (1) to (100). */
  private static String longText(String heading) {
    StringBuilder text = new StringBuilder(heading).append("\n");
    for (int i = 1; i <= 100; i++) {
      text.append("(").append(i).append(") ").append("Made-up Fülltext. ".repeat(55)).append("\n");
    }
    return text.toString();
  }

  private static void estg(String snapshot, String stand, String wording) throws IOException {
    write(
        "text/" + snapshot + "/estg.md",
        """
        # EStG: Einkommensteuergesetz

        Snapshot: %s
        Stand: made-up Stand %s
        Hinweis: made-up Hinweis %s

        ## § 35a Made-up Steuerermäßigung

        %s

        ## § 35b Made-up andere Vorschrift

        (1) Made-up Text, nach dem niemand fragt.

        ## § 52 Anwendungsvorschriften

        (1) Made-up allgemeine Anwendung.
        (2) Made-up Regel zu § 3 Nummer 1.
        (34) Made-up Regel: § 35a ist mit der Maßgabe anzuwenden, dass
         1. made-up erste Bedingung und
         2. made-up zweite Bedingung.
        (34a) Made-up Regel: Die §§ 33 und 35a sind erstmals anzuwenden.
        (35) Made-up Regel zu § 35ab.
        (36) Made-up Regel: Die §§ 35ab und 36 sind anzuwenden.
        """
            .formatted(snapshot, stand, stand, wording));
  }

  private static void write(String file, String text) throws IOException {
    Path path = law.resolve(file);
    Files.createDirectories(path.getParent());
    Files.writeString(path, text, StandardCharsets.UTF_8);
  }

  @BeforeEach
  void turnTheAssistantOffForEveryone() {
    for (StoredPerson person : household.persons()) {
      household.switchAssistant(person.id(), false);
    }
    fake().setDuringQuestion(access -> {});
  }

  @Test
  void theToolListNamesTheLawTools() {
    String body = during(2022, access -> call(access, "{\"jsonrpc\":\"2.0\",\"method\":\"tools/list\",\"id\":2}"));

    assertTrue(body.contains("\"name\":\"search_law\""), body);
    assertTrue(body.contains("\"name\":\"read_provision\""), body);
    assertTrue(body.contains("\"name\":\"read_guidance\""), body);
  }

  @Test
  void aProvisionIsReadInEachVersionOfTheYearOfTheReturnWithStandHinweisAndParagraph52() {
    String text = tool(2022, "read_provision", "{\"law\":\"estg\",\"provision\":\"§ 35a\"}");

    assertTrue(text.contains("Fassung 2022-06-30"), text);
    assertTrue(text.contains("Made-up Wortlaut Mitte 2022."), text);
    assertTrue(text.contains("Fassung 2022-12-31"), text);
    assertTrue(text.contains("Made-up Wortlaut Ende 2022."), text);
    assertTrue(text.contains("Stand: made-up Stand Mitte 2022"), text);
    assertTrue(text.contains("Hinweis: made-up Hinweis Ende 2022"), text);
    assertFalse(text.contains("2023"), "a version of another year stays out: " + text);
    assertFalse(text.contains("§ 35b"), text);
    assertTrue(text.contains("--- § 52 EStG, Fassung 2022-06-30"), text);
    assertTrue(text.contains("(1) Made-up allgemeine Anwendung."), text);
    assertTrue(text.contains("(34) Made-up Regel: § 35a ist mit der Maßgabe"), text);
    assertTrue(text.contains(" 2. made-up zweite Bedingung."), "a paragraph stays whole: " + text);
    assertTrue(text.contains("(34a) Made-up Regel: Die §§ 33 und 35a"), text);
    assertFalse(text.contains("(35)"), "§ 35ab is not § 35a: " + text);
    assertFalse(text.contains("(36)"), "§§ 35ab is not § 35a: " + text);
    assertFalse(text.contains("(2) Made-up"), text);
    assertTrue(text.contains("Derselbe Text wie in der Fassung davor."), text);
  }

  @Test
  void aReturnOfAnotherYearReadsTheVersionsOfThatYear() {
    String text = tool(2023, "read_provision", "{\"law\":\"estg\",\"provision\":\"§ 35a\"}");

    assertTrue(text.contains("Made-up Wortlaut 2023."), text);
    assertFalse(text.contains("2022"), text);
  }

  @Test
  void theSearchFindsTheLawAndTheHandbookOfTheYear() {
    String text = tool(2022, "search_law", "{\"query\":\"steuerermäßigung\"}");

    assertTrue(text.contains("2022-12-31/estg.md | § 35a Made-up Steuerermäßigung"), text);
    assertTrue(
        text.contains("esth-2022.md | § 35a Made-up Steuerermäßigung | # § 35a Made-up"), text);
    assertTrue(text.contains("Made-up Richtlinie zur Steuerermäßigung."), text);
    assertFalse(text.contains("2022-06-30"), "only the last version of the year: " + text);
    assertFalse(text.contains("2023"), text);
  }

  @Test
  void aGuidelineIsReadFromTheHandbookOfTheYear() {
    String text = tool(2022, "read_guidance", "{\"reference\":\"R 35a EStR\"}");

    assertTrue(text.contains("Made-up Richtlinie zur Steuerermäßigung."), text);
    assertTrue(text.contains("Source: https://esth.bundesfinanzministerium.de/esth/2022/A-"), text);
    assertFalse(text.contains("Made-up Hinweis"), text);
  }

  @Test
  void aLetterIsReadFromItsAnnexPages() {
    String text = tool(2022, "read_guidance", "{\"reference\":\"made-up-letter-2022\"}");

    assertTrue(text.startsWith("Made-up Schreiben\nDatum: 09.11.2016."), text);
    assertTrue(text.contains("Made-up Text des Schreibens."), text);
    assertFalse(text.contains("anderen Anhangs"), text);
  }

  @Test
  void aFormInstructionIsWrittenIntoTheFolderOfTheQuestion() {
    List<String> checks = new ArrayList<>();
    during(
        2022,
        access -> {
          String text = textOf(call(access, toolCall("read_guidance", "{\"reference\":\"made-up-instructions-2022\"}")));
          Matcher matcher = FILE.matcher(text);
          assertTrue(matcher.find(), text);
          Path file = Path.of(matcher.group(1));
          assertTrue(file.startsWith(access.documents()));
          assertArrayEquals(PDF, Files.readAllBytes(file));
          checks.add("done");
          return text;
        });
    assertEquals(List.of("done"), checks);
  }

  @Test
  void anEntryOfAnotherYearIsNotReadAndTheEntriesOfTheYearAreNamed() {
    String body =
        during(2022, access -> call(access, toolCall("read_guidance", "{\"reference\":\"made-up-letter-2023\"}")));

    assertTrue(body.contains("\"isError\":true"), body);
    assertTrue(body.contains("made-up-letter-2022: Made-up Schreiben"), body);
    assertFalse(body.contains("Made-up Lücke"), body);
  }

  @Test
  void aLawNameWithAPathIsRefused() {
    String body =
        during(2022, access -> call(access, toolCall("read_provision", "{\"law\":\"../estg\",\"provision\":\"§ 35a\"}")));

    assertTrue(body.contains("\"isError\":true"), body);
    assertFalse(body.contains("Made-up Wortlaut"), body);
  }

  @Test
  void oneParagraphOfAProvisionIsRead() {
    String text = tool(2022, "read_provision", "{\"law\":\"estg\",\"provision\":\"§ 52 Abs. 34\"}");

    assertTrue(text.contains("## § 52 Anwendungsvorschriften"), text);
    assertTrue(text.contains("(34) Made-up Regel: § 35a ist mit der Maßgabe"), text);
    assertTrue(text.contains(" 2. made-up zweite Bedingung."), text);
    assertFalse(text.contains("(34a)"), text);
    assertFalse(text.contains("(1) Made-up allgemeine"), text);
  }

  @Test
  void aLongProvisionComesInPartsThatSayHowToReadOn() {
    String first = tool(2022, "read_provision", "{\"law\":\"made_up_long\",\"provision\":\"§ 1\"}");
    String last =
        tool(2022, "read_provision", "{\"law\":\"made_up_long\",\"provision\":\"§ 1\",\"part\":3}");

    assertTrue(first.length() < LawTools.MAX_TEXT + 500, "length " + first.length());
    assertTrue(first.contains("(1) Made-up"), first);
    assertTrue(first.contains("[Teil 1 von 3."), first);
    assertTrue(first.contains("part 2"), first);
    assertTrue(first.contains("§ 52 Abs. 34"), first);
    assertTrue(last.contains("(100) Made-up"), last);
    assertTrue(last.contains("[Teil 3 von 3."), last);
    assertFalse(last.contains("part 4"), last);
  }

  @Test
  void aLongLetterComesInParts() {
    String first = tool(2023, "read_guidance", "{\"reference\":\"made-up-long-letter-2023\"}");
    String body =
        during(
            2023,
            access ->
                call(
                    access,
                    toolCall(
                        "read_guidance",
                        "{\"reference\":\"made-up-long-letter-2023\",\"part\":9}")));

    assertTrue(first.length() < LawTools.MAX_TEXT + 500, "length " + first.length());
    assertTrue(first.contains("[Teil 1 von 3."), first);
    assertTrue(first.contains("part 2"), first);
    assertTrue(body.contains("\"isError\":true"), body);
    assertTrue(body.contains("nur 3 Teile"), body);
  }

  @Test
  void aQuestionThatSeesNoReturnReadsNoYear() throws Exception {
    long returnId = addReturn(2022);
    long personId = household.personOf(returnId).orElseThrow();
    household.switchAssistant(personId, false);

    assertNull(chat.prepare(returnId, "Made-up question").taxYear());

    DocumentAccess access = tokens.open(new DocumentAccess.Question(Set.of(), null));
    try {
      String body = call(access, toolCall("read_provision", "{\"law\":\"estg\",\"provision\":\"§ 35a\"}"));
      assertTrue(body.contains("\"isError\":true"), body);
      assertTrue(body.contains("kein Jahr"), body);
      assertFalse(body.contains("Made-up Wortlaut"), body);
    } finally {
      tokens.close(access);
    }
  }

  /** A step during a question about a made-up return of the year, with the access of the question. */
  @FunctionalInterface
  private interface Step {
    String run(DocumentAccess access) throws Exception;
  }

  private String during(int year, Step step) {
    long returnId = addReturn(year);
    List<String> results = new ArrayList<>();
    fake().setDuringQuestion(access -> results.add(step.run(access)));
    try {
      personAssistant.askAboutReturn(returnId, "Made-up question");
    } catch (AssistantException e) {
      throw new AssertionError(e);
    }
    assertEquals(1, results.size());
    return results.getFirst();
  }

  private String tool(int year, String name, String arguments) {
    String body = during(year, access -> call(access, toolCall(name, arguments)));
    assertTrue(body.contains("\"isError\":false"), body);
    return textOf(body);
  }

  private String call(DocumentAccess access, String body) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/mcp"))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + access.token())
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
    assertEquals(200, response.statusCode());
    return response.body();
  }

  private static String toolCall(String name, String arguments) {
    return """
        {"jsonrpc":"2.0","method":"tools/call","id":3,"params":{"name":"%s","arguments":%s}}"""
        .formatted(name, arguments);
  }

  /** The text of the first content block of a tool result. */
  @SuppressWarnings("unchecked")
  private static String textOf(String body) {
    Map<String, Object> answer = JsonParserFactory.getJsonParser().parseMap(body);
    Map<String, Object> result = (Map<String, Object>) answer.get("result");
    List<Map<String, Object>> content = (List<Map<String, Object>>) result.get("content");
    return (String) content.getFirst().get("text");
  }

  private long addReturn(int year) {
    String name = "Made-up " + UUID.randomUUID();
    household.addPerson(name, new Answers(Map.of()));
    long personId =
        household.persons().stream()
            .filter(person -> person.name().equals(name))
            .findFirst()
            .orElseThrow()
            .id();
    household.switchAssistant(personId, true);
    return household.addReturn(personId, new TaxYear(year));
  }

  private FakeAssistant fake() {
    return (FakeAssistant) assistant;
  }
}
