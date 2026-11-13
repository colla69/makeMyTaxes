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
 * The interview starts with the upload of documents. The values of an uploaded certificate answer
 * their questions, which the interview then skips.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DocumentFirstBrowserTest {

  private static final String MADE_UP_TAX_ID = "86402715392"; // privacy-scan:allow made up

  @TempDir static Path databaseFolder;

  private static Playwright playwright;
  private static Browser browser;

  @LocalServerPort private int port;

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add(
        "makemytaxes.database.path", () -> databaseFolder.resolve("document-first.db").toString());
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
  void anUploadedCertificatePrefillsItsQuestionsWithItsSourceAndTheInterviewSkipsThem() {
    Page page = browser.newPage();
    openInterview(page);
    Locator upload = page.locator("#upload");
    upload
        .locator("input[type=file]")
        .setInputFiles(
            new FilePayload("made-up.pdf", "application/pdf", MadeUpCertificate.pdf("2022")));
    button(upload, "Hochladen").click();

    // The read values are shown for a check before anything is saved.
    Locator check = page.locator(".upload-check");
    assertThat(check).containsText("made-up.pdf");
    Locator grossWageRow = check.locator("tr[data-field$=-grossWage]");
    assertThat(grossWageRow.locator(".read")).hasText("24.000,00");
    assertThat(grossWageRow.locator("input")).hasValue("24.000,00");
    assertThat(check.locator("tr[data-field$=-solidarity] .read")).hasText("nicht gefunden");
    button(upload, "Werte übernehmen").click();

    // The personal details come first. Then the interview skips each question that the
    // certificate answered, up to the first value that the app did not find.
    button(page.locator("#question"), "Ja, die Angaben stimmen").click();
    assertThat(page.locator("#question h2")).containsText("Solidaritätszuschlag");
    answerField(page, "");
    answerField(page, "");
    answerField(page, "");
    assertThat(page.locator("#question h2")).containsText("weiteren Arbeitgeber");
    button(page.locator("#question"), "Nein").click();
    answerField(page, "Made-up job");

    Locator summary = page.locator("#summary");
    assertThat(summary.locator("[data-question=employed] strong")).hasText("Ja");
    assertThat(summary.locator("[data-question=grossWage] strong")).hasText("24.000,00");
    assertThat(summary.locator("[data-question=grossWage] .source"))
        .hasText("(Beleg: made-up.pdf)");
    assertThat(summary.locator("[data-question=employerName] strong"))
        .hasText("Beispiel Werkstatt GmbH");
    assertThat(summary.locator("[data-question=occupation] .source")).hasText("(eigene Angabe)");

    // A prefilled question can still be changed. The value is then the user's own statement.
    summary
        .locator("[data-question=grossWage]")
        .getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName("Ändern"))
        .click();
    assertThat(page.locator("#question [name=answer]")).hasValue("24.000,00");
    assertThat(page.locator("#question .source")).hasText("Quelle: Beleg: made-up.pdf");
    answerField(page, "25.000,00");
    page.navigate(page.url().replaceAll("/interview.*", "/interview/questions/grossWage"));
    assertThat(page.locator("#question [name=answer]")).hasValue("25.000,00");
    assertThat(page.locator("#question .source")).hasText("Quelle: eigene Angabe");
    // The same amount in another format is no change, so the certificate stays the source.
    page.navigate(page.url().replaceAll("/interview.*", "/interview/questions/wageTax"));
    assertThat(page.locator("#question .source")).hasText("Quelle: Beleg: made-up.pdf");
    answerField(page, "3100");
    page.navigate(page.url().replaceAll("/interview.*", "/interview/questions/wageTax"));
    assertThat(page.locator("#question .source")).hasText("Quelle: Beleg: made-up.pdf");
  }

  @Test
  void aPeriodWithBisAndTheYearIsReadAndTheInterviewDoesNotAskForTheDates() {
    Page page = browser.newPage();
    openInterview(page);
    Locator upload = page.locator("#upload");
    upload
        .locator("input[type=file]")
        .setInputFiles(
            new FilePayload(
                "made-up.pdf",
                "application/pdf",
                MadeUpCertificate.pdf("2022", "01.01.2022 bis 30.06.2022")));
    button(upload, "Hochladen").click();

    Locator check = page.locator(".upload-check");
    assertThat(check.locator("tr[data-field$=-employedFrom] .read")).hasText("01.01.2022");
    assertThat(check.locator("tr[data-field$=-employedTo] .read")).hasText("30.06.2022");
    button(upload, "Werte übernehmen").click();

    button(page.locator("#question"), "Ja, die Angaben stimmen").click();
    assertThat(page.locator("#question h2")).containsText("Solidaritätszuschlag");
  }

  @Test
  void theUserCanSkipTheUploadAndAnswerEachQuestionByHand() {
    Page page = browser.newPage();
    openInterview(page);
    button(page.locator("#upload"), "Ohne Dokumente weiter").click();

    button(page.locator("#question"), "Ja, die Angaben stimmen").click();
    assertThat(page.locator("#question h2")).containsText("Arbeitslohn als Arbeitnehmer");
    button(page.locator("#question"), "Ja").click();
    assertThat(page.locator("#question h2")).containsText("Lohnsteuerbescheinigung?");
    button(page.locator("#question"), "Nein").click();
    assertThat(page.locator("#question h2")).containsText("Wie heißt der Arbeitgeber?");
    answerField(page, "Made-up GmbH");
    assertThat(page.locator("#question h2")).containsText("Ab welchem Tag");

    page.navigate(page.url().replaceAll("/interview.*", "/interview/questions/employerName"));
    assertThat(page.locator("#question [name=answer]")).hasValue("Made-up GmbH");
    assertThat(page.locator("#question .source")).hasText("Quelle: eigene Angabe");

    // An interview in progress goes on with its next question, not with the upload.
    page.locator("nav")
        .getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName("Fragen"))
        .click();
    assertThat(page.locator("#upload")).hasCount(0);
    assertThat(page.locator("#question h2")).containsText("Ab welchem Tag");
  }

  private static void answerField(Page page, String value) {
    page.locator("#question [name=answer]").fill(value);
    button(page.locator("#question"), "Weiter").click();
  }

  private static Locator button(Locator scope, String name) {
    return scope.getByRole(
        AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(name).setExact(true));
  }

  private void openInterview(Page page) {
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
    form.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Person anlegen")).click();
    Locator section =
        page.locator("section").filter(new Locator.FilterOptions().setHasText("Made-up " + lastName));
    section
        .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Steuererklärung 2022"))
        .click();
    page.locator("nav")
        .getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName("Fragen"))
        .click();
    assertThat(page.locator("#upload h2")).hasText("Dokumente hochladen");
  }
}
