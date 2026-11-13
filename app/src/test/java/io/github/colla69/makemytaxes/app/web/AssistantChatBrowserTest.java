package io.github.colla69.makemytaxes.app.web;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.BoundingBox;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;
import io.github.colla69.makemytaxes.app.assistant.Assistant;
import io.github.colla69.makemytaxes.app.assistant.FakeAssistant;
import io.github.colla69.makemytaxes.app.storage.FormStore;
import io.github.colla69.makemytaxes.app.storage.HouseholdStore;
import io.github.colla69.makemytaxes.app.storage.HouseholdStore.StoredPerson;
import io.github.colla69.makemytaxes.core.TaxYear;
import io.github.colla69.makemytaxes.core.form.Answers;
import io.github.colla69.makemytaxes.core.form.MainForm;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** The chat panel beside the tabs of a return, with the fake assistant. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(AssistantChatBrowserTest.Fake.class)
class AssistantChatBrowserTest {

  @TestConfiguration
  static class Fake {
    @Bean
    @Primary
    FakeAssistant fakeAssistant() {
      return new FakeAssistant();
    }
  }

  private static final TaxYear YEAR = new TaxYear(2022);

  private static final int WINDOW_WIDTH = 1280;

  private static final int WINDOW_HEIGHT = 720;

  private static final Browser.NewPageOptions WINDOW =
      new Browser.NewPageOptions().setViewportSize(WINDOW_WIDTH, WINDOW_HEIGHT);

  @TempDir static Path databaseFolder;

  private static Playwright playwright;
  private static Browser browser;

  @LocalServerPort private int port;

  @Autowired private HouseholdStore household;

  @Autowired private FormStore forms;

  @Autowired private Assistant assistant;

  /** One return of a made-up adult, with a street that only this return holds. */
  private record MadeUpReturn(long personId, long returnId, String name, String street) {}

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add(
        "makemytaxes.database.path", () -> databaseFolder.resolve("assistant-chat.db").toString());
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

  /** The tests share one household, so an adult of an earlier test must not join the context. */
  @BeforeEach
  void turnTheAssistantOffForEveryone() {
    for (StoredPerson person : household.persons()) {
      household.switchAssistant(person.id(), false);
    }
    fake().setAvailable(true);
    fake().setAnswer("Made-up answer");
    fake().setDelay(Duration.ZERO);
  }

  @Test
  void thePanelIsMissingWhileTheAssistantIsOff() {
    MadeUpReturn open = addReturn();
    Page page = browser.newPage();
    page.navigate(returnPage(open));

    assertThat(page.locator(".tabs")).isVisible();
    assertThat(page.locator("#assistant-chat")).hasCount(0);
  }

  @Test
  void theOpenChatIsAColumnOnTheRightOverTheFullHeightOfTheWindow() {
    MadeUpReturn open = addReturn();
    household.switchAssistant(open.personId(), true);
    Page page = browser.newPage(WINDOW);
    page.navigate(returnPage(open));

    Locator panel = page.locator("#assistant-chat");
    assertThat(panel.getByLabel("Frage an den Assistenten")).isVisible();
    BoundingBox column = panel.boundingBox();
    assertEquals(WINDOW_WIDTH, column.x + column.width, 1, "the column ends at the right edge");
    assertEquals(0, column.y, "the column starts at the top");
    assertEquals(WINDOW_HEIGHT, column.height, 1, "the column spans the height of the window");
    assertTrue(column.width < WINDOW_WIDTH / 2.0, "the column leaves room for the page");
    BoundingBox content = page.locator("main").boundingBox();
    assertTrue(content.x + content.width <= column.x, "the page sits to the left of the column");
  }

  @Test
  void aLongConversationScrollsOnItsOwnAndKeepsTheQuestionFieldInView() {
    MadeUpReturn open = addReturn();
    household.switchAssistant(open.personId(), true);
    fake().setAnswer("Made-up line of a long answer\n".repeat(20));
    Page page = browser.newPage(WINDOW);
    page.navigate(returnPage(open));
    Locator panel = page.locator("#assistant-chat");
    for (int round = 1; round <= 3; round++) {
      panel.getByLabel("Frage an den Assistenten").fill("Made-up question " + round);
      button(panel, "Fragen").click();
      assertThat(panel.locator(".from-assistant")).hasCount(round);
    }

    Locator messages = panel.locator(".assistant-scroll");
    assertTrue(
        (Boolean) messages.evaluate("list => list.scrollHeight > list.clientHeight"),
        "the conversation overflows the column");
    assertTrue(
        (Boolean)
            messages.evaluate(
                "list => list.scrollTop + list.clientHeight >= list.scrollHeight - 1"),
        "the messages scrolled to the newest one");
    assertThat(panel.locator(".from-assistant").last()).isInViewport();
    assertThat(panel.getByLabel("Frage an den Assistenten")).isInViewport();

    // A long page scrolls, and the column stays where it is.
    page.evaluate("document.querySelector('main').style.minHeight = '3000px'");
    page.evaluate("window.scrollTo(0, 1500)");
    assertTrue(((Number) page.evaluate("window.scrollY")).intValue() > 0, "the page scrolled");
    assertEquals(0, panel.boundingBox().y, "the column stays at the top");
    assertThat(panel.getByLabel("Frage an den Assistenten")).isInViewport();
    assertThat(panel.locator(".from-assistant").last()).isInViewport();

    // After a reload, the newest message shows again.
    page.reload();
    assertThat(panel.locator(".from-assistant")).hasCount(3);
    assertTrue(
        (Boolean)
            messages.evaluate(
                "list => list.scrollTop + list.clientHeight >= list.scrollHeight - 1"),
        "the messages open at the newest one");
  }

  @Test
  void theUserAsksAQuestionAndSeesTheConversationAgainAfterAReload() {
    MadeUpReturn open = addReturn();
    household.switchAssistant(open.personId(), true);
    fake().setAnswer("Made-up answer about the commute");
    Page page = browser.newPage();
    page.navigate(returnPage(open) + "/documents");

    Locator panel = page.locator("#assistant-chat");
    panel.getByLabel("Frage an den Assistenten").fill("Made-up question about the commute");
    fake().setDelay(Duration.ofSeconds(2));
    button(panel, "Fragen").click();

    // While the assistant works, the panel says so and the button waits.
    assertThat(panel.locator("#assistant-working")).hasClass(Pattern.compile("htmx-request"));
    assertThat(button(panel, "Fragen")).isDisabled();
    assertThat(panel.locator(".from-user")).hasText("Made-up question about the commute");
    assertThat(panel.locator(".from-assistant")).hasText("Made-up answer about the commute");
    assertThat(panel.getByLabel("Frage an den Assistenten")).isEmpty();

    page.reload();
    assertThat(panel.locator(".from-user")).hasText("Made-up question about the commute");
    assertThat(panel.locator(".from-assistant")).hasText("Made-up answer about the commute");

    // The next question carries the conversation so far.
    panel.getByLabel("Frage an den Assistenten").fill("Made-up follow-up");
    button(panel, "Fragen").click();
    assertThat(panel.locator(".from-assistant")).hasCount(2);
    String prompt = lastPrompt();
    assertTrue(prompt.contains("Nutzer: Made-up question about the commute"), prompt);
    assertTrue(prompt.contains("Assistent: Made-up answer about the commute"), prompt);
    assertTrue(prompt.endsWith("Made-up follow-up\n"), prompt);
  }

  @Test
  void whileTheOtherAdultHasTheAssistantOffTheContextHoldsOnlyTheOpenReturn() {
    MadeUpReturn open = addReturn();
    MadeUpReturn other = addReturn();
    household.switchAssistant(open.personId(), true);
    Page page = browser.newPage();
    page.navigate(returnPage(open));

    Locator panel = page.locator("#assistant-chat");
    assertThat(panel.locator(".assistant-seen li")).hasText(new String[] {seenName(open)});
    ask(panel, "Made-up question");

    String prompt = lastPrompt();
    assertTrue(prompt.contains(seenName(open)), prompt);
    assertTrue(prompt.contains(open.street()), prompt);
    assertFalse(prompt.contains(other.name()), prompt);
    assertFalse(prompt.contains(other.street()), prompt);
  }

  @Test
  void whileBothAdultsHaveTheAssistantOnTheContextHoldsBothReturnsOfTheYear() {
    MadeUpReturn open = addReturn();
    MadeUpReturn other = addReturn();
    household.switchAssistant(open.personId(), true);
    household.switchAssistant(other.personId(), true);
    household.addReturn(other.personId(), new TaxYear(2023));
    Page page = browser.newPage();
    page.navigate(returnPage(open));

    Locator panel = page.locator("#assistant-chat");
    assertThat(panel.locator(".assistant-seen li"))
        .hasText(new String[] {seenName(open), seenName(other)});
    ask(panel, "Made-up question");

    String prompt = lastPrompt();
    assertTrue(prompt.contains(open.street()), prompt);
    assertTrue(prompt.contains(seenName(other)), prompt);
    assertTrue(prompt.contains(other.street()), prompt);
    assertFalse(prompt.contains("Steuererklärung 2023"), "only the returns of the open year");
  }

  @Test
  void afterTheOtherAdultTurnsTheAssistantOffTheSharedConversationStaysOut() {
    MadeUpReturn open = addReturn();
    MadeUpReturn other = addReturn();
    household.switchAssistant(open.personId(), true);
    household.switchAssistant(other.personId(), true);
    fake().setAnswer("The other adult lives in " + other.street());
    Page page = browser.newPage();
    page.navigate(returnPage(open));
    Locator panel = page.locator("#assistant-chat");
    ask(panel, "Made-up shared question");

    household.switchAssistant(other.personId(), false);
    fake().setAnswer("Made-up answer");
    page.reload();
    assertThat(panel.locator(".assistant-seen li")).hasText(new String[] {seenName(open)});
    assertThat(panel.locator(".assistant-messages li")).hasCount(0);
    ask(panel, "Made-up question alone");

    String prompt = lastPrompt();
    assertFalse(prompt.contains(other.street()), prompt);
    assertFalse(prompt.contains("Made-up shared question"), prompt);
  }

  @Test
  void anErrorOfTheAssistantShowsAsAPlainMessageAndKeepsTheQuestion() {
    MadeUpReturn open = addReturn();
    household.switchAssistant(open.personId(), true);
    fake().setAvailable(false);
    Page page = browser.newPage();
    page.navigate(returnPage(open));

    Locator panel = page.locator("#assistant-chat");
    panel.getByLabel("Frage an den Assistenten").fill("Made-up question without an answer");
    button(panel, "Fragen").click();

    assertThat(panel.locator(".assistant-error")).containsText("Der Assistent hat nicht geantwortet");
    assertThat(panel.getByLabel("Frage an den Assistenten"))
        .hasValue("Made-up question without an answer");
    assertThat(panel.locator(".assistant-messages li")).hasCount(0);
  }

  /** Asks and waits for the answer. */
  private static void ask(Locator panel, String question) {
    panel.getByLabel("Frage an den Assistenten").fill(question);
    button(panel, "Fragen").click();
    assertThat(panel.locator(".from-assistant")).hasCount(1);
  }

  private static Locator button(Locator panel, String name) {
    return panel.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(name));
  }

  private FakeAssistant fake() {
    return (FakeAssistant) assistant;
  }

  private String lastPrompt() {
    List<String> prompts = fake().prompts();
    assertFalse(prompts.isEmpty(), "the assistant got no prompt");
    return prompts.getLast();
  }

  private MadeUpReturn addReturn() {
    String name = "Made-up " + UUID.randomUUID();
    household.addPerson(name, new Answers(Map.of()));
    long personId =
        household.persons().stream()
            .filter(person -> person.name().equals(name))
            .findFirst()
            .orElseThrow()
            .id();
    long returnId = household.addReturn(personId, YEAR);
    String street = "Made-up street " + UUID.randomUUID();
    forms.saveAnswer(returnId, MainForm.ID, "street", street);
    assertEquals(street, forms.answers(returnId, MainForm.ID).values().get("street"));
    return new MadeUpReturn(personId, returnId, name, street);
  }

  private static String seenName(MadeUpReturn stored) {
    return "Steuererklärung " + YEAR.value() + " von " + stored.name();
  }

  private String returnPage(MadeUpReturn stored) {
    return "http://127.0.0.1:" + port + "/returns/" + stored.returnId();
  }
}
