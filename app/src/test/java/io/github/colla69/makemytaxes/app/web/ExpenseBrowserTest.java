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

    page.getByLabel("Date").fill("03.02.2022");
    addExpense(page, "1.234,56", "Made-up laptop");

    assertThat(page.locator("#expenses tbody tr")).hasCount(1);
    assertThat(page.getByLabel("Date")).hasValue("03.02.2022");
    assertThat(page.getByLabel("Amount (EUR)")).isFocused();
    // The link is outside the panel, so it stays only if htmx swapped the panel in place.
    assertThat(page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Back")))
        .isVisible();

    addExpense(page, "10,00", "Made-up cable");

    assertThat(page.locator("#expenses tbody tr")).hasCount(2);
    assertThat(page.locator("#panel")).containsText("1.244,56");
    assertThat(page.locator("#panel")).containsText("2 without a receipt");
  }

  @Test
  void showsAnErrorForADateOutsideTheTaxYear() {
    Page page = browser.newPage();
    openANewReturnFor2022(page);

    page.getByLabel("Date").fill("03.02.2023");
    addExpense(page, "10,00", "Made-up cable");

    assertThat(page.locator(".error")).containsText("03.02.2023");
    assertThat(page.locator("#expenses tbody tr")).hasCount(0);
    assertThat(page.getByLabel("Description")).hasValue("Made-up cable");
  }

  @Test
  void deletesAnExpense() {
    Page page = browser.newPage();
    openANewReturnFor2022(page);
    page.getByLabel("Date").fill("03.02.2022");
    addExpense(page, "10,00", "Made-up cable");
    assertThat(page.locator("#expenses tbody tr")).hasCount(1);

    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Delete")).click();

    assertThat(page.locator("#expenses tbody tr")).hasCount(0);
  }

  @Test
  void deletingAnExpenseKeepsTheEntryInProgress() {
    Page page = browser.newPage();
    openANewReturnFor2022(page);
    page.getByLabel("Date").fill("03.02.2022");
    page.getByLabel("Topic").selectOption("WORK_TRAINING");
    addExpense(page, "10,00", "Made-up course");
    assertThat(page.locator("#expenses tbody tr")).hasCount(1);

    page.getByLabel("Description").fill("Next made-up course");
    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Delete")).click();

    assertThat(page.locator("#expenses tbody tr")).hasCount(0);
    assertThat(page.getByLabel("Topic")).hasValue("WORK_TRAINING");
    assertThat(page.getByLabel("Description")).hasValue("Next made-up course");
    assertThat(page.getByLabel("Amount (EUR)")).hasValue("");
  }

  private void openANewReturnFor2022(Page page) {
    page.navigate("http://127.0.0.1:" + port + "/");
    // Tests share one database, so each test needs its own person and its own return.
    String name = "Made-up " + UUID.randomUUID();
    page.getByLabel("Name").fill(name);
    page.getByLabel("Name").press("Enter");
    Locator section = page.locator("section").filter(new Locator.FilterOptions().setHasText(name));
    section.getByLabel("Tax year").fill("2022");
    section.getByLabel("Tax year").press("Enter");
    section
        .getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName("Return 2022"))
        .click();
    assertThat(page.getByLabel("Date")).isVisible();
  }

  private static void addExpense(Page page, String amount, String description) {
    page.getByLabel("Amount (EUR)").fill(amount);
    page.getByLabel("Description").fill(description);
    page.getByLabel("Description").press("Enter");
    page.waitForCondition(
        () ->
            page.locator(".error").count() > 0
                || page.getByLabel("Description").inputValue().isEmpty());
    // htmx moves the focus to this field just after the swap. A fill in that moment types into the
    // wrong field, because Playwright focuses a field first and then inserts the text.
    assertThat(page.getByLabel("Amount (EUR)")).isFocused();
  }
}
