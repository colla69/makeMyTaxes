package io.github.colla69.makemytaxes.app.web;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Download;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.FilePayload;
import com.microsoft.playwright.options.FormData;
import com.microsoft.playwright.options.RequestOptions;
import io.github.colla69.makemytaxes.core.form.AnlageN;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
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

/** Each line of the review shows its source, and the export holds the review and its documents. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ReviewSourceBrowserTest {

  private static final byte[] MADE_UP_PDF =
      "%PDF-1.4\n% made-up test document\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);

  @TempDir static Path databaseFolder;

  private static Playwright playwright;
  private static Browser browser;

  @LocalServerPort private int port;
  @Autowired private JdbcClient jdbcClient;

  private long returnId;
  private long documentId;

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("makemytaxes.database.path", () -> databaseFolder.resolve("review.db").toString());
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
  void eachLineShowsItsDocumentOrThatTheUserStatedIt() {
    Page page = browser.newPage();
    page.navigate(returnWithCertificateAndOwnStatement(page) + "/review");

    Locator grossWage = row(page, "Bruttoarbeitslohn");
    assertThat(grossWage).containsText("30.000,00");
    Locator link = grossWage.getByText("Beleg: made-up-certificate.pdf");
    assertThat(link)
        .hasAttribute("href", "/returns/" + returnId + "/documents/" + documentId);

    assertThat(row(page, "einfache Entfernung").locator(".source")).hasText("eigene Angabe");
  }

  @Test
  void theExportHoldsTheReviewAndEachLinkedDocument() throws IOException {
    Page page = browser.newPage();
    page.navigate(returnWithCertificateAndOwnStatement(page) + "/review");

    Download download = page.waitForDownload(() -> page.locator("#export").click());
    Map<String, byte[]> entries = unzip(download.path());

    String documentEntry = "belege/" + documentId + "-made-up-certificate.pdf";
    assertEquals(2, entries.size(), "Entries: " + entries.keySet());
    assertArrayEquals(MADE_UP_PDF, entries.get(documentEntry));
    String review = new String(entries.get("uebersicht.html"), StandardCharsets.UTF_8);
    assertTrue(review.contains("30.000,00"), "The export has no value of the review");
    assertTrue(review.contains("href=\"" + documentEntry + "\""), "No link to the document");
    assertTrue(review.contains("eigene Angabe"), "The export does not name an own statement");
    assertTrue(review.contains("Exportiert am"), "The export has no date");
  }

  @Test
  void theReceiptOfAnExpenseIsLinkedOnTheReviewAndInTheExport() throws IOException {
    Page page = browser.newPage();
    String returnUrl = returnWithCertificateAndOwnStatement(page);
    page.request()
        .post(
            returnUrl + "/expenses",
            RequestOptions.create()
                .setMultipart(
                    FormData.create()
                        .set("paidOn", "03.02.2022")
                        .set("amount", "150,00")
                        .set("topic", "WORK_EQUIPMENT")
                        .set("description", "Made-up laptop")
                        .set("receipt", "")
                        .set("statedBasis", "")
                        .set("file", new FilePayload("made-up-receipt.pdf", "application/pdf", MADE_UP_PDF))));
    long receiptId =
        jdbcClient
            .sql("select document_id from expense where tax_return_id = ?")
            .param(returnId)
            .query(Long.class)
            .single();
    page.navigate(returnUrl + "/review");

    assertThat(row(page, "Made-up laptop").getByText("Beleg: made-up-receipt.pdf"))
        .hasAttribute("href", "/returns/" + returnId + "/documents/" + receiptId);

    Download download = page.waitForDownload(() -> page.locator("#export").click());
    Map<String, byte[]> entries = unzip(download.path());
    String receiptEntry = "belege/" + receiptId + "-made-up-receipt.pdf";
    assertArrayEquals(MADE_UP_PDF, entries.get(receiptEntry));
    assertTrue(
        new String(entries.get("uebersicht.html"), StandardCharsets.UTF_8)
            .contains("href=\"" + receiptEntry + "\""),
        "No link to the receipt");
  }

  private static Locator row(Page page, String label) {
    return page.locator(".review-form tbody tr")
        .filter(new Locator.FilterOptions().setHasText(label));
  }

  private static Map<String, byte[]> unzip(Path zipFile) throws IOException {
    Map<String, byte[]> entries = new HashMap<>();
    try (InputStream in = Files.newInputStream(zipFile);
        ZipInputStream zip = new ZipInputStream(in, StandardCharsets.UTF_8)) {
      for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
        entries.put(entry.getName(), zip.readAllBytes());
      }
    }
    return entries;
  }

  /**
   * A return with a gross wage from an uploaded certificate and a distance that the user typed.
   * Answers over HTTP and returns the address of the return.
   */
  private String returnWithCertificateAndOwnStatement(Page page) {
    String base = "http://127.0.0.1:" + port;
    APIRequestContext http = page.request();
    String lastName = UUID.randomUUID().toString();
    http.post(base + "/persons",
        RequestOptions.create().setForm(FormData.create().set("firstName", "Made-up").set("lastName", lastName)));
    long personId =
        jdbcClient
            .sql("select id from person where name = ?")
            .param("Made-up " + lastName)
            .query(Long.class)
            .single();
    http.post(base + "/persons/" + personId + "/returns",
        RequestOptions.create().setForm(FormData.create().set("taxYear", "2022")));
    returnId =
        jdbcClient
            .sql("select id from tax_return where person_id = ?")
            .param(personId)
            .query(Long.class)
            .single();
    String returnUrl = base + "/returns/" + returnId;
    String interview = returnUrl + "/interview/questions/";
    answer(http, interview + "employed", "YES");
    answer(http, interview + "wageCertificate", "YES");
    http.post(
        interview + "wageCertificateDocument/document",
        RequestOptions.create()
            .setMultipart(
                FormData.create()
                    .set("file", new FilePayload("made-up-certificate.pdf", "application/pdf", MADE_UP_PDF))));
    documentId =
        jdbcClient
            .sql("select id from document where tax_return_id = ?")
            .param(returnId)
            .query(Long.class)
            .single();
    answer(http, interview + "grossWage", "30.000,00");
    http.post(
        returnUrl + "/forms/" + AnlageN.ID + "/field",
        RequestOptions.create().setForm(FormData.create().set("field", "commuteKm").set("value", "12")));
    return returnUrl;
  }

  private static void answer(APIRequestContext http, String question, String value) {
    http.post(question, RequestOptions.create().setForm(FormData.create().set("answer", value)));
  }
}
