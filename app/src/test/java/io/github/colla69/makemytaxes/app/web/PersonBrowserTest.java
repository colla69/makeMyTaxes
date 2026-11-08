package io.github.colla69.makemytaxes.app.web;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;
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

/** The personal details belong to the person, and the main form of each year starts with them. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PersonBrowserTest {

  @TempDir static Path databaseFolder;

  private static Playwright playwright;
  private static Browser browser;

  @LocalServerPort private int port;

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("makemytaxes.database.path", () -> databaseFolder.resolve("persons.db").toString());
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
  void theDetailsOfANewPersonFillTheMainForm() {
    Page page = browser.newPage();
    String name = addPerson(page, "01.02.1990", "Musterweg");
    addReturn(page, name, "2022");

    openMainForm(page, name, "2022");
    assertThat(mainField(page, "birthDate")).hasValue("01.02.1990");
    assertThat(mainField(page, "street")).hasValue("Musterweg");
  }

  @Test
  void changedDetailsReachASavedMainForm() {
    Page page = browser.newPage();
    String name = addPerson(page, "01.02.1990", "Musterweg");
    addReturn(page, name, "2022");
    openMainForm(page, name, "2022");
    saveMainForm(page);

    changeStreet(page, name, "Beispielstraße");

    openMainForm(page, name, "2022");
    assertThat(mainField(page, "street")).hasValue("Beispielstraße");
    assertThat(mainField(page, "birthDate")).hasValue("01.02.1990");
  }

  @Test
  void aValueThatTheUserChangedInTheMainFormStaysWhenTheDetailsChange() {
    Page page = browser.newPage();
    String name = addPerson(page, "01.02.1990", "Musterweg");
    addReturn(page, name, "2022");
    openMainForm(page, name, "2022");
    mainField(page, "street").fill("Alte Gasse");
    saveMainForm(page);

    changeStreet(page, name, "Beispielstraße");

    openMainForm(page, name, "2022");
    assertThat(mainField(page, "street")).hasValue("Alte Gasse");
  }

  private static void saveMainForm(Page page) {
    page.locator("#answers").getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Speichern")).click();
    assertThat(page.locator(".saved")).isVisible();
  }

  private void changeStreet(Page page, String name, String street) {
    page.navigate(home());
    section(page, name)
        .getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName("Persönliche Angaben ändern"))
        .click();
    Locator field = page.locator("#details [name=street]");
    assertThat(field).hasValue("Musterweg");
    field.fill(street);
    page.locator("#details").getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Speichern")).click();
    assertThat(page.locator(".saved")).isVisible();
    assertThat(field).hasValue(street);
  }

  @Test
  void aWrongBirthDateShowsAnErrorAndAddsNoPerson() {
    Page page = browser.newPage();
    page.navigate(home());
    String name = "Made-up " + UUID.randomUUID();
    Locator form = page.locator("#new-person");
    form.getByLabel("Name").fill(name);
    form.locator("[name=birthDate]").fill("31.02.1990");
    form.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Person anlegen")).click();

    assertThat(form.locator(".field .error")).hasCount(1);
    assertThat(form.locator("[name=name]")).hasValue(name);
    assertThat(page.locator("h2").filter(new Locator.FilterOptions().setHasText(name))).hasCount(0);
  }

  private String addPerson(Page page, String birthDate, String street) {
    page.navigate(home());
    String name = "Made-up " + UUID.randomUUID();
    Locator form = page.locator("#new-person");
    form.getByLabel("Name").fill(name);
    form.locator("[name=birthDate]").fill(birthDate);
    form.locator("[name=street]").fill(street);
    form.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Person anlegen")).click();
    assertThat(section(page, name)).isVisible();
    return name;
  }

  private void addReturn(Page page, String name, String year) {
    page.navigate(home());
    section(page, name).getByLabel("Steuerjahr").fill(year);
    section(page, name).getByLabel("Steuerjahr").press("Enter");
    assertThat(returnLink(page, name, year)).isVisible();
  }

  private void openMainForm(Page page, String name, String year) {
    page.navigate(home());
    returnLink(page, name, year).click();
    page.locator("nav").getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName("Hauptvordruck")).click();
    assertThat(page.locator("h1")).containsText(year);
  }

  private static Locator returnLink(Page page, String name, String year) {
    return section(page, name)
        .getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName("Steuererklärung " + year));
  }

  private static Locator section(Page page, String name) {
    return page.locator("section").filter(new Locator.FilterOptions().setHasText(name));
  }

  private static Locator mainField(Page page, String fieldId) {
    return page.locator("#answers [name=" + fieldId + "]");
  }

  private String home() {
    return "http://127.0.0.1:" + port + "/";
  }
}
