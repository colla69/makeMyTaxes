package io.github.colla69.makemytaxes.app.web;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;
import io.github.colla69.makemytaxes.app.storage.HouseholdStore;
import io.github.colla69.makemytaxes.core.form.Answers;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
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

  @Autowired private HouseholdStore household;

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
  void theNamesOfANewPersonFillTheMainForm() {
    Page page = browser.newPage();
    String name = addPerson(page, "01.02.1990", "Musterweg");
    addReturn(page, name, "2022");

    openMainForm(page, name, "2022");
    assertThat(mainField(page, "firstName")).hasValue("Made-up");
    assertThat(mainField(page, "lastName")).hasValue(name.substring("Made-up ".length()));
  }

  @Test
  void aPersonWithOneNameKeepsItUntilTheUserSplitsIt() {
    Page page = browser.newPage();
    String oldName = "Made-up old " + UUID.randomUUID();
    household.addPerson(oldName, new Answers(Map.of()));
    page.navigate(home());
    section(page, oldName)
        .getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName("Persönliche Angaben ändern"))
        .click();

    page.locator("#details [name=street]").fill("Musterweg");
    page.locator("#details [name=lastName]").fill("Only a surname");
    page.locator("#details").getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Speichern")).click();
    assertThat(page.locator(".saved")).isVisible();
    page.navigate(home());
    assertThat(page.locator("h2").filter(new Locator.FilterOptions().setHasText(oldName))).hasCount(1);
    section(page, oldName)
        .getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName("Persönliche Angaben ändern"))
        .click();

    String lastName = "Surname " + UUID.randomUUID();
    page.locator("#details [name=firstName]").fill("Made-up");
    page.locator("#details [name=lastName]").fill(lastName);
    page.locator("#details").getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Speichern")).click();
    assertThat(page.locator(".saved")).isVisible();

    page.navigate(home());
    assertThat(page.locator("h2").filter(new Locator.FilterOptions().setHasText(oldName))).hasCount(0);
    assertThat(page.locator("h2").filter(new Locator.FilterOptions().setHasText("Made-up " + lastName)))
        .hasCount(1);
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
    String lastName = UUID.randomUUID().toString();
    Locator form = page.locator("#new-person");
    form.locator("[name=firstName]").fill("Made-up");
    form.locator("[name=lastName]").fill(lastName);
    form.locator("[name=birthDate]").fill("31.02.1990");
    form.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Person anlegen")).click();

    assertThat(form.locator(".field .error")).hasCount(1);
    assertThat(form.locator("[name=lastName]")).hasValue(lastName);
    assertThat(form.locator("[name=firstName]")).hasValue("Made-up");
    assertThat(page.locator("h2").filter(new Locator.FilterOptions().setHasText(lastName))).hasCount(0);
  }

  private String addPerson(Page page, String birthDate, String street) {
    page.navigate(home());
    String lastName = UUID.randomUUID().toString();
    Locator form = page.locator("#new-person");
    form.locator("[name=firstName]").fill("Made-up");
    form.locator("[name=lastName]").fill(lastName);
    form.locator("[name=birthDate]").fill(birthDate);
    form.locator("[name=street]").fill(street);
    form.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Person anlegen")).click();
    String name = "Made-up " + lastName;
    assertThat(page.locator("h2").filter(new Locator.FilterOptions().setHasText(name))).hasCount(1);
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
