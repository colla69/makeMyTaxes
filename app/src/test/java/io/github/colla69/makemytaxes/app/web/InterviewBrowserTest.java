package io.github.colla69.makemytaxes.app.web;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;
import io.github.colla69.makemytaxes.core.form.Kind;
import java.nio.file.Path;
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

/** The interview: questions by life topic, one per screen, that write into the form fields. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class InterviewBrowserTest {

  /** The questions without an employer. */
  private static final int QUESTIONS = 14;

  /** The lines of one certificate and the question for another employer. */
  private static final int QUESTIONS_PER_EMPLOYER = 11;

  /** The four values of one household invoice and the question for another invoice. */
  private static final int QUESTIONS_PER_INVOICE = 5;

  @TempDir static Path databaseFolder;

  private static Playwright playwright;
  private static Browser browser;

  @LocalServerPort private int port;

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("makemytaxes.database.path", () -> databaseFolder.resolve("interview.db").toString());
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
  void allQuestionsOfTheFrameWriteTheirAnswersIntoTheFormFields() {
    Page page = browser.newPage();
    openInterview(page);
    Locator progress = page.locator("#progress");

    // Job: the personal details are shown for confirmation, not asked again.
    assertTopic(page, "Beruf");
    assertThat(page.locator("#details")).containsText("Musterweg");
    assertThat(page.locator("#details input")).hasCount(0);
    assertThat(progress).hasAttribute("value", "0");
    assertThat(back(page)).hasCount(0);
    button(page, "Ja, die Angaben stimmen").click();
    assertThat(progress).hasAttribute("value", "1");
    button(page, "Ja").click();
    answerEmployer(page, "Made-up GmbH", "30.000,00");
    button(page, "Nein").click();
    answerField(page, "Made-up job");
    assertThat(page.locator("#summary")).containsText("Made-up job");
    assertThat(page.locator("#summary")).containsText("Bestätigt");
    forward(page).click();

    assertTopic(page, "Arbeitsweg und Arbeit");
    answerField(page, "12");
    button(page, "Nein").click();
    button(page, "Nein").click();
    assertThat(page.locator("#summary")).containsText("12");
    forward(page).click();

    assertTopic(page, "Versicherungen");
    button(page, "Ja").click();
    button(page, "Nein").click();
    forward(page).click();

    assertTopic(page, "Haushalt");
    button(page, "Ja").click();
    answerInvoice(page, "Made-up Reinigung, R-7, 31.01.2022", "300,00", "250,00");
    button(page, "Nein").click();
    for (int category = 0; category < 3; category++) {
      button(page, "Nein").click();
    }
    button(page, "Nein").click();
    button(page, "Nein").click();
    assertThat(page.locator("#summary")).containsText("Haushalt");
    assertThat(page.locator("#summary")).containsText("Überweisung auf ein Konto");
    forward(page).click();

    assertThat(progress)
        .hasAttribute(
            "value", String.valueOf(QUESTIONS + QUESTIONS_PER_EMPLOYER + QUESTIONS_PER_INVOICE));
    assertThat(page.locator(".topic-summary h2"))
        .hasText(new String[] {"Beruf", "Arbeitsweg und Arbeit", "Versicherungen", "Haushalt"});
    assertThat(page.locator("#steps li a"))
        .containsText(
            new String[] {
              "Abgabepflicht", "Hauptvordruck", "Anlage N", "Ausgaben", "Haushaltsnahe Aufwendungen"
            });
    assertThat(tab(page, "Fragen")).hasClass(Pattern.compile("\\bdone\\b"));

    tab(page, "Hauptvordruck").click();
    assertThat(page.locator("#answers [name=occupation]")).hasValue("Made-up job");
    tab(page, "Anlage N").click();
    assertThat(page.locator("#answers [name=commuteKm]")).hasValue("12");
    tab(page, "Haushaltsnahe Aufwendungen").click();
    assertThat(page.locator("#answers [name=cleaning1-invoice]"))
        .hasValue("Made-up Reinigung, R-7, 31.01.2022");
    assertThat(page.locator("#answers [name=cleaning1-paidAmount]")).hasValue("300,00");
    assertThat(page.locator("#answers [name=cleaning1-labourCost]")).hasValue("250,00");
    assertThat(page.locator("#answers [name=cleaning1-payment]")).hasValue("TRANSFER");
  }

  @Test
  void aCashPaymentStopsTheInvoiceAndAnotherInvoiceGetsItsOwnBlock() {
    Page page = browser.newPage();
    openInterview(page);
    page.navigate(page.url() + "/questions/craftsmen");
    button(page, "Ja").click();

    assertThat(page.locator("#question h2")).containsText("Handwerker 1");
    answerField(page, "Made-up Maler, R-1, 03.05.2022");
    page.locator("#question select[name=answer]").selectOption("CASH");
    button(page, "Weiter").click();
    assertThat(page.locator("#question .error")).containsText("Barzahlung");
    assertThat(page.locator("#question h2")).containsText("Wie haben Sie bezahlt?");

    // Without a transfer, an amount cannot be saved.
    page.locator("#question select[name=answer]").selectOption("");
    button(page, "Weiter").click();
    answerField(page, "800,00");
    assertThat(page.locator("#question .error")).containsText("Zahlungsweg");
    back(page).click();
    page.locator("#question select[name=answer]").selectOption("TRANSFER");
    button(page, "Weiter").click();
    answerField(page, "800,00");
    answerField(page, "500,00");
    assertThat(page.locator("#question h2")).containsText("weitere Rechnung");
    button(page, "Ja").click();

    assertThat(page.locator("#question h2")).containsText("Handwerker 2");
    answerInvoice(page, "Made-up Elektro, R-9, 10.10.2022", "400,00", "100,00");
    button(page, "Nein").click();
    assertThat(page.locator("#question h2")).containsText("Nebenkostenabrechnung");

    tab(page, "Haushaltsnahe Aufwendungen").click();
    assertThat(page.locator("#answers .field-group h2"))
        .containsText(new String[] {"Handwerker 1", "Handwerker 2"});
    assertThat(page.locator("#answers [name=craftsmen2-labourCost]")).hasValue("100,00");

    tab(page, "Übersicht").click();
    Locator form =
        page.locator(".review-form")
            .filter(new Locator.FilterOptions().setHasText("Haushaltsnahe Aufwendungen"));
    assertThat(form).containsText("§ 35a Abs. 3 EStG");
    assertThat(form).containsText("500,00");
    assertThat(form).containsText("100,00");
  }

  @Test
  void eachEmployerGetsTheLinesOfItsCertificateAndItsOwnBlockUntilANo() {
    Page page = browser.newPage();
    openInterview(page);
    button(page, "Ja, die Angaben stimmen").click();
    button(page, "Ja").click();

    // Each line names its number on the Lohnsteuerbescheinigung.
    answerField(page, "Made-up GmbH");
    answerField(page, "01.01.2022");
    answerField(page, "30.06.2022");
    answerField(page, "1");
    assertThat(page.locator("#question h2")).containsText("Nr. 3");
    assertThat(page.locator("#question label")).containsText("Nr. 3 Bruttoarbeitslohn");
    answerField(page, "20.000,00");
    answerField(page, "3.000,00");
    for (int line = 0; line < 4; line++) {
      answerField(page, "");
    }
    assertThat(page.locator("#question h2")).containsText("weiteren Arbeitgeber");
    assertThat(page.locator("#question h2 .hint-text")).containsText("altro datore di lavoro");
    button(page, "Ja").click();

    // The second employer shares the hints of the first.
    assertThat(page.locator("#question h2")).containsText("Arbeitgeber 2");
    assertThat(page.locator("#question h2 .hint-text")).containsText("nome del datore di lavoro");
    assertThat(page.locator("#question .field .hint-text")).containsText("nome del datore di lavoro");
    answerEmployer(page, "Made-up AG", "15.000,00");
    button(page, "Nein").click();
    assertThat(page.locator("#question h2")).containsText("Beruf");
    assertThat(page.locator("#progress"))
        .hasAttribute("value", String.valueOf(2 + 2 * QUESTIONS_PER_EMPLOYER));

    tab(page, "Anlage N").click();
    assertThat(page.locator("#answers .field-group h2"))
        .hasText(new String[] {"Arbeitgeber 1", "Arbeitgeber 2", "Werbungskosten"});
    assertThat(page.locator("#answers [name=employerName]")).hasValue("Made-up GmbH");
    assertThat(page.locator("#answers [name=employedTo]")).hasValue("30.06.2022");
    assertThat(page.locator("#answers [name=employer2-employerName]")).hasValue("Made-up AG");
    assertThat(page.locator("#answers [name=employer2-grossWage]")).hasValue("15.000,00");

    // A save of the tab keeps the second employer.
    page.locator("#answers [name=employer2-grossWage]").fill("16.000,00");
    page.locator("#answers").getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Speichern")).click();
    assertThat(page.locator(".saved")).isVisible();
    assertThat(page.locator("#answers [name=employer2-grossWage]")).hasValue("16.000,00");

    // The period of a certificate must start before it ends.
    page.locator("#answers [name=employer2-employedFrom]").fill("01.06.2022");
    page.locator("#answers [name=employer2-employedTo]").fill("01.05.2022");
    page.locator("#answers").getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Speichern")).click();
    assertThat(page.locator("p.error").first())
        .containsText("Arbeitgeber 2: Das Ende des Dienstverhältnisses liegt vor dem Beginn.");
    page.locator("#answers [name=employer2-employedFrom]").fill("");
    page.locator("#answers [name=employer2-employedTo]").fill("");
    page.locator("#answers").getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Speichern")).click();
    assertThat(page.locator(".saved")).isVisible();

    // The review shows one block for each employer and no sum.
    tab(page, "Übersicht").click();
    Locator anlageN =
        page.locator(".review-form").filter(new Locator.FilterOptions().setHasText("Anlage N"));
    assertThat(anlageN.locator("h3")).containsText(new String[] {"Arbeitgeber 1", "Arbeitgeber 2"});
    assertThat(anlageN).containsText("20.000,00");
    assertThat(anlageN).containsText("16.000,00");
    assertThat(anlageN).not().containsText("36.000,00");
  }

  @Test
  void aValueChangedInTheFormShowsInTheInterview() {
    Page page = browser.newPage();
    openInterview(page);
    button(page, "Ja, die Angaben stimmen").click();
    button(page, "Nein").click();
    answerField(page, "Made-up job");

    tab(page, "Hauptvordruck").click();
    page.locator("#answers [name=occupation]").fill("Made-up other job");
    page.locator("#answers [name=taxOffice]").fill("Made-up Finanzamt");
    page.locator("#answers").getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Speichern")).click();
    assertThat(page.locator(".saved")).isVisible();

    // The interview opens at the first open question. Back from there is the summary of the job.
    tab(page, "Fragen").click();
    assertTopic(page, "Arbeitsweg und Arbeit");
    back(page).click();
    assertThat(page.locator("#summary")).containsText("Made-up other job");
    page.locator("[data-question=occupation]")
        .getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName("Ändern"))
        .click();
    assertThat(fieldAnswer(page)).hasValue("Made-up other job");

    // An answer changes only its own field. The other values and the personal details stay.
    answerField(page, "Made-up third job");
    tab(page, "Hauptvordruck").click();
    assertThat(page.locator("#answers [name=occupation]")).hasValue("Made-up third job");
    assertThat(page.locator("#answers [name=taxOffice]")).hasValue("Made-up Finanzamt");
    assertThat(page.locator("#answers [name=street]")).hasValue("Musterweg");
  }

  @Test
  void backGoesToThePreviousQuestionAndFromTheFirstOfATopicToTheSummaryBefore() {
    Page page = browser.newPage();
    openInterview(page);
    button(page, "Ja, die Angaben stimmen").click();
    back(page).click();
    assertThat(page.locator("#details")).isVisible();

    button(page, "Ja, die Angaben stimmen").click();
    button(page, "Nein").click();
    answerField(page, "");
    assertThat(page.locator("#summary")).containsText("Keine Angabe");
    forward(page).click();
    assertTopic(page, "Arbeitsweg und Arbeit");
    back(page).click();
    assertThat(page.locator("#summary h2")).containsText("Beruf");
  }

  @Test
  void aWrongValueShowsAnErrorAndStaysOnTheQuestion() {
    Page page = browser.newPage();
    openInterview(page);
    button(page, "Ja, die Angaben stimmen").click();
    button(page, "Nein").click();
    answerField(page, "");
    forward(page).click();

    fieldAnswer(page).fill("12,5");
    button(page, "Weiter").click();
    assertThat(page.locator("#question .error")).isVisible();
    assertTopic(page, "Arbeitsweg und Arbeit");
  }

  @Test
  void everyQuestionShowsAnItalianHintOnHover() {
    Page page = browser.newPage();
    openInterview(page);
    Locator hint = page.locator("#question h2 .hint");
    assertThat(hint.locator(".hint-text")).isHidden();
    hint.hover();
    assertThat(hint.locator(".hint-text")).isVisible();

    for (int answered = 0; answered < QUESTIONS; answered++) {
      if (page.locator("#summary").count() > 0) {
        forward(page).click();
      }
      assertThat(hint.locator(".hint-text")).not().isEmpty();
      if (page.locator("#details").count() > 0) {
        button(page, "Ja, die Angaben stimmen").click();
      } else if (fieldAnswer(page).count() > 0) {
        answerField(page, "");
      } else {
        button(page, "Nein").click();
      }
    }
    forward(page).click();
    assertThat(page.locator(".answered li .hint-text")).hasCount(QUESTIONS);
  }

  private static void assertTopic(Page page, String topic) {
    assertThat(page.locator("#question .topic")).containsText(topic);
  }

  private static void answerField(Page page, String value) {
    fieldAnswer(page).fill(value);
    button(page, "Weiter").click();
  }

  /** Answers the lines of one certificate: the name and the gross wage, the rest empty. */
  private static void answerEmployer(Page page, String name, String grossWage) {
    answerField(page, name);
    for (int line = 0; line < 3; line++) {
      answerField(page, "");
    }
    answerField(page, grossWage);
    for (int line = 0; line < 5; line++) {
      answerField(page, "");
    }
  }

  /** Answers one invoice of a household category, paid by transfer. */
  private static void answerInvoice(Page page, String invoice, String paid, String labour) {
    answerField(page, invoice);
    page.locator("#question select[name=answer]").selectOption(Kind.TRANSFER);
    button(page, "Weiter").click();
    answerField(page, paid);
    answerField(page, labour);
  }

  private static Locator fieldAnswer(Page page) {
    return page.locator("#question input[name=answer]");
  }

  private static Locator button(Page page, String name) {
    return page.locator("#question")
        .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(name).setExact(true));
  }

  private static Locator back(Page page) {
    return page.locator("main").getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName("Zurück"));
  }

  private static Locator forward(Page page) {
    return page.locator("#summary").getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName("Weiter"));
  }

  private static Locator tab(Page page, String name) {
    return page.locator("nav").getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName(name));
  }

  private void openInterview(Page page) {
    page.navigate("http://127.0.0.1:" + port + "/");
    String lastName = UUID.randomUUID().toString();
    String name = "Made-up " + lastName;
    Locator form = page.locator("#new-person");
    form.locator("[name=firstName]").fill("Made-up");
    form.locator("[name=lastName]").fill(lastName);
    form.locator("[name=street]").fill("Musterweg");
    form.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Person anlegen")).click();
    Locator section = page.locator("section").filter(new Locator.FilterOptions().setHasText(name));
    section.getByLabel("Steuerjahr").fill("2022");
    section.getByLabel("Steuerjahr").press("Enter");
    section.getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName("Steuererklärung 2022")).click();
    tab(page, "Fragen").click();
    assertThat(page.locator("#question")).isVisible();
  }
}
