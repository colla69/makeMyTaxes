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

  private static final int QUESTIONS = 14;

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

    assertTopic(page, "Versicherungen");
    button(page, "Ja").click();
    button(page, "Nein").click();
    forward(page).click();

    assertTopic(page, "Haushalt");
    button(page, "Ja").click();
    button(page, "Nein").click();
    button(page, "Nein").click();
    assertThat(page.locator("#summary")).containsText("Haushalt");
    forward(page).click();

    assertThat(progress).hasAttribute("value", String.valueOf(QUESTIONS));
    assertThat(page.locator(".topic-summary h2"))
        .hasText(new String[] {"Beruf", "Arbeitsweg und Arbeit", "Versicherungen", "Haushalt"});
    assertThat(page.locator("#steps li a"))
        .containsText(
            new String[] {"Abgabepflicht", "Hauptvordruck", "Anlage N", "Ausgaben", "Ausgaben", "Ausgaben"});
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
    String name = "Made-up " + UUID.randomUUID();
    Locator form = page.locator("#new-person");
    form.locator("[name=lastName]").fill(name);
    form.locator("[name=firstName]").fill("Made-up");
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
