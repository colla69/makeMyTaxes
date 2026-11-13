package io.github.colla69.makemytaxes.app.web;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.FilePayload;
import com.microsoft.playwright.options.SelectOption;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DocumentBrowserTest {

  @TempDir static Path databaseFolder;

  private static Playwright playwright;
  private static Browser browser;

  @LocalServerPort private int port;

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add(
        "makemytaxes.database.path", () -> databaseFolder.resolve("documents.db").toString());
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
  void uploadsAnImageListsItAndOpensItInTheBrowser() throws IOException {
    Page page = browser.newPage();
    openTheDocumentsOfANewReturn(page);

    page.getByLabel("Art").selectOption("INVOICE_OR_RECEIPT");
    page.getByLabel("Datei")
        .setInputFiles(new FilePayload("made-up-receipt.png", "image/png", madeUpPng()));
    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Hochladen")).click();

    Locator row = page.locator("#documents tbody tr");
    assertThat(row).hasCount(1);
    assertThat(row).containsText("Rechnung/Quittung");

    Page viewer =
        page.waitForPopup(
            () ->
                row.getByRole(
                        AriaRole.LINK,
                        new Locator.GetByRoleOptions().setName("made-up-receipt.png"))
                    .click());
    // Chromium shows an image document as one img element with the size of the image.
    Locator image = viewer.locator("img");
    assertThat(image).isVisible();
    viewer.waitForCondition(
        () -> ((Number) image.evaluate("img => img.naturalWidth")).intValue() == 3);
  }

  @Test
  void previewsAPdfInThePageAndClosesThePreview() {
    Page page = browser.newPage();
    openTheDocumentsOfANewReturn(page);
    page.getByLabel("Art").selectOption("INVOICE_OR_RECEIPT");
    page.getByLabel("Datei")
        .setInputFiles(
            new FilePayload(
                "made-up-invoice.pdf", "application/pdf", "%PDF-1.4 made-up".getBytes()));
    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Hochladen")).click();
    Locator row = page.locator("#documents tbody tr");
    assertThat(row).hasCount(1);
    String href =
        row.getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName("made-up-invoice.pdf"))
            .getAttribute("href");

    row.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Vorschau")).click();

    Locator preview = page.locator("#document-preview");
    assertThat(preview).containsText("made-up-invoice.pdf");
    assertThat(preview.locator("iframe")).hasAttribute("src", href);
    assertThat(page.locator("#documents tbody tr")).hasCount(1);
    preview
        .getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName("Vorschau schließen"))
        .click();
    assertThat(page.locator("#document-preview")).hasCount(0);
  }

  @Test
  void previewsAnImageAsAnImageAndReplacesTheShownPreview() throws IOException {
    Page page = browser.newPage();
    openTheDocumentsOfANewReturn(page);
    uploadReceipt(page, "made-up-first.png");
    uploadReceipt(page, "made-up-second.png");
    Locator first =
        page.locator("#documents tbody tr")
            .filter(new Locator.FilterOptions().setHasText("made-up-first.png"));
    Locator second =
        page.locator("#documents tbody tr")
            .filter(new Locator.FilterOptions().setHasText("made-up-second.png"));

    first.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Vorschau")).click();
    assertThat(page.locator("#document-preview")).containsText("made-up-first.png");
    second.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Vorschau")).click();

    assertThat(page.locator("#document-preview")).hasCount(1);
    assertThat(page.locator("#document-preview")).containsText("made-up-second.png");
    Locator image = page.locator("#document-preview img");
    page.waitForCondition(
        () -> ((Number) image.evaluate("img => img.naturalWidth")).intValue() == 3);
  }

  @Test
  void showsAnErrorForAFileOfAnotherType() {
    Page page = browser.newPage();
    openTheDocumentsOfANewReturn(page);

    page.getByLabel("Datei")
        .setInputFiles(new FilePayload("made-up.txt", "text/plain", "made-up".getBytes()));
    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Hochladen")).click();

    assertThat(page.locator(".error")).containsText("Nur PDF, JPEG und PNG");
    assertThat(page.locator("#documents tbody tr")).hasCount(0);
  }

  /** A file over the limit of the server, so that the real Tomcat stops it, and not the app. */
  @Test
  void showsAnErrorForAFileOverTheLimitOfTheServer() {
    Page page = browser.newPage();
    openTheDocumentsOfANewReturn(page);
    byte[] large = new byte[30 * 1024 * 1024];
    large[0] = '%';

    page.getByLabel("Datei").setInputFiles(new FilePayload("large.pdf", "application/pdf", large));
    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Hochladen")).click();

    assertThat(page.locator(".error")).containsText("größer als 20 MB");
    assertThat(page.locator("#documents tbody tr")).hasCount(0);
  }

  @Test
  void deletesADocumentAfterASecondQuestionInThePage() throws IOException {
    Page page = browser.newPage();
    openTheDocumentsOfANewReturn(page);
    uploadReceipt(page, "made-up-receipt.png");
    Locator row = page.locator("#documents tbody tr");
    assertThat(row).hasCount(1);

    row.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Löschen")).click();
    assertThat(row).containsText("Wirklich löschen?");
    assertThat(row).hasCount(1);
    row.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Ja, löschen")).click();

    assertThat(page.locator("#documents tbody tr")).hasCount(0);
    assertThat(page.locator("#documents")).not().containsText("made-up-receipt.png");
  }

  @Test
  void aPopupNamesTheExpenseOfADocumentAndCancelKeepsTheDocument() throws IOException {
    Page page = browser.newPage();
    String documentsUrl = openADocumentThatAnExpenseUses(page);

    Locator dialog = clickDeleteOfTheOnlyDocument(page);
    assertThat(dialog).isVisible();
    assertThat(dialog.getByRole(AriaRole.LINK)).hasText("Ausgabe vom 03.02.2022: Made-up cable");
    String expensesPath =
        documentsUrl.substring(documentsUrl.indexOf("/returns/"), documentsUrl.lastIndexOf('/'));
    assertThat(dialog.getByRole(AriaRole.LINK)).hasAttribute("href", expensesPath);
    dialog.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Abbrechen")).click();

    assertThat(dialog).not().isVisible();
    page.navigate(documentsUrl);
    assertThat(page.locator("#documents tbody tr")).hasCount(1);
    assertThat(page.locator("#documents")).containsText("made-up-invoice.png");
  }

  @Test
  void deleteAnywayKeepsTheExpenseAndShowsThatItsEvidenceIsMissing() throws IOException {
    Page page = browser.newPage();
    String documentsUrl = openADocumentThatAnExpenseUses(page);

    Locator dialog = clickDeleteOfTheOnlyDocument(page);
    dialog
        .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Trotzdem löschen"))
        .click();

    assertThat(page.locator("#documents tbody tr")).hasCount(0);
    page.navigate(documentsUrl.substring(0, documentsUrl.lastIndexOf('/')));
    Locator expense = page.locator("#expenses tbody tr");
    assertThat(expense).hasCount(1);
    assertThat(expense).containsText("10,00");
    assertThat(expense).containsText("Made-up cable");
    assertThat(expense).containsText("Beleg fehlt");
    assertThat(expense).not().containsText("made-up-invoice.png");
  }

  /** Returns the address of the documents page. */
  private String openADocumentThatAnExpenseUses(Page page) throws IOException {
    openTheDocumentsOfANewReturn(page);
    String documentsUrl = page.url();
    uploadReceipt(page, "made-up-invoice.png");
    page.navigate(documentsUrl.substring(0, documentsUrl.lastIndexOf('/')));
    page.getByLabel("Datum").fill("03.02.2022");
    page.getByLabel("Dokument").selectOption(new SelectOption().setLabel("made-up-invoice.png"));
    page.getByLabel("Betrag (EUR)").fill("10,00");
    page.getByLabel("Beschreibung").fill("Made-up cable");
    page.getByLabel("Beschreibung").press("Enter");
    assertThat(page.locator("#expenses tbody tr")).hasCount(1);
    assertThat(page.locator("#expenses tbody tr")).not().containsText("Beleg fehlt");
    page.navigate(documentsUrl);
    return documentsUrl;
  }

  private static Locator clickDeleteOfTheOnlyDocument(Page page) {
    page.locator("#documents tbody tr")
        .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Löschen"))
        .click();
    return page.getByRole(AriaRole.DIALOG);
  }

  private static void uploadReceipt(Page page, String fileName) throws IOException {
    page.getByLabel("Art").selectOption("INVOICE_OR_RECEIPT");
    page.getByLabel("Datei").setInputFiles(new FilePayload(fileName, "image/png", madeUpPng()));
    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Hochladen")).click();
    assertThat(page.locator("#documents")).containsText(fileName);
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

  /** A generated 3 x 2 image, so that the test can check the width that the browser shows. */
  private static byte[] madeUpPng() throws IOException {
    var image = new BufferedImage(3, 2, BufferedImage.TYPE_INT_RGB);
    var bytes = new ByteArrayOutputStream();
    ImageIO.write(image, "png", bytes);
    return bytes.toByteArray();
  }
}
