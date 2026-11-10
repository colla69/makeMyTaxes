package io.github.colla69.makemytaxes.app.web;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.FilePayload;
import com.microsoft.playwright.options.SelectOption;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Uses a real browser, because only a browser can prove that the htmx swap works. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ExpenseBrowserTest {

  /** The app reads the type of a file from these first bytes. */
  private static final byte[] MADE_UP_PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

  @TempDir static Path databaseFolder;

  private static Playwright playwright;
  private static Browser browser;

  @LocalServerPort private int port;
  @Autowired private JdbcClient jdbcClient;

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add(
        "makemytaxes.database.path", () -> databaseFolder.resolve("browser.db").toString());
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
  void addsSeveralExpensesQuicklyWithoutLeavingThePage() {
    Page page = browser.newPage();
    openANewReturnFor2022(page);

    page.getByLabel("Datum").fill("03.02.2022");
    addExpenseWithBasis(page, "1.234,56", "Made-up laptop");

    assertThat(page.locator("#expenses tbody tr")).hasCount(1);
    assertThat(page.getByLabel("Datum")).hasValue("03.02.2022");
    assertThat(page.getByLabel("Betrag (EUR)")).isFocused();
    assertThat(page.getByLabel("Grundlage")).hasValue("");
    // The link is outside the panel, so it stays only if htmx swapped the panel in place.
    assertThat(page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Zurück")))
        .isVisible();

    addExpenseWithBasis(page, "10,00", "Made-up cable");

    assertThat(page.locator("#expenses tbody tr")).hasCount(2);
    assertThat(page.locator("#panel")).containsText("1.244,56");
    assertThat(page.locator("#panel")).not().containsText("ohne Beleg");
  }

  @Test
  void showsAnErrorForADateOutsideTheTaxYear() {
    Page page = browser.newPage();
    openANewReturnFor2022(page);

    page.getByLabel("Datum").fill("03.02.2023");
    addExpenseWithBasis(page, "10,00", "Made-up cable");

    assertThat(page.locator(".error")).containsText("03.02.2023");
    assertThat(page.locator("#expenses tbody tr")).hasCount(0);
    assertThat(page.getByLabel("Beschreibung")).hasValue("Made-up cable");
    assertThat(page.getByLabel("Grundlage")).hasValue("Made-up 12 days");
  }

  @Test
  void deletesAnExpense() {
    Page page = browser.newPage();
    openANewReturnFor2022(page);
    page.getByLabel("Datum").fill("03.02.2022");
    addExpenseWithBasis(page, "10,00", "Made-up cable");
    assertThat(page.locator("#expenses tbody tr")).hasCount(1);

    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Löschen")).click();

    assertThat(page.locator("#expenses tbody tr")).hasCount(0);
  }

  @Test
  void deletingAnExpenseKeepsTheEntryInProgress() {
    Page page = browser.newPage();
    openANewReturnFor2022(page);
    page.getByLabel("Datum").fill("03.02.2022");
    page.getByLabel("Kategorie").selectOption("WORK_TRAINING");
    addExpenseWithBasis(page, "10,00", "Made-up course");
    assertThat(page.locator("#expenses tbody tr")).hasCount(1);

    page.getByLabel("Beschreibung").fill("Next made-up course");
    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Löschen")).click();

    assertThat(page.locator("#expenses tbody tr")).hasCount(0);
    assertThat(page.getByLabel("Kategorie")).hasValue("WORK_TRAINING");
    assertThat(page.getByLabel("Beschreibung")).hasValue("Next made-up course");
    assertThat(page.getByLabel("Betrag (EUR)")).hasValue("");
  }

  @Test
  void uploadsANewDocumentInTheSameStepAndLinksIt() {
    Page page = browser.newPage();
    openANewReturnFor2022(page);
    String returnUrl = page.url();

    page.getByLabel("Datum").fill("03.02.2022");
    page.getByLabel("Neue Datei")
        .setInputFiles(new FilePayload("made-up-receipt.png", "image/png", MADE_UP_PNG));
    addExpense(page, "10,00", "Made-up cable");

    Locator row = page.locator("#expenses tbody tr");
    assertThat(row).hasCount(1);
    assertThat(row.getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName("made-up-receipt.png")))
        .isVisible();
    assertThat(row).not().containsText("Beleg fehlt");
    assertThat(page.locator("#panel")).not().containsText("ohne Beleg");

    page.navigate(returnUrl + "/documents");
    assertThat(page.locator("#documents tbody tr")).containsText("Rechnung/Quittung");
  }

  @Test
  void choosesADocumentOfTheReturn() {
    Page page = browser.newPage();
    openANewReturnFor2022(page);
    String returnUrl = page.url();
    uploadDocument(page, returnUrl, "made-up-invoice.png");
    page.navigate(returnUrl);

    page.getByLabel("Datum").fill("03.02.2022");
    page.getByLabel("Dokument").selectOption(new SelectOption().setLabel("made-up-invoice.png"));
    addExpense(page, "10,00", "Made-up cable");

    Locator row = page.locator("#expenses tbody tr");
    assertThat(row.getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName("made-up-invoice.png")))
        .isVisible();
    assertThat(row).not().containsText("Beleg fehlt");
  }

  @Test
  void anOldExpenseWithoutEvidenceShowsThatTheReceiptIsMissingInTheListAndOnTheReview() {
    Page page = browser.newPage();
    openANewReturnFor2022(page);
    String returnUrl = page.url();

    insertOldExpense(returnUrl, "WORK_OTHER", "Made-up cable");
    insertOldExpense(returnUrl, "MEDICAL", "Made-up doctor");
    page.navigate(returnUrl);
    assertThat(page.locator("#expenses tbody tr .warning")).hasCount(2);
    assertThat(page.locator("#expenses tbody tr").first()).containsText("Beleg fehlt");

    page.navigate(returnUrl + "/review");

    assertThat(
            page.locator(".review-form tr")
                .filter(new Locator.FilterOptions().setHasText("Made-up cable")))
        .containsText("Beleg fehlt");
    assertThat(page.locator("#without-form")).containsText("Beleg fehlt");
  }

  @Test
  void aStatedBasisIsEvidenceInTheListAndOnTheReview() {
    Page page = browser.newPage();
    openANewReturnFor2022(page);
    String returnUrl = page.url();

    page.getByLabel("Datum").fill("03.02.2022");
    page.getByLabel("Kategorie").selectOption("MEDICAL");
    addExpenseWithBasis(page, "10,00", "Made-up doctor");

    Locator row = page.locator("#expenses tbody tr");
    assertThat(row).containsText("Grundlage: Made-up 12 days");
    assertThat(row).not().containsText("Beleg fehlt");

    page.navigate(returnUrl + "/review");

    assertThat(page.locator("#without-form")).containsText("Grundlage: Made-up 12 days");
    assertThat(page.locator("#without-form")).not().containsText("Beleg fehlt");
  }

  @Test
  void anExpenseWithNeitherADocumentNorAStatedBasisCannotBeSaved() {
    Page page = browser.newPage();
    openANewReturnFor2022(page);

    page.getByLabel("Datum").fill("03.02.2022");
    addExpense(page, "10,00", "Made-up cable");

    assertThat(page.locator(".error")).containsText("die Grundlage angeben");
    assertThat(page.locator("#expenses tbody tr")).hasCount(0);
    assertThat(page.getByLabel("Beschreibung")).hasValue("Made-up cable");
  }

  @Test
  void showsAnErrorForAFileOverTheLimitOfTheServer() {
    Page page = browser.newPage();
    openANewReturnFor2022(page);
    byte[] large = new byte[30 * 1024 * 1024];
    large[0] = '%';

    page.getByLabel("Datum").fill("03.02.2022");
    page.getByLabel("Neue Datei")
        .setInputFiles(new FilePayload("large.pdf", "application/pdf", large));
    page.getByLabel("Betrag (EUR)").fill("10,00");
    page.getByLabel("Beschreibung").fill("Made-up cable");
    page.getByLabel("Beschreibung").press("Enter");

    assertThat(page.locator(".error")).containsText("größer als 20 MB");
    assertThat(page.locator("#expenses tbody tr")).hasCount(0);
  }

  private static void uploadDocument(Page page, String returnUrl, String fileName) {
    page.navigate(returnUrl + "/documents");
    page.getByLabel("Datei").setInputFiles(new FilePayload(fileName, "image/png", MADE_UP_PNG));
    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Hochladen")).click();
    assertThat(page.locator("#documents tbody tr")).hasCount(1);
  }

  private void openANewReturnFor2022(Page page) {
    page.navigate("http://127.0.0.1:" + port + "/");
    // Tests share one database, so each test needs its own person and its own return.
    String lastName = UUID.randomUUID().toString();
    String name = "Made-up " + lastName;
    page.locator("#new-person [name=firstName]").fill("Made-up");
    page.locator("#new-person [name=lastName]").fill(lastName);
    page.locator("#new-person [name=lastName]").press("Enter");
    Locator section = page.locator("section").filter(new Locator.FilterOptions().setHasText(name));
    section
        .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Steuererklärung 2022"))
        .click();
    assertThat(page.getByLabel("Datum")).isVisible();
  }

  /** Writes the row directly, because the form no longer accepts an expense without evidence. */
  private void insertOldExpense(String returnUrl, String topic, String description) {
    long returnId = Long.parseLong(returnUrl.substring(returnUrl.lastIndexOf('/') + 1));
    jdbcClient
        .sql(
            """
            insert into expense(
              tax_return_id, paid_on, amount_cents, topic, description, receipt_reference)
            values (?, '2022-02-03', 1000, ?, ?, '')
            """)
        .params(returnId, topic, description)
        .update();
  }

  private static void addExpenseWithBasis(Page page, String amount, String description) {
    page.getByLabel("Grundlage").fill("Made-up 12 days");
    addExpense(page, amount, description);
  }

  private static void addExpense(Page page, String amount, String description) {
    page.getByLabel("Betrag (EUR)").fill(amount);
    page.getByLabel("Beschreibung").fill(description);
    page.getByLabel("Beschreibung").press("Enter");
    page.waitForCondition(
        () ->
            page.locator(".error").count() > 0
                || page.getByLabel("Beschreibung").inputValue().isEmpty());
    // htmx moves the focus to this field just after the swap. A fill in that moment types into the
    // wrong field, because Playwright focuses a field first and then inserts the text.
    assertThat(page.getByLabel("Betrag (EUR)")).isFocused();
  }
}
