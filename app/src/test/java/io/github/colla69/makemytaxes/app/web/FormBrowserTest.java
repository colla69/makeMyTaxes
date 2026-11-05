package io.github.colla69.makemytaxes.app.web;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.BoundingBox;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** The way through a 2022 return: filing check, forms and the review screen. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FormBrowserTest {

  private static final String MADE_UP_TAX_ID = "86402715392"; // privacy-scan:allow made up
  private static final String MADE_UP_IBAN = "DE02120300000000202051"; // privacy-scan:allow made up

  private static final List<String> QUESTIONS =
      List.of(
          "otherIncome",
          "benefits",
          "severalEmployers",
          "healthAllowance",
          "wageTaxAllowance",
          "specialPayment",
          "certificateS",
          "officeRequest");

  @TempDir static Path databaseFolder;

  private static Playwright playwright;
  private static Browser browser;

  @LocalServerPort private int port;

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("makemytaxes.database.path", () -> databaseFolder.resolve("forms.db").toString());
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
  void filingCheckIsOpenThenVoluntaryThenMandatory() {
    Page page = browser.newPage();
    openANewReturn(page, "2022");
    link(page, "Filing check").click();

    assertThat(page.locator("#filing-result")).containsText("The result is open");

    for (String question : QUESTIONS) {
      field(page, question).selectOption("NO");
    }
    save(page);
    assertThat(page.locator(".saved")).isVisible();
    assertThat(page.locator("#filing-result")).containsText("This return is voluntary");
    assertThat(page.locator("#filing-result")).containsText("31.12.2026");

    field(page, "severalEmployers").selectOption("YES");
    save(page);
    assertThat(page.locator("#filing-result")).containsText("You must file this return");
    assertThat(page.locator("#filing-result")).containsText("§ 46(2) no. 2 EStG");
    assertThat(page.locator("#filing-result")).containsText("was 02.10.2023");
  }

  @Test
  void aWrongValueShowsAnErrorKeepsTheInputAndSavesNothing() {
    Page page = browser.newPage();
    openANewReturn(page, "2022");
    link(page, "Main form").click();

    field(page, "lastName").fill("Made-up");
    field(page, "taxId").fill("12345678901");
    save(page);

    assertThat(page.locator(".field .error")).containsText("11 digits");
    assertThat(field(page, "taxId")).hasValue("12345678901");
    assertThat(field(page, "lastName")).hasValue("Made-up");

    page.reload();
    link(page, "Main form").click();
    assertThat(field(page, "lastName")).hasValue("");
  }

  @Test
  void aSavedValueIsThereAfterAReloadAndAClearedValueIsGone() {
    Page page = browser.newPage();
    openANewReturn(page, "2022");
    link(page, "Anlage N").click();

    field(page, "grossWage").fill("1.234,56");
    save(page);
    assertThat(page.locator(".saved")).isVisible();
    page.reload();
    assertThat(field(page, "grossWage")).hasValue("1.234,56");

    field(page, "grossWage").fill("");
    save(page);
    assertThat(page.locator(".saved")).isVisible();
    assertThat(field(page, "grossWage")).hasValue("");
  }

  @Test
  void tooManyDaysAreRejected() {
    Page page = browser.newPage();
    openANewReturn(page, "2022");
    link(page, "Anlage N").click();

    field(page, "commuteDays").fill("300");
    field(page, "homeOfficeDays").fill("100");
    save(page);

    assertThat(page.locator(".error").first()).containsText("total 400");
    assertThat(field(page, "commuteDays")).hasValue("300");
  }

  @Test
  void reviewShowsTheValuesInLineOrderWithTheirEvidence() {
    Page page = browser.newPage();
    openANewReturn(page, "2022");

    page.getByLabel("Date").fill("03.02.2022");
    page.getByLabel("Amount (EUR)").fill("1.500,00");
    page.getByLabel("Topic").selectOption("WORK_EQUIPMENT");
    page.getByLabel("Description").fill("Made-up laptop");
    page.getByLabel("Receipt").fill("made-up-laptop.pdf");
    page.getByLabel("Receipt").press("Enter");
    assertThat(page.locator("#expenses tbody tr")).hasCount(1);

    link(page, "Main form").click();
    fill(page, "taxOffice", "Made-up Finanzamt");
    fill(page, "taxId", MADE_UP_TAX_ID);
    fill(page, "birthDate", "01.01.1990");
    fill(page, "lastName", "Made-up");
    fill(page, "firstName", "Person");
    fill(page, "street", "Made-up street");
    fill(page, "houseNumber", "1");
    fill(page, "postalCode", "00000");
    fill(page, "city", "Made-up city");
    fill(page, "occupation", "Made-up job");
    fill(page, "iban", MADE_UP_IBAN);
    save(page);
    assertThat(page.locator(".saved")).isVisible();

    link(page, "Anlage N").click();
    fill(page, "taxClass", "1");
    fill(page, "grossWage", "40.000,00");
    fill(page, "commuteDays", "10");
    fill(page, "commuteKm", "30");
    field(page, "commuteTransport").selectOption("CAR");
    fill(page, "homeOfficeDays", "20");
    save(page);
    assertThat(page.locator(".saved")).isVisible();

    link(page, "Review").click();

    Locator main = page.locator(".review-form").filter(new Locator.FilterOptions().setHasText("Main form"));
    Locator anlageN = page.locator(".review-form").filter(new Locator.FilterOptions().setHasText("Anlage N"));
    assertThat(main).not().containsText("Missing");
    assertThat(main).containsText("Made-up Finanzamt");
    assertThat(anlageN).containsText("Missing");
    assertThat(anlageN.locator(".warning")).containsText("Lohnsteuer");
    assertThat(anlageN).containsText("40.000,00");
    assertThat(anlageN).containsText("Own car");

    Locator laptop = anlageN.locator("tr").filter(new Locator.FilterOptions().setHasText("Made-up laptop"));
    assertThat(laptop.locator("td").nth(0)).hasText("42");
    assertThat(laptop).containsText("1.500,00");
    assertThat(laptop).containsText("Paid on 03.02.2022, receipt: made-up-laptop.pdf");
    assertThat(anlageN.locator("tbody tr td:first-child"))
        .hasText(new String[] {"5", "6", "35", "35", "35", "42", "42", "45"});

    // Commuting 10 x (20 x 0,30 + 10 x 0,38) = 98,00, home office 20 x 5,00 = 100,00.
    Locator estimate = page.locator("#estimate");
    assertThat(estimate).containsText("98,00");
    assertThat(estimate).containsText("100,00");
    assertThat(estimate).containsText("1.698,00");
    assertThat(estimate).containsText("1.200,00");
    assertThat(estimate).containsText("above the lump sum, so they count in full");
  }

  @Test
  void aCostWithoutAFormStaysVisibleOnTheReview() {
    Page page = browser.newPage();
    openANewReturn(page, "2022");
    page.getByLabel("Date").fill("03.02.2022");
    page.getByLabel("Amount (EUR)").fill("500,00");
    page.getByLabel("Topic").selectOption("HOUSEHOLD_SERVICES");
    page.getByLabel("Description").fill("Made-up cleaner");
    page.getByLabel("Description").press("Enter");
    assertThat(page.locator("#expenses tbody tr")).hasCount(1);

    link(page, "Review").click();

    assertThat(page.locator("#without-form")).containsText("Made-up cleaner");
    assertThat(page.locator("#without-form")).containsText("500,00");
  }

  @Test
  void reviewShowsATypedAmountInTheFormatOfTheApp() {
    Page page = browser.newPage();
    openANewReturn(page, "2022");
    link(page, "Anlage N").click();
    fill(page, "grossWage", "1234,5");
    save(page);
    assertThat(page.locator(".saved")).isVisible();

    link(page, "Review").click();

    assertThat(page.locator(".review-form").filter(new Locator.FilterOptions().setHasText("Anlage N")))
        .containsText("1.234,50");
  }

  @Test
  void aReturnWithoutAYearPackHasNoFormsYet() {
    Page page = browser.newPage();
    openANewReturn(page, "2021");

    assertThat(page.getByLabel("Date")).isVisible();
    assertThat(link(page, "Review")).hasCount(0);
    assertThat(link(page, "Anlage N")).hasCount(0);
  }

  @Test
  void everyFieldOnEveryPageHasTheSameLook() {
    Page page = browser.newPage();
    openANewReturn(page, "2022");
    BoundingBox reference = page.locator(".field input").first().boundingBox();

    assertAllFieldsLookLike(reference, page);
    for (String form : List.of("Main form", "Anlage N", "Filing check")) {
      link(page, form).click();
      assertThat(page.locator("#answers")).isVisible();
      assertAllFieldsLookLike(reference, page);
    }
    link(page, "Back").click();
    assertThat(page.getByLabel("Name")).isVisible();
    assertAllFieldsLookLike(reference, page);
  }

  @Test
  void theFieldComponentKeepsTheSettingsOfEachField() {
    Page page = browser.newPage();
    page.navigate("http://127.0.0.1:" + port + "/");
    assertThat(page.getByLabel("Name")).hasAttribute("required", "required");

    openANewReturn(page, "2022");
    assertThat(page.getByLabel("Date")).hasAttribute("placeholder", "dd.mm.yyyy");
    assertThat(page.getByLabel("Date")).hasAttribute("required", "required");
    assertThat(page.getByLabel("Receipt")).not().hasAttribute("required", "required");
    assertThat(page.getByLabel("Topic")).hasValue("WORK_EQUIPMENT");

    link(page, "Filing check").click();
    assertThat(field(page, "otherIncome").locator("option").first()).hasText("Choose");
    assertThat(page.locator("#answers .field .line").first()).not().isEmpty();

    link(page, "Main form").click();
    assertThat(page.locator("#answers .field .help").first()).not().isEmpty();

    link(page, "Back").click();
    Locator taxYear = page.getByLabel("Tax year").first();
    assertThat(taxYear).hasAttribute("type", "number");
    assertThat(taxYear).hasAttribute("min", "2000");
    assertThat(taxYear).hasAttribute("max", "2100");
  }

  private static void assertAllFieldsLookLike(BoundingBox reference, Page page) {
    assertEquals(
        page.locator("form input, form select").count(),
        page.locator(".field input, .field select").count(),
        "A control is outside the field component on " + page.url());
    for (Locator control : page.locator(".field input, .field select").all()) {
      BoundingBox box = control.boundingBox();
      assertEquals(reference.x, box.x, "left edge on " + page.url());
      assertEquals(reference.width, box.width, "width on " + page.url());
      assertEquals(reference.height, box.height, "height on " + page.url());
    }
  }

  private void openANewReturn(Page page, String year) {
    page.navigate("http://127.0.0.1:" + port + "/");
    String name = "Made-up " + UUID.randomUUID();
    page.getByLabel("Name").fill(name);
    page.getByLabel("Name").press("Enter");
    Locator section = page.locator("section").filter(new Locator.FilterOptions().setHasText(name));
    section.getByLabel("Tax year").fill(year);
    section.getByLabel("Tax year").press("Enter");
    section
        .getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName("Return " + year))
        .click();
    assertThat(page.getByLabel("Date")).isVisible();
  }

  private static Locator link(Page page, String name) {
    return page.locator("nav").getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName(name));
  }

  private static Locator field(Page page, String name) {
    return page.locator("#answers [name=" + name + "]");
  }

  private static void fill(Page page, String name, String value) {
    field(page, name).fill(value);
  }

  private static void save(Page page) {
    page.locator("#answers").getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Save")).click();
  }
}
