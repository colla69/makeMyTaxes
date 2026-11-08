package io.github.colla69.makemytaxes.app.web;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Uses a real browser, because only a browser can prove that the htmx swap works. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ExpenseBrowserTest {

  @TempDir static Path databaseFolder;

  private static Playwright playwright;
  private static Browser browser;

  @LocalServerPort private int port;

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
    addExpense(page, "1.234,56", "Made-up laptop");

    assertThat(page.locator("#expenses tbody tr")).hasCount(1);
    assertThat(page.getByLabel("Datum")).hasValue("03.02.2022");
    assertThat(page.getByLabel("Betrag (EUR)")).isFocused();
    // The link is outside the panel, so it stays only if htmx swapped the panel in place.
    assertThat(page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Zurück")))
        .isVisible();

    addExpense(page, "10,00", "Made-up cable");

    assertThat(page.locator("#expenses tbody tr")).hasCount(2);
    assertThat(page.locator("#panel")).containsText("1.244,56");
    assertThat(page.locator("#panel")).containsText("2 ohne Beleg");
  }

  @Test
  void showsAnErrorForADateOutsideTheTaxYear() {
    Page page = browser.newPage();
    openANewReturnFor2022(page);

    page.getByLabel("Datum").fill("03.02.2023");
    addExpense(page, "10,00", "Made-up cable");

    assertThat(page.locator(".error")).containsText("03.02.2023");
    assertThat(page.locator("#expenses tbody tr")).hasCount(0);
    assertThat(page.getByLabel("Beschreibung")).hasValue("Made-up cable");
  }

  @Test
  void deletesAnExpense() {
    Page page = browser.newPage();
    openANewReturnFor2022(page);
    page.getByLabel("Datum").fill("03.02.2022");
    addExpense(page, "10,00", "Made-up cable");
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
    addExpense(page, "10,00", "Made-up course");
    assertThat(page.locator("#expenses tbody tr")).hasCount(1);

    page.getByLabel("Beschreibung").fill("Next made-up course");
    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Löschen")).click();

    assertThat(page.locator("#expenses tbody tr")).hasCount(0);
    assertThat(page.getByLabel("Kategorie")).hasValue("WORK_TRAINING");
    assertThat(page.getByLabel("Beschreibung")).hasValue("Next made-up course");
    assertThat(page.getByLabel("Betrag (EUR)")).hasValue("");
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
    section.getByLabel("Steuerjahr").fill("2022");
    section.getByLabel("Steuerjahr").press("Enter");
    section
        .getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName("Steuererklärung 2022"))
        .click();
    assertThat(page.getByLabel("Datum")).isVisible();
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
