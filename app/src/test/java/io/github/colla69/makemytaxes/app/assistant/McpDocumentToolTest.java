package io.github.colla69.makemytaxes.app.assistant;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.colla69.makemytaxes.app.storage.DocumentStore;
import io.github.colla69.makemytaxes.app.storage.DocumentStore.NewDocument;
import io.github.colla69.makemytaxes.app.storage.HouseholdStore;
import io.github.colla69.makemytaxes.app.storage.HouseholdStore.StoredPerson;
import io.github.colla69.makemytaxes.core.TaxYear;
import io.github.colla69.makemytaxes.core.document.DocumentKind;
import io.github.colla69.makemytaxes.core.form.Answers;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The MCP server of the app, with the fake assistant in place of Claude Code. During a question the
 * test sends the requests that Claude Code would send, with the token of the question.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(McpDocumentToolTest.Fake.class)
class McpDocumentToolTest {

  @TestConfiguration
  static class Fake {
    @Bean
    @Primary
    FakeAssistant fakeAssistant() {
      return new FakeAssistant();
    }
  }

  private static final TaxYear YEAR = new TaxYear(2022);
  private static final Pattern FILE = Pattern.compile("liegt unter (\\S+)\\. Lies");

  @TempDir static Path databaseFolder;

  @LocalServerPort private int port;

  @Autowired private HouseholdStore household;

  @Autowired private DocumentStore documents;

  @Autowired private PersonAssistant personAssistant;

  @Autowired private Assistant assistant;

  private final HttpClient client = HttpClient.newHttpClient();

  /** A made-up return with one made-up PDF. */
  private record MadeUpReturn(long personId, long returnId, long documentId, byte[] pdf) {}

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("makemytaxes.database.path", () -> databaseFolder.resolve("mcp.db").toString());
    registry.add("makemytaxes.database.passphrase", () -> "made-up-test-passphrase");
  }

  @BeforeEach
  void turnTheAssistantOffForEveryone() {
    for (StoredPerson person : household.persons()) {
      household.switchAssistant(person.id(), false);
    }
    fake().setDuringQuestion(access -> {});
  }

  @Test
  void theDocumentToolWritesTheDocumentOfTheReturnForClaudeCodeToRead() throws Exception {
    MadeUpReturn open = addReturn();
    household.switchAssistant(open.personId(), true);
    List<String> answers = new ArrayList<>();
    List<DocumentAccess> seen = new ArrayList<>();
    fake()
        .setDuringQuestion(
            access -> {
              seen.add(access);
              answers.add(call(access.token(), initialize()).body());
              answers.add(call(access.token(), "{\"jsonrpc\":\"2.0\",\"method\":\"tools/list\",\"id\":2}").body());
              HttpResponse<String> read = call(access.token(), readDocument(open.documentId()));
              assertEquals(200, read.statusCode());
              Path file = fileIn(read.body());
              assertTrue(file.startsWith(access.documents()), "the file is in the folder of the question");
              assertArrayEquals(open.pdf(), Files.readAllBytes(file));
            });

    personAssistant.askAboutReturn(open.returnId(), "Made-up question about the certificate");

    assertTrue(answers.get(0).contains("\"protocolVersion\":\"2025-06-18\""));
    assertTrue(answers.get(1).contains("\"name\":\"read_document\""));
    assertTrue(fake().prompts().getLast().contains("Beleg-Nr. " + open.documentId()));
    assertEquals(1, seen.size());
    assertFalse(Files.exists(seen.getFirst().folder()), "the folder is deleted after the question");
  }

  @Test
  void theTokenDoesNotOpenAReturnThatTheAssistantDoesNotSee() throws Exception {
    MadeUpReturn open = addReturn();
    MadeUpReturn other = addReturn();
    household.switchAssistant(open.personId(), true);
    List<String> bodies = new ArrayList<>();
    fake()
        .setDuringQuestion(
            access -> bodies.add(call(access.token(), readDocument(other.documentId())).body()));

    personAssistant.askAboutReturn(open.returnId(), "Made-up question");

    assertTrue(bodies.getFirst().contains("\"isError\":true"), bodies.getFirst());
    assertFalse(bodies.getFirst().contains("liegt unter"));
  }

  @Test
  void theLinkedReturnOpensWhileBothAdultsHaveTheAssistantOn() throws Exception {
    MadeUpReturn open = addReturn();
    MadeUpReturn other = addReturn();
    household.switchAssistant(open.personId(), true);
    household.switchAssistant(other.personId(), true);
    List<String> bodies = new ArrayList<>();
    fake()
        .setDuringQuestion(
            access -> bodies.add(call(access.token(), readDocument(other.documentId())).body()));

    personAssistant.askAboutReturn(open.returnId(), "Made-up question");

    assertTrue(bodies.getFirst().contains("liegt unter"), bodies.getFirst());
  }

  @Test
  void theTokenOfOneQuestionDoesNotWorkForTheNextQuestion() throws Exception {
    MadeUpReturn open = addReturn();
    household.switchAssistant(open.personId(), true);
    List<DocumentAccess> first = new ArrayList<>();
    fake().setDuringQuestion(first::add);
    personAssistant.askAboutReturn(open.returnId(), "Made-up first question");

    List<Integer> statuses = new ArrayList<>();
    List<String> secondTokens = new ArrayList<>();
    fake()
        .setDuringQuestion(
            access -> {
              secondTokens.add(access.token());
              statuses.add(call(first.getFirst().token(), readDocument(open.documentId())).statusCode());
              statuses.add(call(access.token(), readDocument(open.documentId())).statusCode());
            });
    personAssistant.askAboutReturn(open.returnId(), "Made-up second question");

    assertNotEquals(first.getFirst().token(), secondTokens.getFirst());
    assertEquals(List.of(401, 200), statuses);
    assertEquals(401, call(secondTokens.getFirst(), readDocument(open.documentId())).statusCode());
  }

  @Test
  void aLateToolCallWritesNothingAfterTheQuestionEnded() {
    QuestionTokens tokens = new QuestionTokens(1);
    DocumentAccess access = tokens.open(Set.of(1L));
    tokens.close(access);

    assertTrue(tokens.write(access, "beleg-1.pdf", new byte[] {1}).isEmpty());
    assertFalse(Files.exists(access.folder()));
  }

  @Test
  void theAppDeletesAQuestionFolderLeftFromAnEarlierRun() throws Exception {
    Path left =
        Files.createDirectories(
            Path.of(System.getProperty("java.io.tmpdir"), "makemytaxes-question-made-up-left"));
    Files.writeString(left.resolve("beleg-1.pdf"), "made-up");
    QuestionTokens tokens = new QuestionTokens(1);
    DocumentAccess running = tokens.open(Set.of(1L));

    tokens.deleteLeftFolders();

    assertFalse(Files.exists(left));
    assertTrue(Files.exists(running.folder()), "the folder of a running question stays");
    tokens.close(running);
  }

  @Test
  void aRequestWithoutATokenIsRefused() throws Exception {
    assertEquals(401, call(null, initialize()).statusCode());
    assertEquals(401, call("made-up-token", initialize()).statusCode());
  }

  private HttpResponse<String> call(String token, String body) throws Exception {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/mcp"))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json, text/event-stream")
            .POST(HttpRequest.BodyPublishers.ofString(body));
    if (token != null) {
      request.header("Authorization", "Bearer " + token);
    }
    return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  private static String initialize() {
    return """
        {"jsonrpc":"2.0","method":"initialize","id":1,"params":{"protocolVersion":"2025-06-18",\
        "capabilities":{},"clientInfo":{"name":"fake","version":"0"}}}""";
  }

  private static String readDocument(long documentId) {
    return """
        {"jsonrpc":"2.0","method":"tools/call","id":3,"params":{"name":"read_document",\
        "arguments":{"document_id":%d}}}"""
        .formatted(documentId);
  }

  private static Path fileIn(String body) {
    Matcher matcher = FILE.matcher(body);
    assertTrue(matcher.find(), body);
    return Path.of(matcher.group(1));
  }

  private MadeUpReturn addReturn() {
    String name = "Made-up " + UUID.randomUUID();
    household.addPerson(name, new Answers(Map.of()));
    long personId =
        household.persons().stream()
            .filter(person -> person.name().equals(name))
            .findFirst()
            .orElseThrow()
            .id();
    long returnId = household.addReturn(personId, YEAR);
    byte[] pdf = ("%PDF-1.4 made-up certificate " + name).getBytes(StandardCharsets.UTF_8);
    long documentId =
        documents.add(
            returnId,
            new NewDocument(
                DocumentKind.WAGE_TAX_CERTIFICATE, "made-up.pdf", "application/pdf", pdf),
            LocalDate.of(2023, 2, 3));
    return new MadeUpReturn(personId, returnId, documentId, pdf);
  }

  private FakeAssistant fake() {
    return (FakeAssistant) assistant;
  }
}
