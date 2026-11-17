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
import java.util.Base64;
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

  /** A PNG of one pixel, with no text to recognize. */
  private static final byte[] MADE_UP_PNG =
      Base64.getDecoder()
          .decode(
              "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

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
  void previewsAPayslipInAModalAndClosesIt() {
    Page page = browser.newPage();
    openTheDocumentsOfANewReturn(page);
    uploadPayslip(page, "April", "4.000,00", "500,00");
    Locator april = month(page, "April");
    String href = april.getByRole(AriaRole.LINK).getAttribute("href");

    april.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Vorschau")).click();

    Locator dialog = page.getByRole(AriaRole.DIALOG);
    assertThat(dialog).hasAccessibleName("made-up-april.pdf");
    assertThat(dialog.locator("iframe")).hasAttribute("src", href);
    dialog
        .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Vorschau schließen"))
        .click();
    assertThat(dialog).isHidden();
    assertThat(month(page, "April").getByRole(AriaRole.LINK)).hasText("made-up-april.pdf");
  }

  @Test
  void recognizesThePayslipWithoutAKindOrAMonth() {
    Page page = browser.newPage();
    openTheDocumentsOfANewReturn(page);

    uploadPayslip(page, "August", "4.000,00", "500,00");

    assertThat(page.locator("#recognized"))
        .containsText("„made-up-august.pdf“ gespeichert als: Lohnabrechnung August 2022");
    assertThat(month(page, "August").getByRole(AriaRole.LINK)).hasText("made-up-august.pdf");
  }

  @Test
  void asksForTheKindAndTheMonthOfAFileThatItDoesNotRecognize() {
    Page page = browser.newPage();
    openTheDocumentsOfANewReturn(page);

    upload(page, new FilePayload("made-up-scan.png", "image/png", MADE_UP_PNG));

    Locator form = page.locator("#kind-form");
    assertThat(form).containsText("Die App hat nicht erkannt, was diese Datei ist.");
    assertThat(form.locator("img")).hasAttribute("alt", "made-up-scan.png");
    assertThat(page.locator("#documents tbody tr")).hasCount(0);
    form.getByLabel("Art").selectOption("PAYSLIP");
    form.getByLabel("Monat").selectOption(new SelectOption().setLabel("Juni"));
    form.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Speichern")).click();

    assertThat(month(page, "Juni").getByRole(AriaRole.LINK)).hasText("made-up-scan.png");
  }

  @Test
  void doesNotStoreAPayslipOfAnotherYear() {
    Page page = browser.newPage();
    openTheDocumentsOfANewReturn(page);

    upload(
        page,
        new FilePayload(
            "made-up-2021.pdf",
            "application/pdf",
            MadeUpPayslip.pdf("Mai 2021", "4.000,00", "500,00")));

    assertThat(page.locator(".error"))
        .containsText("„made-up-2021.pdf“ gehört zum Jahr 2021, nicht zu 2022.");
    assertThat(page.locator("#documents tbody tr")).hasCount(0);
  }

  @Test
  void correctsTheMonthOfARecognizedPayslip() {
    Page page = browser.newPage();
    openTheDocumentsOfANewReturn(page);
    uploadPayslip(page, "September", "4.000,00", "500,00");

    page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Art oder Monat korrigieren"))
        .click();
    Locator form = page.locator("#kind-form");
    form.getByLabel("Monat").selectOption(new SelectOption().setLabel("Oktober"));
    form.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Speichern")).click();

    assertThat(page.locator("#recognized")).containsText("Lohnabrechnung Oktober 2022");
    assertThat(month(page, "Oktober").getByRole(AriaRole.LINK)).hasText("made-up-september.pdf");
    assertThat(month(page, "September")).containsText("fehlt");
  }

  @Test
  void doesNotMoveAPayslipIntoAMonthThatHasOne() {
    Page page = browser.newPage();
    openTheDocumentsOfANewReturn(page);
    uploadPayslip(page, "Juli", "4.000,00", "500,00");
    uploadPayslip(page, "August", "4.100,00", "510,00");

    page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Art oder Monat korrigieren"))
        .click();
    Locator form = page.locator("#kind-form");
    form.getByLabel("Monat").selectOption(new SelectOption().setLabel("Juli"));
    form.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Speichern")).click();

    assertThat(form.locator(".error")).containsText("Juli hat schon eine Lohnabrechnung");
    page.navigate(page.url().replaceAll("/documents.*", "/documents"));
    assertThat(month(page, "Juli").getByRole(AriaRole.LINK)).hasText("made-up-juli.pdf");
    assertThat(month(page, "August").getByRole(AriaRole.LINK)).hasText("made-up-august.pdf");
  }

  @Test
  void asksBeforeANewPayslipReplacesTheOldOneOfTheMonth() {
    Page page = browser.newPage();
    openTheDocumentsOfANewReturn(page);
    uploadPayslip(page, "Mai", "4.000,00", "500,00");

    upload(
        page,
        new FilePayload(
            "made-up-mai-neu.pdf",
            "application/pdf",
            MadeUpPayslip.pdf("Mai 2022", "4.200,00", "520,00")));

    Locator check = page.locator("#version-check");
    assertThat(check).containsText("Mai hat schon eine Lohnabrechnung: „made-up-mai.pdf“");
    assertThat(month(page, "Mai")).containsText("4.000,00");
    check.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Übernehmen")).click();

    Locator may = month(page, "Mai");
    assertThat(may.getByRole(AriaRole.LINK)).hasText("made-up-mai-neu.pdf");
    assertThat(may).containsText("4.200,00");
    assertThat(may).containsText("520,00");
    assertThat(page.locator("#documents tbody tr")).hasCount(1);
  }

  @Test
  void keepsTheOldPayslipIfTheUserCancelsTheReplacement() {
    Page page = browser.newPage();
    openTheDocumentsOfANewReturn(page);
    uploadPayslip(page, "November", "4.000,00", "500,00");

    upload(
        page,
        new FilePayload(
            "made-up-november-neu.pdf",
            "application/pdf",
            MadeUpPayslip.pdf("November 2022", "4.200,00", "520,00")));
    page.locator("#version-check")
        .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Abbrechen"))
        .click();

    assertThat(month(page, "November").getByRole(AriaRole.LINK)).hasText("made-up-november.pdf");
    assertThat(page.locator("#documents tbody tr")).hasCount(1);
  }

  @Test
  void showsTheDifferenceToTheCertificate() {
    Page page = browser.newPage();
    openTheDocumentsOfANewReturn(page);
    upload(
        page,
        new FilePayload(
            "made-up-certificate.pdf", "application/pdf", MadeUpCertificate.pdf("2022")));
    assertThat(page.locator("#recognized")).containsText("Lohnsteuerbescheinigung 2022");

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

  @Test
  void addsTheCorrectionsOfEarlierMonthsAndChecksTheYearColumn() {
    Page page = browser.newPage();
    openTheDocumentsOfANewReturn(page);

    uploadPayslip(
        page,
        "Januar",
        MadeUpPayslip.pdfOf(
            List.of(
                "Entgeltabrechnung für den Zeitraum vom 01.01. bis 31.01.2022",
                "Steuerbrutto, lfd. 3.000,00 3.000,00",
                "Lohnsteuer, lfd. 400,00 400,00")));
    uploadPayslip(
        page,
        "Februar",
        MadeUpPayslip.pdfOf(
            List.of(
                "Entgeltabrechnung für den Zeitraum vom 01.02. bis 28.02.2022",
                "Steuerbrutto, lfd. 3.000,00 6.000,00",
                "Lohnsteuer, lfd. 400,00 800,00")));
    // March holds a correction of January and a negative correction of February.
    uploadPayslip(
        page,
        "März",
        MadeUpPayslip.pdfOf(
            List.of(
                "Entgeltabrechnung für den Zeitraum vom 01.03. bis 31.03.2022",
                "Steuerbrutto, lfd. 3.000,00 9.150,00",
                "Lohnsteuer, lfd. 400,00 1.190,00",
                "Entgeltabrechnung für den Zeitraum vom 01.01. bis 31.01.2022",
                "Steuerbrutto, lfd. 150,00",
                "Lohnsteuer, lfd. 40,00",
                "Entgeltabrechnung für den Zeitraum vom 01.02. bis 28.02.2022",
                "Lohnsteuer, lfd. 50,00 -")));

    Locator january = month(page, "Januar");
    assertThat(january).containsText("3.150,00");
    assertThat(january).containsText("440,00");
    assertThat(january).containsText("Abrechnung des Monats: Steuer-Brutto 3.000,00, Lohnsteuer 400,00");
    assertThat(january)
        .containsText("Korrektur in made-up-märz.pdf: Steuer-Brutto 150,00, Lohnsteuer 40,00");
    assertThat(month(page, "Februar")).containsText("350,00");
    assertThat(month(page, "Februar")).containsText("Lohnsteuer -50,00");
    assertThat(month(page, "März")).not().containsText("Korrektur");

    Locator yearCheck = page.locator("#payslip-year-check");
    assertThat(yearCheck).containsText("Lohnabrechnung für März (made-up-märz.pdf)");
    Locator gross =
        yearCheck.locator("tr").filter(new Locator.FilterOptions().setHasText("Steuer-Brutto"));
    assertThat(gross).containsText("9.150,00");
    assertThat(gross).containsText("stimmt überein");
    Locator wageTax =
        yearCheck.locator("tr").filter(new Locator.FilterOptions().setHasText("Lohnsteuer"));
    assertThat(wageTax).containsText("1.190,00");
    assertThat(wageTax).containsText("stimmt überein");
  }

  @Test
  void saysSoIfTheSumOfTheMonthsDoesNotMatchTheYearColumn() {
    Page page = browser.newPage();
    openTheDocumentsOfANewReturn(page);

    uploadPayslip(
        page,
        "Februar",
        MadeUpPayslip.pdfOf(
            List.of(
                "Entgeltabrechnung für den Zeitraum vom 01.02. bis 28.02.2022",
                "Steuerbrutto, lfd. 3.000,00 6.000,00",
                "Lohnsteuer, lfd. 400,00 800,00")));

    Locator wageTax =
        page.locator("#payslip-year-check tr")
            .filter(new Locator.FilterOptions().setHasText("Lohnsteuer"));
    assertThat(wageTax).containsText("Differenz 400,00 €: die Jahressumme ist höher");
  }

  private static void uploadPayslip(Page page, String month, String gross, String wageTax) {
    uploadPayslip(page, month, MadeUpPayslip.pdf(month + " 2022", gross, wageTax));
  }

  /** The app recognizes the month from the text, so the upload names no month. */
  private static void uploadPayslip(Page page, String month, byte[] pdf) {
    upload(
        page,
        new FilePayload(
            "made-up-" + month.toLowerCase(Locale.ROOT) + ".pdf", "application/pdf", pdf));
    assertThat(page.locator("#recognized")).containsText("Lohnabrechnung " + month);
  }

  private static void upload(Page page, FilePayload file) {
    page.getByLabel("Datei").setInputFiles(file);
    page.waitForNavigation(
        () ->
            page.getByRole(
                    AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Hochladen").setExact(true))
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
