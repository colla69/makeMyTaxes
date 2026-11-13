package io.github.colla69.makemytaxes.app.web;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.FilePayload;
import io.github.colla69.makemytaxes.app.document.MadeUpCertificate;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * A new version of a Lohnsteuerbescheinigung replaces the old one. The values that the app read
 * from the old one are read again from the new version, after a check by the user.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DocumentVersionBrowserTest {

  private static final String MADE_UP_TAX_ID = "86402715392"; // privacy-scan:allow made up

  private static final String OLD_FILE = "made-up.pdf";
  private static final String NEW_FILE = "made-up-corrected.pdf";

  /** The corrected certificate: another gross wage, another wage tax, and no line 17. */
  private static final Map<String, String> CORRECTED =
      Map.of("3.", "25.000,00", "4.", "3.300,00", "17.", "");

  @TempDir static Path databaseFolder;

  private static Playwright playwright;
  private static Browser browser;

  @LocalServerPort private int port;

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add(
        "makemytaxes.database.path", () -> databaseFolder.resolve("document-version.db").toString());
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
  void aNewVersionReplacesTheOldDocumentAndItsValuesAfterTheCheck() {
    Page page = browser.newPage();
    fillFromCertificate(page);
    uploadNewVersion(page);

    Locator check = page.locator("#version-check");
    Locator grossWage = check.locator("tr[data-field=grossWage]");
    assertThat(grossWage.locator(".current")).hasText("24.000,00");
    assertThat(grossWage.locator(".replacement")).hasText("25.000,00");
    button(check, "Übernehmen").click();

    assertThat(page.locator("#version-check")).hasCount(0);
    Locator rows = page.locator("#documents tbody tr");
    assertThat(rows).hasCount(1);
    assertThat(rows).containsText(NEW_FILE);

    Locator summary = jobSummary(page);
    assertThat(summary.locator("[data-question=grossWage] strong")).hasText("25.000,00");
    assertThat(summary.locator("[data-question=grossWage] .source"))
        .hasText("(Beleg: " + NEW_FILE + ")");
  }

  @Test
  void aValueTypedByHandStays() {
    Page page = browser.newPage();
    fillFromCertificate(page);
    page.navigate(page.url().replaceAll("/interview.*", "/interview/questions/wageTax"));
    page.locator("#question [name=answer]").fill("3.000,00");
    button(page.locator("#question"), "Weiter").click();
    uploadNewVersion(page);

    assertThat(page.locator("#version-check tr[data-field=wageTax]")).hasCount(0);
    button(page.locator("#version-check"), "Übernehmen").click();

    Locator summary = jobSummary(page);
    assertThat(summary.locator("[data-question=wageTax] strong")).hasText("3.000,00");
    assertThat(summary.locator("[data-question=wageTax] .source")).hasText("(eigene Angabe)");
  }

  @Test
  void aValueThatTheNewVersionLacksBecomesEmptyWithoutEvidence() {
    Page page = browser.newPage();
    fillFromCertificate(page);
    uploadNewVersion(page);

    Locator commute = page.locator("#version-check tr[data-field=employerCommute]");
    assertThat(commute.locator(".current")).hasText("240,00");
    assertThat(commute.locator(".replacement")).containsText("Beleg fehlt");
    button(page.locator("#version-check"), "Übernehmen").click();

    Locator summary = jobSummary(page);
    assertThat(summary.locator("[data-question=employerCommute] strong")).hasText("Keine Angabe");
    assertThat(summary.locator("[data-question=employerCommute] .source"))
        .hasText("(Beleg fehlt)");
  }

  @Test
  void cancellingTheCheckChangesNothing() {
    Page page = browser.newPage();
    fillFromCertificate(page);
    uploadNewVersion(page);

    button(page.locator("#version-check"), "Abbrechen").click();

    assertThat(page.locator("#version-check")).hasCount(0);
    Locator rows = page.locator("#documents tbody tr");
    assertThat(rows).hasCount(1);
    assertThat(rows).containsText(OLD_FILE);
    Locator summary = jobSummary(page);
    assertThat(summary.locator("[data-question=grossWage] strong")).hasText("24.000,00");
    assertThat(summary.locator("[data-question=grossWage] .source"))
        .hasText("(Beleg: " + OLD_FILE + ")");
    assertThat(summary.locator("[data-question=employerCommute] strong")).hasText("240,00");
  }

  private void uploadNewVersion(Page page) {
    page.navigate(page.url().replaceAll("/interview.*", "/documents"));
    Locator row = page.locator("#documents tbody tr").filter(rowWith(OLD_FILE));
    row.locator("input[type=file]")
        .setInputFiles(
            new FilePayload(
                NEW_FILE, "application/pdf", MadeUpCertificate.pdf("2022", CORRECTED)));
    button(row, "Neue Version hochladen").click();
    assertThat(page.locator("#version-check")).isVisible();
  }

  private static Locator.FilterOptions rowWith(String fileName) {
    return new Locator.FilterOptions().setHasText(fileName);
  }

  private static Locator jobSummary(Page page) {
    page.navigate(page.url().replaceAll("/documents.*", "/interview/topics/JOB"));
    return page.locator("#summary");
  }

  /** Creates a return and takes the values of the made-up certificate in the interview. */
  private void fillFromCertificate(Page page) {
    page.navigate("http://127.0.0.1:" + port + "/");
    String lastName = UUID.randomUUID().toString();
    Locator form = page.locator("#new-person");
    form.locator("[name=firstName]").fill("Made-up");
    form.locator("[name=lastName]").fill(lastName);
    form.locator("[name=taxId]").fill(MADE_UP_TAX_ID);
    form.locator("[name=birthDate]").fill("01.01.1990");
    form.locator("[name=street]").fill("Musterweg");
    form.locator("[name=houseNumber]").fill("1");
    form.locator("[name=postalCode]").fill("00000");
    form.locator("[name=city]").fill("Made-up city");
    button(form, "Person anlegen").click();
    page.locator("section")
        .filter(new Locator.FilterOptions().setHasText("Made-up " + lastName))
        .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Steuererklärung 2022"))
        .click();
    page.locator("nav")
        .getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName("Fragen"))
        .click();
    Locator upload = page.locator("#upload");
    upload
        .locator("input[type=file]")
        .setInputFiles(
            new FilePayload(OLD_FILE, "application/pdf", MadeUpCertificate.pdf("2022")));
    button(upload, "Hochladen").click();
    button(upload, "Werte übernehmen").click();
    button(page.locator("#question"), "Ja, die Angaben stimmen").click();
  }

  private static Locator button(Locator scope, String name) {
    return scope.getByRole(
        AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(name).setExact(true));
  }
}
