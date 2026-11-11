package io.github.colla69.makemytaxes.app.web;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.BoundingBox;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
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

  /** The browser rounds column widths, so two equal columns can differ by a part of a pixel. */
  private static final double LAYOUT_ROUNDING = 0.1;

  private static final String NO_RULES = "Für dieses Jahr gibt es noch keine Regeln.";

  /** The tabs of the forms with fields. */
  private static final List<String> FORM_TABS =
      List.of(
          "Hauptvordruck",
          "Anlage N",
          "Anlage Vorsorgeaufwand",
          "Haushaltsnahe Aufwendungen",
          "Abgabepflicht");

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
    link(page, "Abgabepflicht").click();

    assertThat(page.locator("#filing-result")).containsText("Das Ergebnis ist offen");

    for (String question : QUESTIONS) {
      field(page, question).selectOption("NO");
    }
    save(page);
    assertThat(page.locator(".saved")).isVisible();
    assertThat(page.locator("#filing-result")).containsText("Diese Erklärung ist freiwillig");
    assertThat(page.locator("#filing-result")).containsText("31.12.2026");

    field(page, "severalEmployers").selectOption("YES");
    save(page);
    assertThat(page.locator("#filing-result")).containsText("Sie müssen diese Erklärung abgeben");
    assertThat(page.locator("#filing-result")).containsText("§ 46 Abs. 2 Nr. 2 EStG");
    assertThat(page.locator("#filing-result")).containsText("endete am 02.10.2023");
  }

  @Test
  void aFormWithAllRequiredAnswersSavedHasAGreenTab() {
    Page page = browser.newPage();
    openANewReturn(page, "2022");
    link(page, "Abgabepflicht").click();
    assertThat(link(page, "Abgabepflicht")).not().hasClass(Pattern.compile("\\bdone\\b"));

    for (String question : QUESTIONS) {
      field(page, question).selectOption("NO");
    }
    save(page);
    assertThat(page.locator(".saved")).isVisible();
    link(page, "Anlage N").click();

    assertTrue(isGreen(link(page, "Abgabepflicht")), "The done form tab is not green");
    assertTrue(!isGreen(link(page, "Hauptvordruck")), "A form without answers has a green tab");
    assertTrue(!isGreen(link(page, "Anlage N")), "The open tab is green");
  }

  @Test
  void aWrongValueShowsItsErrorAtTheFieldAndTheValidFieldsStillSave() {
    Page page = browser.newPage();
    openANewReturn(page, "2022");
    link(page, "Hauptvordruck").click();

    field(page, "occupation").fill("Made-up");
    field(page, "taxId").fill("12345678901");
    field(page, "taxId").press("Tab");

    assertThat(page.locator(".field:has([name=taxId]) .error")).containsText("11 Ziffern");
    assertThat(field(page, "taxId")).hasValue("12345678901");

    // A new page shows only what the database holds.
    Page other = browser.newPage();
    other.navigate(page.url());
    assertThat(field(other, "occupation")).hasValue("Made-up");
    assertThat(field(other, "taxId")).hasValue("");
  }

  @Test
  void eachFieldSavesWhenTheUserLeavesItWithoutAClickOnSpeichern() {
    Page page = browser.newPage();
    openANewReturn(page, "2022");

    link(page, "Anlage N").click();
    fill(page, "grossWage", "2.345,67");
    field(page, "commuteTransport").selectOption("CAR");
    link(page, "Hauptvordruck").click();
    fill(page, "lastName", "Made-up");
    link(page, "Anlage N").click();
    // The link waits for the open save, so the next page comes a moment later.
    assertThat(field(page, "grossWage")).isVisible();

    page.reload();
    assertThat(field(page, "grossWage")).hasValue("2.345,67");
    assertThat(field(page, "commuteTransport")).hasValue("CAR");
    link(page, "Hauptvordruck").click();
    assertThat(field(page, "lastName")).hasValue("Made-up");
  }

  @Test
  void anUnsavedExpenseIsNotKeptInTheBrowser() {
    Page page = browser.newPage();
    openANewReturn(page, "2022");

    page.getByLabel("Beschreibung").fill("Made-up chair");
    link(page, "Anlage N").click();
    link(page, "Ausgaben").click();
    assertThat(page.getByLabel("Beschreibung")).hasValue("");
  }

  @Test
  void aFormattedAmountStaysFormattedAfterATabSwitch() {
    Page page = browser.newPage();
    openANewReturn(page, "2022");
    link(page, "Anlage N").click();
    Locator amount = field(page, "grossWage");
    amount.pressSequentially("1234,5");
    amount.press("Tab");
    assertThat(amount).hasValue("1.234,50");

    link(page, "Hauptvordruck").click();
    link(page, "Anlage N").click();
    assertThat(field(page, "grossWage")).hasValue("1.234,50");
  }

  @Test
  void aDayFromTheCalendarStaysAfterATabSwitch() {
    Page page = browser.newPage();
    openANewReturn(page, "2022");
    link(page, "Hauptvordruck").click();
    page.locator(".field:has([name=birthDate]) button.calendar").click();
    page.locator("#calendar-picker").fill("1990-12-24");
    assertThat(field(page, "birthDate")).hasValue("24.12.1990");

    link(page, "Anlage N").click();
    link(page, "Hauptvordruck").click();
    assertThat(field(page, "birthDate")).hasValue("24.12.1990");
  }

  @Test
  void anAddedExpenseDoesNotComeBackAfterATabSwitch() {
    Page page = browser.newPage();
    openANewReturn(page, "2022");

    page.getByLabel("Datum").fill("03.02.2022");
    page.getByLabel("Betrag (EUR)").fill("10,00");
    page.getByLabel("Beschreibung").fill("Made-up cable");
    page.getByLabel("Grundlage").fill("Made-up 12 days");
    page.getByLabel("Beschreibung").press("Enter");
    assertThat(page.locator("#expenses tbody tr")).hasCount(1);

    link(page, "Anlage N").click();
    link(page, "Ausgaben").click();
    assertThat(page.getByLabel("Betrag (EUR)")).hasValue("");
    assertThat(page.getByLabel("Beschreibung")).hasValue("");
  }

  @Test
  void clearingTheExpenseTabKeepsAKategorieSoAnExpenseCanStillBeAdded() {
    Page page = browser.newPage();
    openANewReturn(page, "2022");

    page.getByLabel("Kategorie").selectOption("WORK_EQUIPMENT");
    page.getByLabel("Beschreibung").fill("Made-up cable");
    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Alle Felder leeren"))
        .click();
    assertThat(page.getByLabel("Beschreibung")).hasValue("");
    assertThat(page.getByLabel("Kategorie")).not().hasValue("");

    page.getByLabel("Datum").fill("03.02.2022");
    page.getByLabel("Betrag (EUR)").fill("10,00");
    page.getByLabel("Beschreibung").fill("Made-up cable");
    page.getByLabel("Grundlage").fill("Made-up 12 days");
    page.getByLabel("Beschreibung").press("Enter");
    assertThat(page.locator("#expenses tbody tr")).hasCount(1);
  }

  @Test
  void aClearButtonEmptiesOneFieldAndAnotherEmptiesTheTab() {
    Page page = browser.newPage();
    openANewReturn(page, "2022");
    link(page, "Anlage N").click();

    fill(page, "grossWage", "2.345,67");
    fill(page, "commuteKm", "12");
    clearButton(page, "grossWage").click();
    assertThat(field(page, "grossWage")).hasValue("");
    assertThat(field(page, "commuteKm")).hasValue("12");

    fill(page, "grossWage", "2.345,67");
    field(page, "commuteTransport").selectOption("CAR");
    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Alle Felder leeren"))
        .click();
    assertThat(field(page, "grossWage")).hasValue("");
    assertThat(field(page, "commuteKm")).hasValue("");
    assertThat(field(page, "commuteTransport")).hasValue("");

    link(page, "Hauptvordruck").click();
    link(page, "Anlage N").click();
    assertThat(field(page, "grossWage")).hasValue("");
  }

  @Test
  void tabGoesFromOneFieldToTheNextAndSkipsTheClearButton() {
    Page page = browser.newPage();
    openANewReturn(page, "2022");

    page.getByLabel("Datum").focus();
    page.getByLabel("Datum").press("Tab");
    assertThat(page.getByLabel("Betrag (EUR)")).isFocused();
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

    assertThat(page.locator(".error").first()).containsText("zusammen 400");
    assertThat(field(page, "commuteDays")).hasValue("300");
  }

  @Test
  void reviewShowsTheValuesInLineOrderWithTheirEvidence() {
    Page page = browser.newPage();
    openANewReturn(page, "2022");

    page.getByLabel("Datum").fill("03.02.2022");
    page.getByLabel("Betrag (EUR)").fill("1.500,00");
    page.getByLabel("Kategorie").selectOption("WORK_EQUIPMENT");
    page.getByLabel("Beschreibung").fill("Made-up laptop");
    page.getByLabel("Grundlage").fill("Made-up 12 days");
    page.getByLabel("Grundlage").press("Enter");
    assertThat(page.locator("#expenses tbody tr")).hasCount(1);

    link(page, "Hauptvordruck").click();
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

    link(page, "Übersicht").click();

    Locator main = page.locator(".review-form").filter(new Locator.FilterOptions().setHasText("Hauptvordruck"));
    Locator anlageN = page.locator(".review-form").filter(new Locator.FilterOptions().setHasText("Anlage N"));
    assertThat(main).not().containsText("Es fehlt");
    assertThat(main).containsText("Made-up Finanzamt");
    assertThat(anlageN).containsText("Es fehlt");
    assertThat(anlageN.locator(".warning")).containsText("Lohnsteuer");
    assertThat(anlageN).containsText("40.000,00");
    assertThat(anlageN).containsText("Eigener oder zur Nutzung überlassener Kraftwagen");

    Locator laptop = anlageN.locator("tr").filter(new Locator.FilterOptions().setHasText("Made-up laptop"));
    assertThat(laptop.locator("td").nth(0)).hasText("42");
    assertThat(laptop).containsText("1.500,00");
    assertThat(laptop).containsText("Made-up laptop, bezahlt am 03.02.2022");
    assertThat(laptop.locator(".source")).hasText("Grundlage: Made-up 12 days");
    assertThat(
            main.locator("tr").filter(new Locator.FilterOptions().setHasText("Made-up Finanzamt"))
                .locator(".source"))
        .hasText("eigene Angabe");
    assertThat(anlageN.locator("tbody tr td:first-child"))
        .hasText(new String[] {"5", "6", "35", "35", "35", "42", "42", "45"});

    // Commuting 10 x (20 x 0,30 + 10 x 0,38) = 98,00, home office 20 x 5,00 = 100,00.
    Locator estimate = page.locator("#estimate");
    assertThat(estimate).containsText("98,00");
    assertThat(estimate).containsText("100,00");
    assertThat(estimate).containsText("1.698,00");
    assertThat(estimate).containsText("1.200,00");
    assertThat(estimate).containsText("Sie zählen daher in voller Höhe");

    // Each rate and limit comes from the year pack and names its source.
    assertThat(estimate.locator("#limit-commute")).containsText("0,30 EUR");
    assertThat(estimate.locator("#limit-commute")).containsText("0,38 EUR");
    assertThat(estimate.locator("#limit-commute")).containsText("4.500,00 EUR");
    assertThat(estimate.locator("#limit-commute")).containsText("§ 9 Abs. 1 Satz 3 Nr. 4");
    assertThat(estimate.locator("#limit-commute")).containsText("§ 40 Abs. 2 Satz 2 Nr. 1 EStG");
    assertThat(estimate.locator("#limit-home-office")).containsText("5,00 EUR");
    assertThat(estimate.locator("#limit-home-office")).containsText("600,00 EUR");
    assertThat(estimate.locator("#limit-home-office")).containsText("§ 4 Abs. 5 Satz 1 Nr. 6b");
    assertThat(estimate.locator("#limit-home-office")).containsText("Tag nur zu Hause");
    assertThat(estimate.locator("#union-fees")).hasCount(0);
    assertThat(estimate).containsText("§ 9a Satz 1 Nr. 1 Buchstabe a EStG");
  }

  /** The lines come from the official forms in law/forms/2024. */
  @Test
  void aReturnOf2024HasAllTabsAndAFilledAnlageNReachesTheReview() {
    Page page = browser.newPage();
    openANewReturn(page, "2024");
    for (String form : FORM_TABS) {
      assertThat(link(page, form)).hasCount(1);
    }

    page.getByLabel("Datum").fill("05.03.2024");
    page.getByLabel("Betrag (EUR)").fill("1.500,00");
    page.getByLabel("Kategorie").selectOption("WORK_EQUIPMENT");
    page.getByLabel("Beschreibung").fill("Made-up laptop");
    page.getByLabel("Grundlage").fill("Made-up 12 days");
    page.getByLabel("Grundlage").press("Enter");
    assertThat(page.locator("#expenses tbody tr")).hasCount(1);

    link(page, "Hauptvordruck").click();
    fill(page, "taxOffice", "Made-up Finanzamt");
    fill(page, "occupation", "Made-up job");
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
    // The Italian hint must not state the 2022 rule (days only at home) for 2024.
    Locator homeOfficeHint =
        page.locator(".field")
            .filter(new Locator.FilterOptions().setHas(page.locator("[name=homeOfficeDays]")))
            .locator(".hint-text");
    assertThat(homeOfficeHint).not().containsText("solo da casa");
    assertThat(homeOfficeHint).containsText("dipende dall'anno");

    link(page, "Übersicht").click();

    assertThat(page.locator("#form-year")).hasText("2024");
    Locator main = page.locator(".review-form").filter(new Locator.FilterOptions().setHasText("Hauptvordruck"));
    Locator anlageN = page.locator(".review-form").filter(new Locator.FilterOptions().setHasText("Anlage N"));
    assertThat(main.locator("tbody tr td:first-child")).hasText(new String[] {"5", "9", "10", "12"});
    assertThat(anlageN.locator("tbody tr td:first-child"))
        .hasText(new String[] {"4", "5", "32", "33", "34 bis 36", "57", "57", "61"});

    // Commuting 10 x (20 x 0,30 + 10 x 0,38) = 98,00, home office 20 x 6,00 = 120,00.
    Locator estimate = page.locator("#estimate");
    assertThat(estimate).containsText("98,00");
    assertThat(estimate).containsText("120,00");
    assertThat(estimate).containsText("1.718,00");
    assertThat(estimate).containsText("1.230,00");
    assertThat(estimate.locator("#limit-home-office")).containsText("6,00 EUR");
    assertThat(estimate.locator("#limit-home-office")).containsText("1.260,00 EUR");
    assertThat(estimate.locator("#limit-home-office")).containsText("§ 4 Abs. 5 Satz 1 Nr. 6c");
    assertThat(estimate.locator("#limit-home-office")).containsText("Anlage N Zeile 61");
    assertThat(estimate.locator("#limit-home-office"))
        .containsText("Zeile 62, die die App nicht abdeckt");
  }

  @Test
  void aCostWithoutAFormStaysVisibleOnTheReview() {
    Page page = browser.newPage();
    openANewReturn(page, "2022");
    page.getByLabel("Datum").fill("03.02.2022");
    page.getByLabel("Betrag (EUR)").fill("500,00");
    page.getByLabel("Kategorie").selectOption("HOUSEHOLD_SERVICES");
    page.getByLabel("Beschreibung").fill("Made-up cleaner");
    page.getByLabel("Grundlage").fill("Made-up 12 days");
    page.getByLabel("Beschreibung").press("Enter");
    assertThat(page.locator("#expenses tbody tr")).hasCount(1);

    link(page, "Übersicht").click();

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

    link(page, "Übersicht").click();

    assertThat(page.locator(".review-form").filter(new Locator.FilterOptions().setHasText("Anlage N")))
        .containsText("1.234,50");
  }

  @Test
  void a2026ReturnHasAllTabsAndAFilledAnlageNReachesTheReviewWithoutLines() {
    Page page = browser.newPage();
    openANewReturn(page, "2026");

    for (String tab : List.of("Fragen", "Belege", "Übersicht")) {
      assertThat(link(page, tab)).hasCount(1);
    }
    for (String form : FORM_TABS) {
      link(page, form).click();
      assertThat(page.locator("#answers")).isVisible();
      assertThat(page.locator("#lines-unknown")).containsText("keine Zeilennummern");
      // A form line is a number. A legal source such as "§ 35a Abs. 2 EStG" can stand there too.
      for (String line : page.locator("#answers .line").allTextContents()) {
        assertTrue(line.startsWith("§"), "A line number on the 2026 form " + form + ": " + line);
      }
      // The hints have no year, so they must not name a certificate line ("punto 3").
      for (String hint : page.locator(".field .hint-text").allTextContents()) {
        assertFalse(hint.contains("punt"), "A certificate line in a hint on " + form + ": " + hint);
      }
    }
    assertThat(page.locator("#answers")).containsText("mit mehr als 410,00 Euro erstattet");
    assertThat(page.locator("#answers")).containsText("13.614,00");

    link(page, "Anlage N").click();
    fill(page, "taxClass", "1");
    fill(page, "grossWage", "40.000,00");
    fill(page, "commuteDays", "10");
    fill(page, "commuteKm", "30");
    field(page, "commuteTransport").selectOption("CAR");
    fill(page, "homeOfficeDays", "20");
    save(page);
    assertThat(page.locator(".saved")).isVisible();

    link(page, "Übersicht").click();

    assertThat(page.locator("#lines-unknown")).containsText("keine Zeilennummern");
    Locator anlageN =
        page.locator(".review-form").filter(new Locator.FilterOptions().setHasText("Anlage N"));
    assertThat(anlageN).containsText("40.000,00");
    assertThat(anlageN.locator("tbody tr td:first-child"))
        .hasText(new String[] {"", "", "", "", "", ""});

    // Commuting 10 x 30 x 0,38 = 114,00, home office 20 x 6,00 = 120,00.
    Locator estimate = page.locator("#estimate");
    assertThat(estimate).containsText("114,00");
    assertThat(estimate).containsText("120,00");
    assertThat(estimate).containsText("1.230,00");
    assertThat(estimate.locator("#limit-commute")).containsText("§ 9 Abs. 1 Satz 3 Nr. 4 Satz 2 EStG.");
    // § 9(1) sentence 3 no. 4 sentence 2 EStG, versions 30.06.2026 and 05.11.2026: one rate.
    assertThat(estimate.locator("#limit-commute"))
        .containsText("Wege zur Arbeit: 0,38 EUR je vollen Kilometer, für jeden Tag");
    assertThat(estimate.locator("#limit-commute")).not().containsText("21. Kilometer");
    assertThat(estimate.locator("#limit-home-office")).containsText("Nr. 6c Satz 1 und 2 EStG");
    assertThat(estimate.locator("#limit-home-office")).containsText("1.260,00 EUR");
    assertThat(estimate.locator("#limit-home-office")).containsText("§ 4 Abs. 5 Satz 1 Nr. 6c");
    // § 4(5) no. 6c sentences 1 and 2 and § 9a sentence 3 EStG, versions 30.06.2026 and 05.11.2026.
    assertThat(estimate.locator("#limit-home-office")).containsText("überwiegend zu Hause");
    assertThat(estimate.locator("#limit-home-office")).not().containsText("nur zu Hause");
    assertThat(estimate.locator("#union-fees")).containsText("§ 9a Satz 3 EStG");
  }

  @Test
  void aReturnWithoutAYearPackHasNoFormsYet() {
    Page page = browser.newPage();
    openANewReturn(page, "2023");

    assertThat(page.getByLabel("Datum")).isVisible();
    assertThat(link(page, "Übersicht")).hasCount(0);
    assertThat(link(page, "Anlage N")).hasCount(0);
    assertThat(link(page, "Fragen")).hasCount(0);
    assertThat(page.getByText(NO_RULES)).isVisible();
    link(page, "Belege").click();
    assertThat(page.getByText(NO_RULES)).isVisible();
  }

  @Test
  void aReturnOfAYearWithADefinitionDoesNotSayItHasNoRules() {
    Page page = browser.newPage();
    openANewReturn(page, "2022");

    assertThat(link(page, "Anlage N")).hasCount(1);
    assertThat(page.getByText(NO_RULES)).hasCount(0);
  }

  /** The lines come from Anlage N 2025, which {@code Definition2025Test} names. */
  @Test
  void aReturnFor2025ShowsAllTabsAndAFilledAnlageNReachesTheReview() {
    Page page = browser.newPage();
    openANewReturn(page, "2025");

    assertThat(page.getByText(NO_RULES)).hasCount(0);
    for (String form : FORM_TABS) {
      assertThat(link(page, form)).hasCount(1);
    }
    assertThat(link(page, "Übersicht")).hasCount(1);

    link(page, "Anlage N").click();
    fill(page, "employerName", "Made-up GmbH");
    fill(page, "taxClass", "1");
    fill(page, "grossWage", "40.000,00");
    fill(page, "wageTax", "5.000,00");
    fill(page, "commuteDays", "10");
    fill(page, "commuteKm", "30");
    field(page, "commuteTransport").selectOption("CAR");
    fill(page, "homeOfficeDays", "20");
    save(page);
    assertThat(page.locator(".saved")).isVisible();

    link(page, "Übersicht").click();

    assertThat(page.locator("#form-year")).hasText("2025");
    Locator anlageN =
        page.locator(".review-form").filter(new Locator.FilterOptions().setHasText("Anlage N"));
    assertThat(anlageN).not().containsText("Es fehlt");
    assertThat(anlageN).containsText("Made-up GmbH");
    assertThat(anlageN.locator("tbody tr td:first-child"))
        .hasText(new String[] {"", "4", "5", "6", "29", "30", "31 bis 33", "58"});

    // Home office 20 x 6,00 = 120,00 under § 4(5) no. 6c EStG.
    Locator estimate = page.locator("#estimate");
    assertThat(estimate).containsText("120,00");
    assertThat(estimate.locator("#limit-home-office")).containsText("6,00 EUR");
    assertThat(estimate.locator("#limit-home-office")).containsText("1.260,00 EUR");
    assertThat(estimate.locator("#limit-home-office")).containsText("§ 4 Abs. 5 Satz 1 Nr. 6c");
    assertThat(estimate.locator("#limit-home-office"))
        .containsText("Zeile 59, die die App nicht abdeckt");
    assertThat(estimate).containsText("1.230,00");
  }

  @Test
  void everyFieldOnEveryPageHasTheSameLook() {
    Page page = browser.newPage();
    openANewReturn(page, "2022");
    BoundingBox reference = page.locator(".field input").first().boundingBox();
    List<Double> columns = leftEdges(page);

    assertAllFieldsLookLike(reference, columns, page);
    for (String form : FORM_TABS) {
      link(page, form).click();
      assertThat(page.locator("#answers")).isVisible();
      assertAllFieldsLookLike(reference, columns, page);
    }
    link(page, "Zurück").click();
    assertThat(page.locator("#new-person [name=lastName]")).isVisible();
    assertAllFieldsLookLike(reference, columns, page);
  }

  @Test
  void thePagesOfAReturnAreTabsAndTheOpenPageIsMarked() {
    Page page = browser.newPage();
    openANewReturn(page, "2022");
    assertOnlyOpenTab(page, "Ausgaben");

    for (String tab :
        List.of(
            "Fragen",
            "Hauptvordruck",
            "Anlage N",
            "Anlage Vorsorgeaufwand",
            "Haushaltsnahe Aufwendungen",
            "Abgabepflicht",
            "Übersicht")) {
      link(page, tab).click();
      assertOnlyOpenTab(page, tab);
    }
  }

  @Test
  void thePageUsesEightyPercentOfTheVisibleWidth() {
    Page page = browser.newPage();
    page.navigate("http://127.0.0.1:" + port + "/");
    assertBodyIsEightyPercentOfTheWindow(page);

    openANewReturn(page, "2022");
    assertBodyIsEightyPercentOfTheWindow(page);

    link(page, "Hauptvordruck").click();
    assertBodyIsEightyPercentOfTheWindow(page);

    link(page, "Übersicht").click();
    assertBodyIsEightyPercentOfTheWindow(page);
  }

  @Test
  void theFieldsStandInThreeColumns() {
    Page page = browser.newPage();
    openANewReturn(page, "2022");
    assertEquals(3, leftEdges(page).size(), "columns on " + page.url());

    for (String form : FORM_TABS) {
      link(page, form).click();
      assertThat(page.locator("#answers")).isVisible();
      assertEquals(3, leftEdges(page).size(), "columns on " + page.url());
    }
  }

  @Test
  void theControlsOfOneRowStandAtTheSameHeight() {
    Page page = browser.newPage();
    openANewReturn(page, "2022");

    // The labels of the filing check differ in length, so they wrap to a different number of lines.
    link(page, "Abgabepflicht").click();
    assertThat(page.locator("#answers")).isVisible();
    List<Locator> controls = page.locator("#answers select").all();
    for (int i = 0; i < controls.size(); i++) {
      double rowTop = controls.get(i - i % 3).boundingBox().y;
      assertEquals(rowTop, controls.get(i).boundingBox().y, LAYOUT_ROUNDING, "question " + i);
    }
  }

  @Test
  void theFieldsOfOneSectionStayTogether() {
    Page page = browser.newPage();
    openANewReturn(page, "2022");

    link(page, "Hauptvordruck").click();
    assertThat(page.locator("#answers .field-group h2"))
        .hasText(
            new String[] {
              "Allgemeine Angaben",
              "Bankverbindung",
              "Einkommensersatzleistungen",
              "Ergänzende Angaben zur Steuererklärung"
            });
    assertEquals(List.of("iban"), namesInGroup(page, "Bankverbindung"));
    assertGroupsDoNotOverlap(page);

    link(page, "Anlage N").click();
    assertThat(page.locator("#answers .field-group h2"))
        .hasText(new String[] {"Arbeitgeber 1", "Werbungskosten"});
    assertEquals(
        List.of("workplace", "commuteDays", "commuteKm", "commuteTransport", "homeOfficeDays"),
        namesInGroup(page, "Werbungskosten"));
    assertGroupsDoNotOverlap(page);
  }

  @Test
  void theFieldComponentKeepsTheSettingsOfEachField() {
    Page page = browser.newPage();
    page.navigate("http://127.0.0.1:" + port + "/");
    assertThat(page.locator("#new-person [name=lastName]")).hasAttribute("required", "required");
    assertThat(page.locator("#new-person [name=firstName]")).hasAttribute("required", "required");

    openANewReturn(page, "2022");
    assertThat(page.getByLabel("Datum")).hasAttribute("placeholder", "TT.MM.JJJJ");
    assertThat(page.getByLabel("Datum")).hasAttribute("required", "required");
    assertThat(page.getByLabel("Beleg")).not().hasAttribute("required", "required");
    assertThat(page.getByLabel("Kategorie")).hasValue("WORK_EQUIPMENT");

    link(page, "Abgabepflicht").click();
    assertThat(field(page, "otherIncome").locator("option").first()).hasText("Bitte wählen");
    assertThat(page.locator("#answers .field .line").first()).not().isEmpty();

    link(page, "Hauptvordruck").click();
    assertThat(page.locator("#answers .field .help").first()).not().isEmpty();
  }

  @Test
  void everyFieldOnEveryPageHasAHint() {
    Page page = browser.newPage();
    page.navigate("http://127.0.0.1:" + port + "/");
    assertEveryFieldHasAHint(page);

    openANewReturn(page, "2022");
    assertEveryFieldHasAHint(page);
    for (String form : FORM_TABS) {
      link(page, form).click();
      assertThat(page.locator("#answers")).isVisible();
      assertEveryFieldHasAHint(page);
    }
  }

  @Test
  void theHintShowsItsItalianTextOnHover() {
    Page page = browser.newPage();
    openANewReturn(page, "2022");
    link(page, "Anlage N").click();
    Locator hint =
        page.locator(".field")
            .filter(new Locator.FilterOptions().setHas(page.locator("[name=grossWage]")))
            .locator(".hint");

    assertThat(hint).hasText(Pattern.compile("^\\?"));
    assertThat(hint.locator(".hint-text")).isHidden();

    hint.hover();
    assertThat(hint.locator(".hint-text")).isVisible();
    assertThat(hint.locator(".hint-text")).containsText("salario lordo");
    // The hint must not become a part of the name of the field.
    assertThat(field(page, "grossWage")).not().hasAccessibleName(Pattern.compile("salario|\\?"));
  }

  @Test
  void aLongOptionDoesNotMakeAFieldWiderThanTheOthers() {
    Page page = browser.newPage();
    openANewReturn(page, "2022");
    // A large text stands for a wide font: the width of an option depends on the fonts of the machine.
    page.addStyleTag(new Page.AddStyleTagOptions().setContent(".field select { font-size: 2rem; }"));

    assertEquals(
        page.getByLabel("Datum").boundingBox().width,
        page.getByLabel("Kategorie").boundingBox().width,
        LAYOUT_ROUNDING);
  }

  @Test
  void aDateFieldTakesOnlyDigitsAndPutsTheDotsIn() {
    Page page = browser.newPage();
    openANewReturn(page, "2022");
    Locator date = page.getByLabel("Datum");

    date.pressSequentially("03a02b2022");
    assertThat(date).hasValue("03.02.2022");

    date.fill("");
    date.pressSequentially("3.2.2022");
    assertThat(date).hasValue("3.2.2022");
  }

  @Test
  void anEditInTheMiddleOfADateDoesNotMoveItsDigits() {
    Page page = browser.newPage();
    openANewReturn(page, "2022");
    Locator date = page.getByLabel("Datum");
    date.pressSequentially("03022022");

    date.press("Home");
    for (int i = 0; i < 3; i++) {
      date.press("ArrowRight");
    }
    date.pressSequentially("1");
    assertThat(date).hasValue("03.102.2022");

    date.press("Backspace");
    date.press("Backspace");
    assertThat(date).hasValue("0302.2022");
  }

  @Test
  void anAmountFieldTakesDigitsAndOneCommaAndShowsTheFormatOfTheAppOnLeaving() {
    Page page = browser.newPage();
    openANewReturn(page, "2022");
    link(page, "Anlage N").click();
    Locator amount = field(page, "grossWage");

    amount.pressSequentially("1234,5x6");
    assertThat(amount).hasValue("1234,56");
    amount.press("Tab");
    assertThat(amount).hasValue("1.234,56");
  }

  @Test
  void anAmountMaskKeepsEverySeparatorAndDigit() {
    Page page = browser.newPage();
    openANewReturn(page, "2022");
    link(page, "Anlage N").click();
    Locator amount = field(page, "grossWage");

    amount.pressSequentially("12.50");
    amount.press("Tab");
    assertThat(amount).hasValue("12.50");

    amount.fill("");
    amount.pressSequentially("1234,567");
    amount.press("Tab");
    assertThat(amount).hasValue("1234,567");
  }

  @Test
  void aDayCountRemovesLettersButKeepsSeparators() {
    Page page = browser.newPage();
    openANewReturn(page, "2022");
    link(page, "Anlage N").click();

    field(page, "commuteDays").pressSequentially("1a2");
    assertThat(field(page, "commuteDays")).hasValue("12");

    field(page, "commuteKm").pressSequentially("12,5");
    assertThat(field(page, "commuteKm")).hasValue("12,5");
  }

  @Test
  void theCalendarOfADateFieldWritesTheChosenDay() {
    Page page = browser.newPage();
    openANewReturn(page, "2022");
    page.locator(".field:has([name=paidOn]) button.calendar").click();
    page.locator("#calendar-picker").fill("2022-02-03");
    assertThat(page.getByLabel("Datum")).hasValue("03.02.2022");

    link(page, "Hauptvordruck").click();
    page.locator(".field:has([name=birthDate]) button.calendar").click();
    page.locator("#calendar-picker").fill("1990-12-24");
    assertThat(field(page, "birthDate")).hasValue("24.12.1990");
  }

  private static void assertAllFieldsLookLike(BoundingBox reference, List<Double> columns, Page page) {
    assertEquals(
        page.locator("form input, form select").count(),
        page.locator(".field input, .field select").count(),
        "A control is outside the field component on " + page.url());
    for (Locator control : page.locator(".field input, .field select").all()) {
      BoundingBox box = control.boundingBox();
      assertTrue(
          columns.stream().anyMatch(column -> Math.abs(column - box.x) < LAYOUT_ROUNDING),
          "left edge " + box.x + " on " + page.url());
      assertEquals(reference.width, box.width, LAYOUT_ROUNDING, "width on " + page.url());
      assertEquals(reference.height, box.height, "height on " + page.url());
    }
  }

  private static void assertEveryFieldHasAHint(Page page) {
    int fields = page.locator(".field").count();
    assertTrue(fields > 0, "no field on " + page.url());
    List<String> hints = page.locator(".field .hint-text").allTextContents();
    assertEquals(fields, hints.size(), "fields without a hint on " + page.url());
    assertTrue(hints.stream().noneMatch(String::isBlank), "an empty hint on " + page.url());
    // Thymeleaf shows a missing message key as ??key??.
    assertTrue(hints.stream().noneMatch(hint -> hint.contains("??")), "a missing hint key on " + page.url());
  }

  /** The open tab is the only marked tab, it differs in look from the others, and no tab is underlined. */
  private static void assertOnlyOpenTab(Page page, String name) {
    Locator tabs = page.locator("nav .tabs a");
    Locator open = page.locator("nav .tabs a[aria-current=page]");
    assertThat(open).hasCount(1);
    assertThat(open).containsText(name);
    assertThat(tabs.first()).hasCSS("text-decoration-line", "none");
    Locator other = tabs.filter(new Locator.FilterOptions().setHasNotText(name)).first();
    String openBackground = (String) open.evaluate("tab => getComputedStyle(tab).backgroundColor");
    String otherBackground = (String) other.evaluate("tab => getComputedStyle(tab).backgroundColor");
    assertNotEquals(otherBackground, openBackground,"The open tab looks like the others on " + page.url());

    // A tab view: the page is a framed panel, and the open tab joins it.
    Locator panel = page.locator("main.panel");
    assertThat(panel).hasCount(1);
    assertThat(panel).hasCSS("border-left-style", "solid");
    assertThat(panel).hasCSS("border-bottom-style", "solid");
    String panelBackground = (String) panel.evaluate("p => getComputedStyle(p).backgroundColor");
    assertEquals(panelBackground, openBackground, "The open tab and the panel differ on " + page.url());
    double tabBottom = open.boundingBox().y + open.boundingBox().height;
    double panelTop = panel.boundingBox().y;
    assertTrue(tabBottom > panelTop && tabBottom <= panelTop + 2,
        "The open tab does not join the panel on " + page.url());

    // A changed style sheet has a new URL, so a browser cannot show the old look from its cache.
    String styleSheet = page.locator("link[rel=stylesheet]").getAttribute("href");
    assertTrue(styleSheet.matches("/app-[0-9a-f]{32}\\.css"), "no content hash in " + styleSheet);
  }

  private static void assertBodyIsEightyPercentOfTheWindow(Page page) {
    int visibleWidth = (int) page.evaluate("document.documentElement.clientWidth");
    assertEquals(visibleWidth * 0.8, page.locator("body").boundingBox().width, 1, "width on " + page.url());
  }

  private static void assertGroupsDoNotOverlap(Page page) {
    double previousBottom = 0;
    for (Locator group : page.locator("#answers .field-group").all()) {
      BoundingBox box = group.boundingBox();
      assertTrue(box.y >= previousBottom, "A section overlaps the section above it on " + page.url());
      previousBottom = box.y + box.height;
    }
  }

  private static List<Double> leftEdges(Page page) {
    return page.locator(".field input, .field select").all().stream()
        .map(control -> control.boundingBox().x)
        .distinct()
        .toList();
  }

  private static Object namesInGroup(Page page, String title) {
    return page.locator(".field-group")
        .filter(new Locator.FilterOptions().setHasText(title))
        .locator("input, select")
        .evaluateAll("controls => controls.map(control => control.name)");
  }

  private void openANewReturn(Page page, String year) {
    page.navigate("http://127.0.0.1:" + port + "/");
    String lastName = UUID.randomUUID().toString();
    String name = "Made-up " + lastName;
    page.locator("#new-person [name=firstName]").fill("Made-up");
    page.locator("#new-person [name=lastName]").fill(lastName);
    page.locator("#new-person [name=lastName]").press("Enter");
    Locator section = page.locator("section").filter(new Locator.FilterOptions().setHasText(name));
    section
        .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Steuererklärung " + year))
        .click();
    assertThat(page.getByLabel("Datum")).isVisible();
  }

  private static Locator link(Page page, String name) {
    return page.locator("nav").getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName(name));
  }

  private static Locator field(Page page, String name) {
    return page.locator("#answers [name=" + name + "]");
  }

  private static Locator clearButton(Page page, String name) {
    return page.locator("#answers .field:has([name=" + name + "])")
        .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Feld leeren"));
  }

  private static void fill(Page page, String name, String value) {
    field(page, name).fill(value);
  }

  /** Green: the green channel of the background is clearly above red and blue. */
  private static boolean isGreen(Locator tab) {
    String background = (String) tab.evaluate("tab => getComputedStyle(tab).backgroundColor");
    String[] rgb = background.replaceAll("[^0-9,]", "").split(",");
    int red = Integer.parseInt(rgb[0]);
    int green = Integer.parseInt(rgb[1]);
    int blue = Integer.parseInt(rgb[2]);
    return green - red > 15 && green - blue > 15;
  }

  private static void save(Page page) {
    page.locator("#answers").getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Speichern")).click();
  }
}
