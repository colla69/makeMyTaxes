package io.github.colla69.makemytaxes.app.web;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.FilePayload;
import com.microsoft.playwright.options.SelectOption;
import io.github.colla69.makemytaxes.app.document.MadeUpCertificate;
import io.github.colla69.makemytaxes.app.document.MadeUpPayslip;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
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
 * Monthly payslips on the page "Belege". The made-up certificate for 2022 has Bruttoarbeitslohn
 * 24.000,00 and Lohnsteuer 3.100,00.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PayslipBrowserTest {

  private static final List<String> FIRST_HALF =
      List.of("Januar", "Februar", "März", "April", "Mai", "Juni");

  @TempDir static Path databaseFolder;

  private static Playwright playwright;
  private static Browser browser;

  @LocalServerPort private int port;

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add(
        "makemytaxes.database.path", () -> databaseFolder.resolve("payslips.db").toString());
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
  void showsWhichMonthsHaveAPayslip() {
    Page page = browser.newPage();
    openTheDocumentsOfANewReturn(page);

    uploadPayslip(page, "Januar", "4.000,00", "500,00");
    uploadPayslip(page, "März", "4.100,00", "510,00");

    Locator january = month(page, "Januar");
    assertThat(january.getByRole(AriaRole.LINK)).hasText("made-up-januar.pdf");
    assertThat(january).containsText("4.000,00");
    assertThat(january).containsText("500,00");
    assertThat(month(page, "März").getByRole(AriaRole.LINK)).hasText("made-up-märz.pdf");
    assertThat(month(page, "Februar")).containsText("fehlt");
    assertThat(page.locator("#payslips tr.missing")).hasCount(10);
    assertThat(page.locator("#documents tbody tr")).hasCount(2);
  }

  @Test
  void previewsAPayslipInThePageAndClosesThePreview() {
    Page page = browser.newPage();
    openTheDocumentsOfANewReturn(page);
    uploadPayslip(page, "April", "4.000,00", "500,00");
    Locator april = month(page, "April");
    String href = april.getByRole(AriaRole.LINK).getAttribute("href");

    april.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Vorschau")).click();

    Locator preview = page.locator("#document-preview");
    assertThat(preview).containsText("made-up-april.pdf");
    assertThat(preview.locator("iframe")).hasAttribute("src", href);
    preview
        .getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName("Vorschau schließen"))
        .click();
    assertThat(page.locator("#document-preview")).hasCount(0);
    assertThat(month(page, "April").getByRole(AriaRole.LINK)).hasText("made-up-april.pdf");
  }

  @Test
  void aNewPayslipReplacesTheOldOneOfTheMonth() {
    Page page = browser.newPage();
    openTheDocumentsOfANewReturn(page);

    uploadPayslip(page, "Mai", "4.000,00", "500,00");
    uploadPayslip(page, "Mai", "4.200,00", "520,00");

    Locator may = month(page, "Mai");
    assertThat(may.getByRole(AriaRole.LINK)).hasCount(1);
    assertThat(may).containsText("4.200,00");
    assertThat(may).containsText("520,00");
    // The old payslip stays as a document, because an expense can link to it.
    assertThat(page.locator("#documents tbody tr")).hasCount(2);
  }

  @Test
  void showsTheDifferenceToTheCertificate() {
    Page page = browser.newPage();
    openTheDocumentsOfANewReturn(page);
    page.getByLabel("Art").selectOption("WAGE_TAX_CERTIFICATE");
    page.getByLabel("Datei")
        .setInputFiles(
            new FilePayload(
                "made-up-certificate.pdf", "application/pdf", MadeUpCertificate.pdf("2022")));
    page.waitForNavigation(
        () ->
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Hochladen").setExact(true))
                .click());

    // Six months of 4.000,00 match line 3. Six months of 500,00 are 100,00 below line 4.
    for (String month : FIRST_HALF) {
      uploadPayslip(page, month, "4.000,00", "500,00");
    }

    Locator check = page.locator("#payslip-check");
    Locator gross = check.locator("tr").filter(new Locator.FilterOptions().setHasText("Zeile 3"));
    assertThat(gross).containsText("24.000,00");
    assertThat(gross).containsText("stimmt überein");
    Locator wageTax = check.locator("tr").filter(new Locator.FilterOptions().setHasText("Zeile 4"));
    assertThat(wageTax).containsText("3.000,00");
    assertThat(wageTax).containsText("3.100,00");
    assertThat(wageTax).containsText("Differenz 100,00 €: die Bescheinigung ist höher");
  }

  @Test
  void saysThatTheCertificateIsMissing() {
    Page page = browser.newPage();
    openTheDocumentsOfANewReturn(page);

    uploadPayslip(page, "Juli", "4.000,00", "500,00");

    assertThat(page.locator("#payslip-check")).containsText("keine Lohnsteuerbescheinigung");
    assertThat(page.locator("#payslip-check")).containsText("kein Vergleich möglich");
  }

  private static void uploadPayslip(Page page, String month, String gross, String wageTax) {
    page.getByLabel("Monat").selectOption(new SelectOption().setLabel(month));
    page.getByLabel("Abrechnung")
        .setInputFiles(
            new FilePayload(
                "made-up-" + month.toLowerCase(Locale.ROOT) + ".pdf",
                "application/pdf",
                MadeUpPayslip.pdf(month + " 2022", gross, wageTax)));
    page.waitForNavigation(
        () ->
            page.getByRole(
                    AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Abrechnung speichern"))
                .click());
  }

  private static Locator month(Page page, String month) {
    return page.locator("#payslips tr[data-month='" + month + "']");
  }

  private void openTheDocumentsOfANewReturn(Page page) {
    page.navigate("http://127.0.0.1:" + port + "/");
    String lastName = UUID.randomUUID().toString();
    String name = "Made-up " + lastName;
    page.locator("#new-person [name=firstName]").fill("Made-up");
    page.locator("#new-person [name=lastName]").fill(lastName);
    page.locator("#new-person [name=lastName]").press("Enter");
    Locator section = page.locator("section").filter(new Locator.FilterOptions().setHasText(name));
    section
        .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Steuererklärung 2022"))
        .click();
    page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Belege")).click();
    assertThat(page.getByLabel("Datei")).isVisible();
  }
}
