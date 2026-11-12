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
import io.github.colla69.makemytaxes.app.storage.HouseholdStore;
import io.github.colla69.makemytaxes.core.form.Answers;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
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

/** Each person turns the assistant on and off for their own returns, on the person page. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(AssistantBrowserTest.Fake.class)
class AssistantBrowserTest {

  @TestConfiguration
  static class Fake {
    @Bean
    @Primary
    FakeAssistant fakeAssistant() {
      return new FakeAssistant();
    }
  }

  @TempDir static Path databaseFolder;

  private static Playwright playwright;
  private static Browser browser;

  @LocalServerPort private int port;

  @Autowired private HouseholdStore household;

  @Autowired private Assistant assistant;

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add(
        "makemytaxes.database.path", () -> databaseFolder.resolve("assistant-ui.db").toString());
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

  @AfterEach
  void makeTheFakeAvailableAgain() {
    ((FakeAssistant) assistant).setAvailable(true);
  }

  @Test
  void aPersonTurnsTheAssistantOnAndOffAgain() {
    long personId = addPerson();
    long other = addPerson();
    Page page = browser.newPage();
    page.navigate(personPage(personId));

    Locator section = page.locator("#assistant");
    assertThat(section).containsText("Anthropic");
    assertThat(section.locator(".assistant-state")).hasText("Der Assistent ist aus.");
    assertThat(section.locator(".assistant-unavailable")).hasCount(0);

    button(section, "Assistent einschalten").click();
    assertThat(section.locator(".assistant-state")).hasText("Der Assistent ist an.");
    page.reload();
    assertThat(section.locator(".assistant-state")).hasText("Der Assistent ist an.");
    assertTrue(household.assistantOn(personId));
    assertFalse(household.assistantOn(other), "the other person keeps it off");

    button(section, "Assistent ausschalten").click();
    assertThat(section.locator(".assistant-state")).hasText("Der Assistent ist aus.");
    assertThat(button(section, "Assistent einschalten")).isVisible();
    assertFalse(household.assistantOn(personId));
  }

  @Test
  void thePageSaysWhenTheAssistantIsNotAvailable() {
    ((FakeAssistant) assistant).setAvailable(false);
    long personId = addPerson();
    Page page = browser.newPage();
    page.navigate(personPage(personId));

    assertThat(page.locator("#assistant .assistant-unavailable")).containsText("nicht verfügbar");
    assertThat(page.locator("#details")).isVisible();
  }

  private static Locator button(Locator section, String name) {
    return section.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(name));
  }

  private long addPerson() {
    String name = "Made-up " + UUID.randomUUID();
    household.addPerson(name, new Answers(Map.of()));
    return household.persons().stream()
        .filter(person -> person.name().equals(name))
        .findFirst()
        .orElseThrow()
        .id();
  }

  private String personPage(long personId) {
    return "http://127.0.0.1:" + port + "/persons/" + personId;
  }
}
