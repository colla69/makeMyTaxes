package io.github.colla69.makemytaxes.app.web;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.FilePayload;
import io.github.colla69.makemytaxes.app.assistant.Assistant;
import io.github.colla69.makemytaxes.app.assistant.FakeAssistant;
import io.github.colla69.makemytaxes.app.storage.HouseholdStore;
import io.github.colla69.makemytaxes.core.TaxYear;
import io.github.colla69.makemytaxes.core.form.Answers;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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

/** The expense form that the assistant fills from an uploaded receipt, with the fake assistant. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(ExpenseReceiptBrowserTest.Fake.class)
class ExpenseReceiptBrowserTest {

  @TestConfiguration
  static class Fake {
    @Bean
    @Primary
    FakeAssistant fakeAssistant() {
      return new FakeAssistant();
    }
  }

  private static final TaxYear YEAR = new TaxYear(2022);

  private static final FilePayload MADE_UP_RECEIPT =
      new FilePayload(
          "made-up-receipt.pdf",
          "application/pdf",
          "%PDF-1.4 made-up receipt".getBytes(StandardCharsets.US_ASCII));

  private static final String FULL_ANSWER =
      """
      {"betrag": "1.234,56", "bezahltAm": "03.02.2022",
       "beschreibung": "Made-up optician – glasses", "thema": "MEDICAL",
       "arbeitskosten": "", "unsicher": []}
      """;

  @TempDir static Path databaseFolder;

  private static Playwright playwright;
  private static Browser browser;

  @LocalServerPort private int port;

  @Autowired private HouseholdStore household;

  @Autowired private Assistant assistant;

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add(
        "makemytaxes.database.path", () -> databaseFolder.resolve("expense-receipt.db").toString());
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

  @BeforeEach
  void resetTheFake() {
    fake().setAvailable(true);
    fake().setAnswer(FULL_ANSWER);
  }

  @Test
  void aReceiptIsNotReadWhileTheAssistantIsOff() {
    long returnId = addReturn(false);
    int asked = fake().prompts().size();
    Page page = browser.newPage();
    page.navigate(returnPage(returnId));

    page.getByLabel("Neue Datei").setInputFiles(MADE_UP_RECEIPT);

    Locator dialog = page.locator("#read-off-dialog");
    assertThat(dialog).isVisible();
    assertThat(dialog).containsText("nicht automatisch lesen");
    assertThat(dialog.getByRole(AriaRole.LINK)).hasAttribute("href", "/persons/" + personOf(returnId) + "#assistant");
    dialog.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Schließen")).click();
    assertThat(dialog).not().isVisible();

    page.getByLabel("Neue Datei").setInputFiles(MADE_UP_RECEIPT);
    Backdrop.closesOnlyOnABackdropClick(page, dialog);
    assertThat(page.getByLabel("Betrag (EUR)")).hasValue("");
    assertThat(page.getByLabel("Datum")).hasValue("");
    assertThat(page.getByLabel("Beschreibung")).hasValue("");
    assertThat(page.locator(".read-mark")).hasCount(0);
    assertEquals(asked, fake().prompts().size(), "the receipt did not reach the assistant");
  }

  @Test
  void theAssistantFillsTheFormAndTheExpenseIsStoredWithTheFile() {
    long returnId = addReturn(true);
    Page page = browser.newPage();
    page.navigate(returnPage(returnId));

    page.getByLabel("Neue Datei").setInputFiles(MADE_UP_RECEIPT);

    assertThat(page.getByLabel("Betrag (EUR)")).hasValue("1.234,56");
    assertThat(page.getByLabel("Datum")).hasValue("03.02.2022");
    assertThat(page.getByLabel("Beschreibung")).hasValue("Made-up optician – glasses");
    assertThat(page.getByLabel("Kategorie")).hasValue("MEDICAL");
    assertThat(page.locator(".read-mark")).hasCount(4);
    assertThat(page.locator("dialog[open]")).hasCount(0);

    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Ausgabe hinzufügen"))
        .click();

    Locator row = page.locator("#expenses tbody tr");
    assertThat(row).hasCount(1);
    assertThat(row).containsText("1.234,56");
    assertThat(row).containsText("Made-up optician – glasses");
    assertThat(
            row.getByRole(
                AriaRole.LINK, new Locator.GetByRoleOptions().setName("made-up-receipt.pdf")))
        .isVisible();
    assertThat(page.locator(".read-mark")).hasCount(0);
  }

  @Test
  void anIsoDateFromTheReceiptFillsTheDateField() {
    fake().setAnswer(FULL_ANSWER.replace("03.02.2022", "2022-02-03"));
    long returnId = addReturn(true);
    Page page = browser.newPage();
    page.navigate(returnPage(returnId));

    page.getByLabel("Neue Datei").setInputFiles(MADE_UP_RECEIPT);

    assertThat(page.getByLabel("Betrag (EUR)")).hasValue("1.234,56");
    assertThat(page.getByLabel("Datum")).hasValue("03.02.2022");
    assertThat(page.locator(".field:has([name=paidOn]) .read-mark")).isVisible();
  }

  @Test
  void aFieldThatDoesNotPassTheCheckStaysEmpty() {
    fake().setAnswer(
        """
        {"betrag": "12.34", "bezahltAm": "31.02.2022", "beschreibung": "Made-up shop – pens",
         "thema": "MADE_UP_TOPIC", "unsicher": []}
        """);
    long returnId = addReturn(true);
    Page page = browser.newPage();
    page.navigate(returnPage(returnId));

    page.getByLabel("Neue Datei").setInputFiles(MADE_UP_RECEIPT);

    assertThat(page.getByLabel("Beschreibung")).hasValue("Made-up shop – pens");
    assertThat(page.getByLabel("Betrag (EUR)")).hasValue("");
    assertThat(page.getByLabel("Datum")).hasValue("");
    assertThat(page.locator(".read-mark")).hasCount(1);
  }

  @Test
  void aFieldThatWasNotReadKeepsWhatTheUserTyped() {
    fake().setAnswer("{\"betrag\": \"45,00\", \"unsicher\": []}");
    long returnId = addReturn(true);
    Page page = browser.newPage();
    page.navigate(returnPage(returnId));
    page.getByLabel("Kategorie").selectOption("HOUSEHOLD_SERVICES");
    page.getByLabel("Beschreibung").fill("Made-up window cleaning");

    page.getByLabel("Neue Datei").setInputFiles(MADE_UP_RECEIPT);

    assertThat(page.getByLabel("Betrag (EUR)")).hasValue("45,00");
    assertThat(page.getByLabel("Kategorie")).hasValue("HOUSEHOLD_SERVICES");
    assertThat(page.getByLabel("Beschreibung")).hasValue("Made-up window cleaning");
    assertThat(page.locator(".read-mark")).hasCount(1);
  }

  @Test
  void clearingTheFormAfterAReadClearsTheReadFieldsAndAsksNothing() {
    long returnId = addReturn(true);
    Page page = browser.newPage();
    page.navigate(returnPage(returnId));
    page.getByLabel("Neue Datei").setInputFiles(MADE_UP_RECEIPT);
    assertThat(page.getByLabel("Betrag (EUR)")).hasValue("1.234,56");
    int asked = fake().prompts().size();

    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Alle Felder leeren"))
        .click();

    assertThat(page.getByLabel("Betrag (EUR)")).hasValue("");
    assertThat(page.getByLabel("Beschreibung")).hasValue("");
    page.waitForTimeout(500);
    assertThat(page.getByLabel("Betrag (EUR)")).hasValue("");
    assertThat(page.locator("dialog[open]")).hasCount(0);
    assertEquals(asked, fake().prompts().size(), "clearing the file sends nothing");
  }

  @Test
  void whenReadingFailsTheAppSaysSoAndTheFormStaysEmpty() {
    fake().setAnswer("Made-up answer without fields");
    long returnId = addReturn(true);
    Page page = browser.newPage();
    page.navigate(returnPage(returnId));

    page.getByLabel("Neue Datei").setInputFiles(MADE_UP_RECEIPT);

    Locator dialog = page.locator("#read-failed-dialog");
    assertThat(dialog).isVisible();
    assertThat(dialog).containsText("konnte den Beleg nicht lesen");
    assertThat(page.getByLabel("Betrag (EUR)")).hasValue("");
    assertThat(page.getByLabel("Beschreibung")).hasValue("");
    assertThat(page.locator(".read-mark")).hasCount(0);
    Backdrop.closesOnlyOnABackdropClick(page, dialog);
  }

  @Test
  void aFileOverTheLimitOfTheServerKeepsWhatTheUserTyped() throws IOException {
    long returnId = addReturn(true);
    Page page = browser.newPage();
    page.navigate(returnPage(returnId));
    page.getByLabel("Datum").fill("03.02.2022");
    page.getByLabel("Betrag (EUR)").fill("10,00");
    page.getByLabel("Kategorie").selectOption("MEDICAL");
    page.getByLabel("Beschreibung").fill("Made-up cable");
    // Over spring.servlet.multipart.max-file-size, so the server stops it before the controller.
    byte[] large = new byte[101 * 1024 * 1024];
    large[0] = '%';
    Path file = Files.write(databaseFolder.resolve("made-up-large.pdf"), large);

    page.getByLabel("Neue Datei").setInputFiles(file);

    assertThat(page.locator("#read-error .error")).containsText("größer als 20 MB");
    assertThat(page.getByLabel("Datum")).hasValue("03.02.2022");
    assertThat(page.getByLabel("Betrag (EUR)")).hasValue("10,00");
    assertThat(page.getByLabel("Kategorie")).hasValue("MEDICAL");
    assertThat(page.getByLabel("Beschreibung")).hasValue("Made-up cable");
  }

  private long addReturn(boolean assistantOn) {
    String name = "Made-up " + UUID.randomUUID();
    household.addPerson(name, new Answers(Map.of()));
    long personId =
        household.persons().stream()
            .filter(person -> person.name().equals(name))
            .findFirst()
            .orElseThrow()
            .id();
    household.switchAssistant(personId, assistantOn);
    return household.addReturn(personId, YEAR);
  }

  private long personOf(long returnId) {
    return household.personOf(returnId).orElseThrow();
  }

  private FakeAssistant fake() {
    return (FakeAssistant) assistant;
  }

  private String returnPage(long returnId) {
    return "http://127.0.0.1:" + port + "/returns/" + returnId;
  }
}
