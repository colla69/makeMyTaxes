package io.github.colla69.makemytaxes.app.web;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;
import io.github.colla69.makemytaxes.app.assistant.Assistant;
import io.github.colla69.makemytaxes.app.assistant.FakeAssistant;
import io.github.colla69.makemytaxes.app.assistant.PersonAssistant;
import io.github.colla69.makemytaxes.app.storage.ProposalStore;
import io.github.colla69.makemytaxes.app.storage.ProposalStore.StoredProposal;
import io.github.colla69.makemytaxes.core.form.Interview;
import io.github.colla69.makemytaxes.core.form.Kind;
import io.github.colla69.makemytaxes.app.document.MadeUpCertificate;
import io.github.colla69.makemytaxes.app.storage.DocumentStore;
import io.github.colla69.makemytaxes.app.storage.DocumentStore.NewDocument;
import io.github.colla69.makemytaxes.app.storage.FormStore;
import io.github.colla69.makemytaxes.app.storage.HouseholdStore;
import io.github.colla69.makemytaxes.core.TaxYear;
import io.github.colla69.makemytaxes.core.document.DocumentKind;
import io.github.colla69.makemytaxes.core.form.AnlageN;
import io.github.colla69.makemytaxes.core.form.Answers;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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
 * The assistant proposes values from a made-up certificate through the MCP tool, as Claude Code
 * would, and the user confirms or rejects each one in the panel.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(AssistantProposalBrowserTest.Fake.class)
class AssistantProposalBrowserTest {

  @TestConfiguration
  static class Fake {
    @Bean
    @Primary
    FakeAssistant fakeAssistant() {
      return new FakeAssistant();
    }
  }

  private static final TaxYear YEAR = new TaxYear(2022);

  @TempDir static Path databaseFolder;

  private static Playwright playwright;
  private static Browser browser;

  @LocalServerPort private int port;

  @Autowired private HouseholdStore household;
  @Autowired private DocumentStore documents;
  @Autowired private FormStore forms;
  @Autowired private Assistant assistant;
  @Autowired private PersonAssistant personAssistant;
  @Autowired private ProposalStore proposals;

  private final HttpClient client = HttpClient.newHttpClient();

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add(
        "makemytaxes.database.path", () -> databaseFolder.resolve("proposals.db").toString());
    registry.add("makemytaxes.database.passphrase", () -> "made-up-test-passphrase");
  }

  @BeforeAll
  static void startBrowser() {
    playwright = Playwright.create();
    browser = playwright.chromium().launch();
  }

  @AfterAll
  static void stopBrowser() {
    browser.close();
    playwright.close();
  }

  @Test
  void theFormsHoldExactlyTheConfirmedValuesWithTheirSource() {
    long personId = addPerson();
    long returnId = household.addReturn(personId, YEAR);
    long documentId =
        documents.add(
            returnId,
            new NewDocument(
                DocumentKind.WAGE_TAX_CERTIFICATE,
                "made-up-certificate.pdf",
                "application/pdf",
                MadeUpCertificate.pdf("2022")),
            LocalDate.of(2023, 2, 14));
    household.switchAssistant(personId, true);
    List<String> toolAnswers = new ArrayList<>();
    fake()
        .setDuringQuestion(
            access ->
                toolAnswers.add(
                    call(access.token(), proposeCertificateValues(documentId)).body()));
    fake().setAnswer("Made-up answer: I propose the values of the certificate.");

    Page page = browser.newPage();
    page.navigate("http://127.0.0.1:" + port + "/returns/" + returnId);
    Locator panel = page.locator("#assistant-chat");
    panel.locator("summary").click();
    panel.getByLabel("Frage an den Assistenten").fill("Made-up question: fill the certificate");
    button(panel, "Fragen").click();

    Locator proposals = panel.locator(".assistant-proposal");
    assertThat(proposals).hasCount(3);
    assertThat(proposals.nth(0)).containsText("Bruttoarbeitslohn");
    assertThat(proposals.nth(0)).containsText("24.000,00");
    assertThat(proposals.nth(0)).containsText("Lohnsteuerbescheinigung, Zeile 3");
    assertTrue(
        toolAnswers.getFirst().contains("Nicht vorgeschlagen: anlage-n/madeUpField"),
        toolAnswers.getFirst());
    assertTrue(
        fake().prompts().getLast().contains("propose_values"), "the prompt names the tool");
    assertTrue(
        forms.answers(returnId, AnlageN.ID).values().isEmpty(),
        "no value enters the return before the user confirms it");

    choice(proposals, "Bruttoarbeitslohn", "Übernehmen").click();
    assertThat(proposals).hasCount(2);
    choice(proposals, "Steuerklasse", "Verwerfen").click();
    assertThat(proposals).hasCount(1);
    button(panel, "Alle übernehmen").click();
    assertThat(proposals).hasCount(0);

    assertEquals(
        Map.of("grossWage", "24.000,00", "wageTax", "3.100,00"),
        forms.answers(returnId, AnlageN.ID).values());
    page.navigate("http://127.0.0.1:" + port + "/returns/" + returnId + "/review");
    for (String label : List.of("Bruttoarbeitslohn", "Lohnsteuer (EUR)")) {
      assertThat(row(page, label).getByText("Beleg: made-up-certificate.pdf"))
          .hasAttribute("href", "/returns/" + returnId + "/documents/" + documentId);
    }
    assertThat(row(page, "Bruttoarbeitslohn")).containsText("24.000,00");
    assertThat(row(page, "Lohnsteuer (EUR)")).containsText("3.100,00");
    assertThat(row(page, "Steuerklasse")).hasCount(0);

    // A later answer in the interview keeps the confirmed source. A changed value loses it.
    forms.save(
        returnId,
        Interview.ID,
        new Answers(Map.of("employed", Kind.YES, "wageCertificate", Kind.NO)));
    assertEquals(
        Map.of("grossWage", documentId, "wageTax", documentId),
        forms.answerDocuments(returnId, AnlageN.ID));
    forms.saveAnswer(returnId, AnlageN.ID, "wageTax", "3.000,00");
    assertEquals(Map.of("grossWage", documentId), forms.answerDocuments(returnId, AnlageN.ID));
  }

  @Test
  void aProposalReachesNoOtherReturn() throws Exception {
    long openPerson = addPerson();
    long openReturn = household.addReturn(openPerson, YEAR);
    long otherPerson = addPerson();
    long otherReturn = household.addReturn(otherPerson, YEAR);
    long otherDocument =
        documents.add(
            otherReturn,
            new NewDocument(
                DocumentKind.WAGE_TAX_CERTIFICATE,
                "made-up-other.pdf",
                "application/pdf",
                MadeUpCertificate.pdf("2022")),
            LocalDate.of(2023, 2, 14));
    household.switchAssistant(openPerson, true);
    household.switchAssistant(otherPerson, true);
    List<String> toolAnswers = new ArrayList<>();
    String fromOtherDocument =
        "{\"form_id\":\"anlage-n\",\"field_id\":\"grossWage\",\"value\":\"1,00\","
            + "\"source\":\"Made-up\",\"document_id\":"
            + otherDocument
            + "}";
    String ownStatement =
        "{\"form_id\":\"anlage-n\",\"field_id\":\"wageTax\",\"value\":\"2,00\","
            + "\"source\":\"Made-up\"}";
    fake()
        .setDuringQuestion(
            access ->
                toolAnswers.add(
                    call(access.token(), proposeValues(fromOtherDocument + "," + ownStatement))
                        .body()));

    personAssistant.askAboutReturn(openReturn, "Made-up question");

    assertTrue(
        toolAnswers.getFirst().contains("Diesen Beleg gibt es in der offenen Erklärung nicht"),
        toolAnswers.getFirst());
    List<StoredProposal> stored = proposals.proposalsOf(openReturn);
    assertEquals(1, stored.size());
    assertTrue(proposals.proposalsOf(otherReturn).isEmpty());

    // The proposal of one return cannot be confirmed through the address of another.
    String confirmThroughOther =
        "http://127.0.0.1:%d/returns/%d/assistant/proposals/%d/confirm"
            .formatted(port, otherReturn, stored.getFirst().id());
    HttpResponse<String> response =
        client.send(
            HttpRequest.newBuilder(URI.create(confirmThroughOther))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertEquals(200, response.statusCode());
    assertTrue(forms.answers(otherReturn, AnlageN.ID).values().isEmpty());
    assertTrue(forms.answers(openReturn, AnlageN.ID).values().isEmpty());
    assertEquals(1, proposals.proposalsOf(openReturn).size());
  }

  @Test
  void aValueFromTheUsersOwnWordsCountsAsOwnStatement() {
    long personId = addPerson();
    long returnId = household.addReturn(personId, YEAR);
    household.switchAssistant(personId, true);
    fake()
        .setDuringQuestion(
            access ->
                call(
                    access.token(),
                    proposeValues(
                        "{\"form_id\":\"anlage-n\",\"field_id\":\"commuteKm\",\"value\":\"17\","
                            + "\"source\":\"Angabe des Nutzers im Gespräch\"}")));

    Page page = browser.newPage();
    page.navigate("http://127.0.0.1:" + port + "/returns/" + returnId);
    Locator panel = page.locator("#assistant-chat");
    panel.locator("summary").click();
    panel.getByLabel("Frage an den Assistenten").fill("Made-up question: my commute is 17 km");
    button(panel, "Fragen").click();
    assertThat(panel.locator(".assistant-proposal")).containsText("Angabe des Nutzers im Gespräch");
    button(panel, "Alle übernehmen").click();
    assertThat(panel.locator(".assistant-proposal")).hasCount(0);

    page.navigate("http://127.0.0.1:" + port + "/returns/" + returnId + "/review");
    assertThat(row(page, "einfache Entfernung").locator(".source")).hasText("eigene Angabe");
    assertFalse(forms.answerDocuments(returnId, AnlageN.ID).containsKey("commuteKm"));
  }

  /** Three values of the made-up certificate, and one field that does not exist. */
  private static String proposeCertificateValues(long documentId) {
    String certificate =
        "\"source\":\"Lohnsteuerbescheinigung, Zeile %s\",\"document_id\":" + documentId;
    return proposeValues(
        "{\"form_id\":\"anlage-n\",\"field_id\":\"grossWage\",\"value\":\"24.000,00\","
            + certificate.formatted("3")
            + "},{\"form_id\":\"anlage-n\",\"field_id\":\"taxClass\",\"value\":\"3\","
            + "\"source\":\"Lohnsteuerbescheinigung, Steuerklasse\",\"document_id\":"
            + documentId
            + "},{\"form_id\":\"anlage-n\",\"field_id\":\"wageTax\",\"value\":\"3.100,00\","
            + certificate.formatted("4")
            + "},{\"form_id\":\"anlage-n\",\"field_id\":\"madeUpField\",\"value\":\"1\","
            + "\"source\":\"Made-up\"}");
  }

  private static String proposeValues(String values) {
    return "{\"jsonrpc\":\"2.0\",\"method\":\"tools/call\",\"id\":5,\"params\":{\"name\":"
        + "\"propose_values\",\"arguments\":{\"values\":["
        + values
        + "]}}}";
  }

  private HttpResponse<String> call(String token, String body) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/mcp"))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + token)
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
    assertEquals(200, response.statusCode(), response.body());
    return response;
  }

  private long addPerson() {
    String name = "Made-up " + UUID.randomUUID();
    household.addPerson(name, new Answers(Map.of()));
    return household.persons().stream()
        .filter(person -> person.name().equals(name))
        .findFirst()
        .orElseThrow()
        .id();
  }

  private static Locator button(Locator panel, String name) {
    return panel.getByRole(
        AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(name).setExact(true));
  }

  /** A button of the proposal for the field with this label. Its name also holds the label. */
  private static Locator choice(Locator proposals, String label, String action) {
    return proposals
        .filter(new Locator.FilterOptions().setHasText(label))
        .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(action + ": "));
  }

  private static Locator row(Page page, String label) {
    return page.locator(".review-form tbody tr")
        .filter(new Locator.FilterOptions().setHasText(label));
  }

  private FakeAssistant fake() {
    return (FakeAssistant) assistant;
  }
}
