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
import io.github.colla69.makemytaxes.app.storage.ExpenseStore;
import io.github.colla69.makemytaxes.app.storage.HouseholdStore;
import io.github.colla69.makemytaxes.core.TaxYear;
import io.github.colla69.makemytaxes.core.form.Answers;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
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

/**
 * The receipt check: after the user chose a receipt, a modal dialog shows it beside the fields
 * that the fake assistant read, and the user completes and saves them.
 */
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

  private static final FilePayload MADE_UP_PHOTO =
      new FilePayload(
          "made-up-photo.png",
          "image/png",
          new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n', 0, 0, 0, 0});

  private static final String FULL_ANSWER =
      """
      {"betrag": "1.234,56", "bezahltAm": "03.02.2022",
       "beschreibung": "Made-up optician – glasses", "thema": "MEDICAL",
       "arbeitskosten": "", "unsicher": []}
      """;

  private static final String ANSWER_WITHOUT_DATE =
      """
      {"betrag": "1.234,56", "beschreibung": "Made-up optician – glasses", "thema": "MEDICAL",
       "unsicher": []}
      """;

  @TempDir static Path databaseFolder;

  private static Playwright playwright;
  private static Browser browser;

  @LocalServerPort private int port;

  @Autowired private HouseholdStore household;

  @Autowired private ExpenseStore expenses;

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
  void theUserCompletesTheReadReceiptInTheDialogAndSavesIt() {
    fake().setAnswer(ANSWER_WITHOUT_DATE);
    long returnId = addReturn(true);
    Page page = browser.newPage();
    page.navigate(returnPage(returnId));

    page.getByLabel("Neue Datei").setInputFiles(MADE_UP_RECEIPT);

    Locator dialog = receiptCheck(page);
    assertThat(dialog).isVisible();
    assertThat(dialog.locator(".receipt-preview iframe"))
        .hasAttribute("src", Pattern.compile("^blob:"));
    assertThat(dialog.getByLabel("Betrag (EUR)")).hasValue("1.234,56");
    assertThat(dialog.getByLabel("Beschreibung")).hasValue("Made-up optician – glasses");
    assertThat(dialog.getByLabel("Kategorie")).hasValue("MEDICAL");
    assertThat(dialog.locator(".read-mark")).hasCount(3);
    assertThat(dialog.getByLabel("Datum")).hasValue("");
    assertThat(dialog.locator(".field.missing")).hasCount(1);
    assertThat(dialog.locator(".field.missing")).containsText("nicht aus dem Beleg gelesen");

    dialog.getByLabel("Datum").fill("03.02.2022");
    dialog.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Speichern")).click();

    assertThat(page.locator("dialog[open]")).hasCount(0);
    Locator row = page.locator("#expenses tbody tr");
    assertThat(row).hasCount(1);
    assertThat(row).containsText("03.02.2022");
    assertThat(row).containsText("1.234,56");
    assertThat(row).containsText("Made-up optician – glasses");
    assertThat(
            row.getByRole(
                AriaRole.LINK, new Locator.GetByRoleOptions().setName("made-up-receipt.pdf")))
        .isVisible();
  }

  @Test
  void aRefusedSaveKeepsTheDialogTheValuesAndTheFile() {
    long returnId = addReturn(true);
    Page page = browser.newPage();
    page.navigate(returnPage(returnId));

    page.getByLabel("Neue Datei").setInputFiles(MADE_UP_RECEIPT);
    Locator dialog = receiptCheck(page);
    dialog.getByLabel("Datum").fill("03.02.2021");
    dialog.getByLabel("Beschreibung").fill("Made-up glasses, checked");
    Locator save =
        dialog.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Speichern"));
    save.click();

    Locator dateError = dialog.locator(".field:has([name=paidOn]) + #receipt-check-error .error");
    assertThat(dateError).containsText("Diese Erklärung gilt für 2022");
    assertThat(dialog).isVisible();
    assertThat(dialog.getByLabel("Datum")).hasValue("03.02.2021");
    assertThat(dialog.getByLabel("Betrag (EUR)")).hasValue("1.234,56");
    assertThat(dialog.getByLabel("Beschreibung")).hasValue("Made-up glasses, checked");
    assertThat(dialog.locator(".receipt-preview iframe")).isVisible();
    assertThat(page.locator("#expenses tbody tr")).hasCount(0);

    dialog.getByLabel("Datum").fill("03.02.2022");
    save.click();

    assertThat(page.locator("dialog[open]")).hasCount(0);
    Locator row = page.locator("#expenses tbody tr");
    assertThat(row).hasCount(1);
    assertThat(row).containsText("03.02.2022");
    assertThat(row).containsText("Made-up glasses, checked");
    assertThat(
            row.getByRole(
                AriaRole.LINK, new Locator.GetByRoleOptions().setName("made-up-receipt.pdf")))
        .isVisible();
  }

  @Test
  void aZeroAmountKeepsTheDialogWithTheErrorBelowTheAmount() {
    long returnId = addReturn(true);
    Page page = browser.newPage();
    page.navigate(returnPage(returnId));

    page.getByLabel("Neue Datei").setInputFiles(MADE_UP_RECEIPT);
    Locator dialog = receiptCheck(page);
    dialog.getByLabel("Betrag (EUR)").fill("0,00");
    dialog.getByLabel("Beschreibung").fill("Made-up glasses, checked");
    dialog.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Speichern")).click();

    Locator amountError = dialog.locator(".field:has([name=amount]) + #receipt-check-error .error");
    assertThat(amountError).containsText("größer als null");
    assertThat(dialog).isVisible();
    assertThat(dialog.locator(".receipt-preview iframe")).isVisible();
    assertThat(page.locator("#expenses tbody tr")).hasCount(0);
  }

  @Test
  void aBlankDescriptionKeepsTheDialogWithTheErrorBelowTheDescription() {
    long returnId = addReturn(true);
    Page page = browser.newPage();
    page.navigate(returnPage(returnId));

    page.getByLabel("Neue Datei").setInputFiles(MADE_UP_RECEIPT);
    Locator dialog = receiptCheck(page);
    dialog.getByLabel("Beschreibung").fill("   ");
    dialog.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Speichern")).click();

    Locator descriptionError =
        dialog.locator(".field:has([name=description]) + #receipt-check-error .error");
    assertThat(descriptionError).containsText("braucht eine Beschreibung");
    assertThat(dialog).isVisible();
    assertThat(dialog.locator(".receipt-preview iframe")).isVisible();
    assertThat(page.locator("#expenses tbody tr")).hasCount(0);
  }

  @Test
  void anErrorAboutAFieldOutsideTheDialogGoesToThePage() {
    long returnId = addReturn(true);
    Page page = browser.newPage();
    page.navigate(returnPage(returnId));
    // The input mask would drop the letters, so the made-up wrong refund is set directly.
    page.getByLabel("Davon erstattet (EUR)").evaluate("field => field.value = 'abc'");

    page.getByLabel("Neue Datei").setInputFiles(MADE_UP_RECEIPT);
    Locator dialog = receiptCheck(page);
    dialog.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Speichern")).click();

    // The dialog would hide the refund field that the user must correct.
    assertThat(page.locator("dialog")).hasCount(0);
    assertThat(page.locator("#expense-form > .error")).isVisible();
    assertThat(page.getByLabel("Davon erstattet (EUR)")).hasValue("abc");
    assertThat(page.getByLabel("Betrag (EUR)")).hasValue("1.234,56");
  }

  @Test
  void aBackdropClickClosesTheReceiptCheckAndStoresNothing() {
    long returnId = addReturn(true);
    Page page = browser.newPage();
    page.navigate(returnPage(returnId));

    page.getByLabel("Neue Datei").setInputFiles(MADE_UP_RECEIPT);
    Locator dialog = receiptCheck(page);
    assertThat(dialog.getByLabel("Betrag (EUR)")).hasValue("1.234,56");
    Backdrop.closesOnlyOnABackdropClick(page, dialog);

    assertThat(page.locator("dialog")).hasCount(0);
    assertEquals(0, expenses.expensesOf(returnId).size(), "nothing was stored");
    assertThat(page.getByLabel("Neue Datei")).hasValue("");
    assertThat(page.getByLabel("Beschreibung")).isEnabled();
  }

  @Test
  void cancelingTheDialogStoresNothing() {
    long returnId = addReturn(true);
    Page page = browser.newPage();
    page.navigate(returnPage(returnId));
    page.getByLabel("Beschreibung").fill("Made-up typed text");

    page.getByLabel("Neue Datei").setInputFiles(MADE_UP_RECEIPT);
    Locator dialog = receiptCheck(page);
    assertThat(dialog.getByLabel("Betrag (EUR)")).hasValue("1.234,56");
    dialog.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Abbrechen")).click();

    assertThat(page.locator("dialog")).hasCount(0);
    assertThat(page.locator("#expenses tbody tr")).hasCount(0);
    assertEquals(0, expenses.expensesOf(returnId).size(), "nothing was stored");
    assertThat(page.getByLabel("Neue Datei")).hasValue("");
    assertThat(page.getByLabel("Beschreibung")).isEnabled();
    assertThat(page.getByLabel("Beschreibung")).hasValue("Made-up typed text");
    assertThat(page.getByLabel("Betrag (EUR)")).hasValue("");
  }

  @Test
  void clearingTheFormAfterACanceledCheckAsksNothing() {
    long returnId = addReturn(true);
    Page page = browser.newPage();
    page.navigate(returnPage(returnId));
    page.getByLabel("Neue Datei").setInputFiles(MADE_UP_RECEIPT);
    receiptCheck(page)
        .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Abbrechen"))
        .click();
    int asked = fake().prompts().size();

    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Alle Felder leeren"))
        .click();

    page.waitForTimeout(500);
    assertThat(page.locator("dialog")).hasCount(0);
    assertEquals(asked, fake().prompts().size(), "clearing the file sends nothing");
  }

  @Test
  void aNewReceiptClearsTheErrorOfTheFileBefore() {
    long returnId = addReturn(true);
    Page page = browser.newPage();
    page.navigate(returnPage(returnId));
    page.getByLabel("Neue Datei")
        .setInputFiles(
            new FilePayload(
                "made-up.txt", "text/plain", "made-up text".getBytes(StandardCharsets.US_ASCII)));
    assertThat(page.locator("#read-error .error")).containsText("Nur PDF, JPEG und PNG");

    page.getByLabel("Neue Datei").setInputFiles(MADE_UP_RECEIPT);

    assertThat(receiptCheck(page)).isVisible();
    assertThat(page.locator("#read-error .error")).hasCount(0);
  }

  @Test
  void whileTheAssistantIsOffTheDialogShowsTheReceiptWithEmptyFields() {
    long returnId = addReturn(false);
    int asked = fake().prompts().size();
    Page page = browser.newPage();
    page.navigate(returnPage(returnId));

    page.getByLabel("Neue Datei").setInputFiles(MADE_UP_PHOTO);

    Locator dialog = receiptCheck(page);
    assertThat(dialog.locator("#read-off")).containsText("nicht automatisch lesen");
    assertThat(dialog.locator("#read-off").getByRole(AriaRole.LINK))
        .hasAttribute("href", "/persons/" + personOf(returnId) + "#assistant");
    assertThat(dialog.locator(".receipt-preview img"))
        .hasAttribute("src", Pattern.compile("^blob:"));
    assertThat(dialog.getByLabel("Betrag (EUR)")).hasValue("");
    assertThat(dialog.getByLabel("Datum")).hasValue("");
    assertThat(dialog.getByLabel("Beschreibung")).hasValue("");
    assertThat(dialog.locator(".read-mark")).hasCount(0);
    assertThat(dialog.locator(".field.missing")).hasCount(4);
    assertEquals(asked, fake().prompts().size(), "the receipt did not reach the assistant");

    dialog.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Schließen")).click();
    assertThat(page.locator("dialog")).hasCount(0);
    assertEquals(0, expenses.expensesOf(returnId).size(), "nothing was stored");
  }

  @Test
  void whenReadingFailsTheDialogSaysSoAndTheFieldsStayEmpty() {
    fake().setAnswer("Made-up answer without fields");
    long returnId = addReturn(true);
    Page page = browser.newPage();
    page.navigate(returnPage(returnId));

    page.getByLabel("Neue Datei").setInputFiles(MADE_UP_RECEIPT);

    Locator dialog = receiptCheck(page);
    assertThat(dialog.locator("#read-failed")).containsText("konnte den Beleg nicht lesen");
    assertThat(dialog.locator(".receipt-preview iframe")).hasCount(1);
    assertThat(dialog.getByLabel("Betrag (EUR)")).hasValue("");
    assertThat(dialog.getByLabel("Beschreibung")).hasValue("");
    assertThat(dialog.locator(".read-mark")).hasCount(0);
  }

  @Test
  void anIsoDateFromTheReceiptFillsTheDateField() {
    fake().setAnswer(FULL_ANSWER.replace("03.02.2022", "2022-02-03"));
    long returnId = addReturn(true);
    Page page = browser.newPage();
    page.navigate(returnPage(returnId));

    page.getByLabel("Neue Datei").setInputFiles(MADE_UP_RECEIPT);

    Locator dialog = receiptCheck(page);
    assertThat(dialog.getByLabel("Betrag (EUR)")).hasValue("1.234,56");
    assertThat(dialog.getByLabel("Datum")).hasValue("03.02.2022");
    assertThat(dialog.locator(".field:has([name=paidOn]) .read-mark")).isVisible();
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

    Locator dialog = receiptCheck(page);
    assertThat(dialog.getByLabel("Beschreibung")).hasValue("Made-up shop – pens");
    assertThat(dialog.getByLabel("Betrag (EUR)")).hasValue("");
    assertThat(dialog.getByLabel("Datum")).hasValue("");
    assertThat(dialog.locator(".read-mark")).hasCount(1);
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

    Locator dialog = receiptCheck(page);
    assertThat(dialog.getByLabel("Betrag (EUR)")).hasValue("45,00");
    assertThat(dialog.getByLabel("Kategorie")).hasValue("HOUSEHOLD_SERVICES");
    assertThat(dialog.getByLabel("Beschreibung")).hasValue("Made-up window cleaning");
    assertThat(dialog.locator(".read-mark")).hasCount(1);
    assertThat(dialog.locator(".labour-cost")).containsText("Arbeitskosten nicht aus dem Beleg");
  }

  @Test
  void theDialogShowsTheLabourPartOfAHouseholdService() {
    fake().setAnswer(
        """
        {"betrag": "300,00", "bezahltAm": "03.02.2022", "beschreibung": "Made-up gardener",
         "thema": "HOUSEHOLD_SERVICES", "arbeitskosten": "200,00", "unsicher": []}
        """);
    long returnId = addReturn(true);
    Page page = browser.newPage();
    page.navigate(returnPage(returnId));

    page.getByLabel("Neue Datei").setInputFiles(MADE_UP_RECEIPT);

    assertThat(receiptCheck(page).locator(".labour-cost"))
        .containsText("Arbeitskosten laut Beleg: 200,00 EUR");
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
    assertThat(page.locator("dialog")).hasCount(0);
    assertThat(page.getByLabel("Datum")).hasValue("03.02.2022");
    assertThat(page.getByLabel("Betrag (EUR)")).hasValue("10,00");
    assertThat(page.getByLabel("Kategorie")).hasValue("MEDICAL");
    assertThat(page.getByLabel("Beschreibung")).hasValue("Made-up cable");
  }

  @Test
  void withoutJavaScriptTheFormSavesTheExpenseWithTheFile() {
    long returnId = addReturn(true);
    int asked = fake().prompts().size();
    Page page = browser.newPage(new Browser.NewPageOptions().setJavaScriptEnabled(false));
    page.navigate(returnPage(returnId));

    page.getByLabel("Neue Datei").setInputFiles(MADE_UP_RECEIPT);
    page.getByLabel("Datum").fill("03.02.2022");
    page.getByLabel("Betrag (EUR)").fill("10,00");
    page.getByLabel("Beschreibung").fill("Made-up cable");
    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Ausgabe hinzufügen"))
        .click();

    Locator row = page.locator("#expenses tbody tr");
    assertThat(row).hasCount(1);
    assertThat(
            row.getByRole(
                AriaRole.LINK, new Locator.GetByRoleOptions().setName("made-up-receipt.pdf")))
        .isVisible();
    assertEquals(asked, fake().prompts().size(), "without JavaScript nothing reads the receipt");
  }

  private static Locator receiptCheck(Page page) {
    return page.locator("dialog.receipt-check[open]");
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
