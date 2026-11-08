package io.github.colla69.makemytaxes.app.web;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;
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

  /** The questions without an employer and with a statutory health insurance. */
  private static final int QUESTIONS = 15;

  /** The lines of one certificate and the question for another employer. */
  private static final int QUESTIONS_PER_EMPLOYER = 11;

  /** The social insurance lines of one certificate, for a statutory health insurance. */
  private static final int STATUTORY_LINES = 7;

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
    answerField(page, "CAR");
    answerField(page, "180");
    answerField(page, "40");
    button(page, "Ja").click();
    button(page, "Nein").click();
    Locator commute = page.locator("#summary");
    assertThat(commute.locator("[data-question=commuteKm] strong")).hasText("12");
    assertThat(commute.locator("[data-question=commuteTransport] strong"))
        .hasText("Eigener oder zur Nutzung überlassener Kraftwagen");
    assertThat(commute.locator("[data-question=commuteDays] strong")).hasText("180");
    assertThat(commute.locator("[data-question=homeOfficeDays] strong")).hasText("40");
    forward(page).click();

    // Insurance: a statutory insurance asks the social insurance lines of the certificate.
    assertTopic(page, "Versicherungen");
    assertThat(page.locator("#question h2")).containsText("gesetzlich oder privat");
    button(page, "Gesetzlich").click();
    assertThat(page.locator("#question h2")).containsText("Nr. 22a/b");
    answerField(page, "2.790,00");
    answerField(page, "2.790,00");
    answerField(page, "");
    answerField(page, "");
    assertThat(page.locator("#question h2")).containsText("Nr. 25");
    answerField(page, "2.400,00");
    answerField(page, "");
    answerField(page, "");
    assertThat(page.locator("#question h2")).containsText("weitere Versicherungen");
    button(page, "Ja").click();
    button(page, "Nein").click();
    assertThat(page.locator("#summary")).containsText("Gesetzlich");
    assertThat(page.locator("#summary")).containsText("2.400,00");
    forward(page).click();

    assertTopic(page, "Haushalt");
    button(page, "Ja").click();
    button(page, "Nein").click();
    button(page, "Nein").click();
    assertThat(page.locator("#summary")).containsText("Haushalt");
    forward(page).click();

    assertThat(progress)
        .hasAttribute(
            "value", String.valueOf(QUESTIONS + QUESTIONS_PER_EMPLOYER + STATUTORY_LINES));
    assertThat(page.locator(".topic-summary h2"))
        .hasText(new String[] {"Beruf", "Arbeitsweg und Arbeit", "Versicherungen", "Haushalt"});
    assertThat(page.locator("#steps li a"))
        .containsText(
            new String[] {
              "Abgabepflicht",
              "Hauptvordruck",
              "Anlage N",
              "Ausgaben",
              "Anlage Vorsorgeaufwand",
              "Ausgaben",
              "Ausgaben"
            });
    assertThat(page.locator("#steps")).containsText("Arbeitsmittel");
    assertThat(page.locator("#steps")).containsText("Haushaltsnahe Aufwendungen");
    assertThat(tab(page, "Fragen")).hasClass(Pattern.compile("\\bdone\\b"));

    tab(page, "Hauptvordruck").click();
    assertThat(page.locator("#answers [name=occupation]")).hasValue("Made-up job");
    tab(page, "Anlage N").click();
    assertThat(page.locator("#answers [name=commuteKm]")).hasValue("12");
    assertThat(page.locator("#answers [name=commuteTransport]")).hasValue("CAR");
    assertThat(page.locator("#answers [name=commuteDays]")).hasValue("180");
    assertThat(page.locator("#answers [name=homeOfficeDays]")).hasValue("40");
    tab(page, "Anlage Vorsorgeaufwand").click();
    assertThat(page.locator("#answers [name=pensionEmployee]")).hasValue("2.790,00");
    assertThat(page.locator("#answers [name=healthEmployee]")).hasValue("2.400,00");
  }

  @Test
  void officeDaysWithoutTheTransportShowWhyTheTransportIsNeeded() {
    Page page = browser.newPage();
    openInterview(page);
    button(page, "Ja, die Angaben stimmen").click();
    button(page, "Nein").click();
    answerField(page, "");
    forward(page).click();
    answerField(page, "12");
    answerField(page, "");

    answerField(page, "180");
    assertThat(page.locator("#question .error")).containsText("Höchstbetrag");
    assertThat(fieldAnswer(page)).hasValue("180");
  }

  @Test
  void aPrivateInsuranceAsksTheLinesOfEachCertificateAndTheCertificateOfTheInsurer() {
    Page page = browser.newPage();
    openInterview(page);
    button(page, "Ja, die Angaben stimmen").click();
    button(page, "Ja").click();
    answerEmployer(page, "Made-up GmbH", "30.000,00");
    button(page, "Ja").click();
    answerEmployer(page, "Made-up AG", "15.000,00");
    button(page, "Nein").click();
    answerField(page, "");
    forward(page).click();
    answerField(page, "");
    answerField(page, "");
    answerField(page, "");
    answerField(page, "");
    button(page, "Nein").click();
    button(page, "Nein").click();
    forward(page).click();

    button(page, "Privat").click();
    // Nr. 22a/b, Nr. 23a/b, Nr. 24b and Nr. 27 for each employer.
    assertThat(page.locator("#question h2")).containsText("Arbeitgeber 1: ");
    for (int line = 0; line < 2; line++) {
      answerField(page, "");
    }
    assertThat(page.locator("#question h2")).containsText("Nr. 24b");
    answerField(page, "1.200,00");
    answerField(page, "");
    assertThat(page.locator("#question h2")).containsText("Arbeitgeber 2: ");
    assertThat(page.locator("#question h2 .hint-text")).containsText("punto 22a");
    for (int line = 0; line < 2; line++) {
      answerField(page, "");
    }
    answerField(page, "600,00");
    answerField(page, "");
    assertThat(page.locator("#question h2")).containsText("Basisabsicherung");
    answerField(page, "4.000,00");
    answerField(page, "500,00");
    answerField(page, "150,00");
    button(page, "Nein").click();
    button(page, "Nein").click();
    assertThat(page.locator("#summary")).containsText("Privat");

    tab(page, "Anlage Vorsorgeaufwand").click();
    assertThat(page.locator("#answers .field-group h2"))
        .hasText(
            new String[] {
              "Arbeitgeber 1", "Arbeitgeber 2", "Private Kranken- und Pflegeversicherung"
            });
    assertThat(page.locator("#answers [name=healthAllowancePrivate]")).hasValue("1.200,00");
    assertThat(page.locator("#answers [name=employer2-healthAllowancePrivate]"))
        .hasValue("600,00");
    assertThat(page.locator("#answers [name=privateHealthBasic]")).hasValue("4.000,00");

    // The review shows the values only, with no sum and no deductible amount.
    tab(page, "Übersicht").click();
    Locator form =
        page.locator(".review-form")
            .filter(new Locator.FilterOptions().setHasText("Anlage Vorsorgeaufwand"));
    assertThat(form).containsText("Nr. 24b");
    assertThat(form).containsText("1.200,00");
    assertThat(form).containsText("600,00");
    assertThat(form).containsText("4.000,00");
    assertThat(form).containsText("150,00");
    assertThat(form).not().containsText("1.800,00");
  }

  @Test
  void aChangeToAPrivateInsuranceRemovesTheStatutoryLines() {
    Page page = browser.newPage();
    openInterview(page);
    button(page, "Ja, die Angaben stimmen").click();
    button(page, "Ja").click();
    answerEmployer(page, "Made-up GmbH", "30.000,00");
    button(page, "Nein").click();
    answerField(page, "");
    forward(page).click();
    answerField(page, "");
    answerField(page, "");
    answerField(page, "");
    answerField(page, "");
    button(page, "Nein").click();
    button(page, "Nein").click();
    forward(page).click();

    button(page, "Gesetzlich").click();
    answerField(page, "");
    answerField(page, "2.790,00");
    answerField(page, "");
    answerField(page, "");
    answerField(page, "2.400,00");

    tab(page, "Fragen").click();
    assertThat(page.locator("#question h2")).containsText("Nr. 26");
    page.navigate(page.url() + "/questions/healthInsurance");
    assertThat(button(page, "Gesetzlich")).hasAttribute("aria-pressed", "true");
    button(page, "Privat").click();

    tab(page, "Anlage Vorsorgeaufwand").click();
    assertThat(page.locator("#answers [name=pensionEmployee]")).hasValue("2.790,00");
    assertThat(page.locator("#answers [name=healthEmployee]")).hasValue("");
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
      } else if (button(page, "Gesetzlich").count() > 0) {
        button(page, "Gesetzlich").click();
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

  /** Types the value, or picks it when the field is a choice. */
  private static void answerField(Page page, String value) {
    Locator answer = fieldAnswer(page);
    if ("select".equals(answer.evaluate("element => element.localName"))) {
      answer.selectOption(value);
    } else {
      answer.fill(value);
    }
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

  private static Locator fieldAnswer(Page page) {
    return page.locator("#question .fields [name=answer]");
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
