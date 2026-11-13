package io.github.colla69.makemytaxes.app.web;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Locator;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

/** The check of a return for the best legal result, with the fake assistant. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(AssistantAdviceBrowserTest.Fake.class)
class AssistantAdviceBrowserTest {

  @TestConfiguration
  static class Fake {
    @Bean
    @Primary
    FakeAssistant fakeAssistant() {
      return new FakeAssistant();
    }
  }

  private static final TaxYear YEAR = new TaxYear(2022);

  /** Made-up advice: one tip with a source, and one without a source that must not appear. */
  private static final String ADVICE =
      """
      {"tipps": [
        {"art": "abzug", "titel": "Made-up craftsman costs", "text": "Made-up text of the tip",
         "beleg": "Made-up invoice", "quelle": "§ 35a Abs. 3 EStG, Fassung vom 01.01.2022"},
        {"art": "wahl", "titel": "Made-up tip without source", "text": "Made-up", "quelle": ""}
      ]}
      """;

  @TempDir static Path databaseFolder;

  private static Playwright playwright;
  private static Browser browser;

  @LocalServerPort private int port;

  @Autowired private HouseholdStore household;

  @Autowired private FormStore forms;

  @Autowired private Assistant assistant;

  private record MadeUpReturn(long personId, long returnId, String street) {}

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add(
        "makemytaxes.database.path", () -> databaseFolder.resolve("assistant-advice.db").toString());
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

  @BeforeEach
  void turnTheAssistantOffForEveryone() {
    for (StoredPerson person : household.persons()) {
      household.switchAssistant(person.id(), false);
    }
    fake().setAvailable(true);
    fake().setAnswer(ADVICE);
  }

  @Test
  void theReviewPageOffersNoCheckWhileTheAssistantIsOff() {
    MadeUpReturn open = addReturn();
    Page page = browser.newPage();
    page.navigate(returnPage(open) + "/review");

    assertThat(page.locator("h1")).containsText("Übersicht");
    assertThat(page.locator("#advice")).hasCount(0);
  }

  @Test
  void theCheckBeforeTheReviewSendsTheReturnAndShowsTheTipsWithTheirSources() {
    MadeUpReturn open = addReturn();
    household.switchAssistant(open.personId(), true);
    Page page = browser.newPage();
    page.navigate(returnPage(open) + "/review");

    Locator advice = page.locator("#advice");
    button(advice, "Erklärung prüfen lassen").click();

    Locator tip = advice.locator(".advice-tip");
    assertThat(tip).hasCount(1);
    assertThat(tip.locator(".advice-kind")).hasText("Abzug");
    assertThat(tip.locator(".advice-title")).hasText("Made-up craftsman costs");
    assertThat(tip.locator(".advice-document")).hasText("Beleg: Made-up invoice");
    assertThat(tip.locator(".advice-source"))
        .hasText("Quelle: § 35a Abs. 3 EStG, Fassung vom 01.01.2022");
    assertThat(advice).not().containsText("Made-up tip without source");

    String prompt = lastPrompt();
    assertTrue(prompt.contains(open.street()), prompt);
    assertTrue(prompt.contains("Lass einen Tipp ohne Quelle weg."), prompt);
  }

  @Test
  void theChatPanelAsksForAdviceAndKeepsTheTipsInTheConversation() {
    MadeUpReturn open = addReturn();
    household.switchAssistant(open.personId(), true);
    Page page = browser.newPage();
    page.navigate(returnPage(open));

    Locator panel = page.locator("#assistant-chat");
    button(panel, "Rat für das beste Ergebnis").click();

    assertThat(panel.locator(".from-user")).hasText("Rat für das beste legale Ergebnis");
    Locator answer = panel.locator(".from-assistant");
    assertThat(answer).containsText("Abzug: Made-up craftsman costs");
    assertThat(answer).containsText("Quelle: § 35a Abs. 3 EStG, Fassung vom 01.01.2022");
    assertThat(answer).not().containsText("Made-up tip without source");
  }

  @Test
  void whileBothAdultsHaveTheAssistantOnTheCheckSeesBothReturnsAndTheSharedExpenses() {
    MadeUpReturn open = addReturn();
    MadeUpReturn other = addReturn();
    household.switchAssistant(open.personId(), true);
    household.switchAssistant(other.personId(), true);
    Page page = browser.newPage();
    page.navigate(returnPage(open) + "/review");

    button(page.locator("#advice"), "Erklärung prüfen lassen").click();
    assertThat(page.locator(".advice-tip")).hasCount(1);

    String prompt = lastPrompt();
    assertTrue(prompt.contains(open.street()), prompt);
    assertTrue(prompt.contains(other.street()), prompt);
    assertTrue(prompt.contains("gemeinsamen Ausgaben"), prompt);
  }

  @Test
  void anAnswerWithoutAdviceShowsAnErrorAndNoTip() {
    MadeUpReturn open = addReturn();
    household.switchAssistant(open.personId(), true);
    fake().setAnswer("Made-up answer without JSON");
    Page page = browser.newPage();
    page.navigate(returnPage(open) + "/review");

    button(page.locator("#advice"), "Erklärung prüfen lassen").click();

    assertThat(page.locator("#advice .assistant-error"))
        .containsText("Der Assistent hat nicht geantwortet");
    assertThat(page.locator(".advice-tip")).hasCount(0);
  }

  private static Locator button(Locator area, String name) {
    return area.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(name));
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
    return new MadeUpReturn(personId, returnId, street);
  }

  private String returnPage(MadeUpReturn stored) {
    return "http://127.0.0.1:" + port + "/returns/" + stored.returnId();
  }
}
