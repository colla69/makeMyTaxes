package io.github.colla69.makemytaxes.app.web;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.FilePayload;
import com.microsoft.playwright.options.FormData;
import com.microsoft.playwright.options.RequestOptions;
import io.github.colla69.makemytaxes.app.document.MadeUpCertificate;
import io.github.colla69.makemytaxes.core.form.AnlageN;
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

/**
 * The values of a certificate pass a check by the user in a modal dialog before they are saved. The
 * certificate is generated with made-up values: Bruttoarbeitslohn 24.000,00, Lohnsteuer 3.100,00
 * and no Solidaritätszuschlag.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DocumentCheckBrowserTest {

  private static final String MARK = "Weicht vom Beleg ab";

  @TempDir static Path databaseFolder;

  private static Playwright playwright;
  private static Browser browser;

  @LocalServerPort private int port;
  @Autowired private JdbcClient jdbcClient;

  private long returnId;

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("makemytaxes.database.path", () -> databaseFolder.resolve("check.db").toString());
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
  void theReadValuesAreSavedOnlyAfterTheUserConfirmsThem() {
    Page page = browser.newPage();
    String formPage = anlageNWithCertificate(page);
    page.navigate(formPage);

    // A value typed by hand that is not on the certificate.
    page.locator("#answers [name=grossWage]").fill("25.000,00");
    page.locator("#answers [name=wageTax]").click();
    page.waitForCondition(() -> savedAnswer("grossWage").equals("25.000,00"));

    openCheck(page);
    Locator grossWage = row(page, "grossWage");
    assertThat(grossWage).containsText("Zeile 3");
    assertThat(grossWage.locator(".read")).hasText("24.000,00");
    assertThat(grossWage.locator("input")).hasValue("25.000,00");
    assertThat(grossWage.locator(".mark")).hasText(MARK);
    // A read value fills an empty input and matches.
    assertThat(row(page, "wageTax").locator("input")).hasValue("3.100,00");
    assertThat(row(page, "wageTax").locator(".mark")).isEmpty();
    // A value that the app did not find stays empty. The app suggests nothing of its own.
    Locator solidarity = row(page, "solidarity");
    assertThat(solidarity.locator(".read")).hasText("nicht gefunden");
    assertThat(solidarity.locator("input")).hasValue("");
    assertThat(solidarity.locator(".mark")).isEmpty();
    assertThat(row(page, "taxClass").locator("input")).hasValue("3");

    // Cancel saves nothing.
    dialogButton(page, "Abbrechen").click();
    assertThat(page.locator("#document-check")).hasCount(0);
    page.reload();
    assertThat(page.locator("#answers [name=wageTax]")).hasValue("");
    assertThat(page.locator("#answers [name=grossWage]")).hasValue("25.000,00");

    // The user changes the inputs in the dialog, and the marks follow.
    openCheck(page);
    row(page, "grossWage").locator("input").fill("24000");
    assertThat(row(page, "grossWage").locator(".mark")).isEmpty();
    row(page, "solidarity").locator("input").fill("10,00");
    assertThat(row(page, "solidarity").locator(".mark")).hasText(MARK);

    dialogButton(page, "Übernehmen").click();
    assertThat(page.locator("#document-check")).hasCount(0);
    assertThat(page.locator("#answers [name=wageTax]")).hasValue("3.100,00");
    page.waitForCondition(
        () ->
            savedAnswer("grossWage").equals("24.000,00")
                && savedAnswer("wageTax").equals("3.100,00")
                && savedAnswer("solidarity").equals("10,00")
                && !savedAnswer("employerName").isEmpty());
    page.reload();
    assertThat(page.locator("#answers [name=grossWage]")).hasValue("24.000,00");
    assertThat(page.locator("#answers [name=wageTax]")).hasValue("3.100,00");
    assertThat(page.locator("#answers [name=solidarity]")).hasValue("10,00");
    assertThat(page.locator("#answers [name=employerName]")).hasValue("Beispiel Werkstatt GmbH");
  }

  @Test
  void aSectionWithoutACertificateHasNoCheck() {
    Page page = browser.newPage();
    String formPage = anlageNWithCertificate(page);
    page.navigate(formPage);

    assertThat(page.locator("#answers .field-group").first().getByRole(AriaRole.BUTTON,
        new Locator.GetByRoleOptions().setName("Mit der Lohnsteuerbescheinigung prüfen")))
        .hasCount(1);
    assertThat(page.locator("#answers .field-group").last().getByRole(AriaRole.BUTTON,
        new Locator.GetByRoleOptions().setName("Mit der Lohnsteuerbescheinigung prüfen")))
        .hasCount(0);
  }

  private static void openCheck(Page page) {
    page.getByRole(
            AriaRole.BUTTON,
            new Page.GetByRoleOptions().setName("Mit der Lohnsteuerbescheinigung prüfen"))
        .first()
        .click();
    assertThat(page.locator("#document-check")).isVisible();
  }

  private static Locator row(Page page, String fieldId) {
    return page.locator("#document-check tr[data-field=" + fieldId + "]");
  }

  private static Locator dialogButton(Page page, String name) {
    return page.locator("#document-check")
        .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(name));
  }

  private String savedAnswer(String fieldId) {
    return jdbcClient
        .sql(
            "select value from form_answer"
                + " where tax_return_id = ? and form_id = ? and field_id = ?")
        .params(returnId, AnlageN.ID, fieldId)
        .query(String.class)
        .optional()
        .orElse("");
  }

  /** A return of an employee with the generated certificate. Answers the interview over HTTP. */
  private String anlageNWithCertificate(Page page) {
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
    String interview = base + "/returns/" + returnId + "/interview/questions/";
    http.post(interview + "employed", RequestOptions.create().setForm(FormData.create().set("answer", "YES")));
    http.post(interview + "wageCertificate", RequestOptions.create().setForm(FormData.create().set("answer", "YES")));
    http.post(
        interview + "wageCertificateDocument/document",
        RequestOptions.create()
            .setMultipart(
                FormData.create()
                    .set("file", new FilePayload("made-up.pdf", "application/pdf", MadeUpCertificate.pdf("2022")))));
    return base + "/returns/" + returnId + "/forms/" + AnlageN.ID;
  }
}
