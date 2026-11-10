package io.github.colla69.makemytaxes.app.web;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.BoundingBox;
import io.github.colla69.makemytaxes.app.storage.HouseholdStore;
import io.github.colla69.makemytaxes.core.TaxYear;
import io.github.colla69.makemytaxes.core.form.Answers;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
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

    openMainForm(page, name, "2022");
    assertThat(mainField(page, "birthDate")).hasValue("01.02.1990");
    assertThat(mainField(page, "street")).hasValue("Musterweg");
  }

  @Test
  void theNamesOfANewPersonFillTheMainForm() {
    Page page = browser.newPage();
    String name = addPerson(page, "01.02.1990", "Musterweg");

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
    openMainForm(page, name, "2022");
    mainField(page, "street").fill("Alte Gasse");
    saveMainForm(page);

    changeStreet(page, name, "Beispielstraße");

    openMainForm(page, name, "2022");
    assertThat(mainField(page, "street")).hasValue("Alte Gasse");
  }

  @Test
  void aDetailSavesWhenTheUserLeavesItsFieldWithoutAClickOnSpeichern() {
    Page page = browser.newPage();
    String name = addPerson(page, "01.02.1990", "Musterweg");
    section(page, name)
        .getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName("Persönliche Angaben ändern"))
        .click();

    page.locator("#details [name=street]").fill("Beispielstraße");
    page.locator("#details [name=birthDate]").fill("31.02.1990");
    page.locator("#details [name=birthDate]").press("Tab");
    assertThat(page.locator("#details .field:has([name=birthDate]) .error")).isVisible();
    page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Zurück")).click();
    // The link waits for the open save, so the next page comes a moment later.
    assertThat(page.locator("#new-person")).isVisible();

    page.goBack();
    page.reload();
    assertThat(page.locator("#details [name=street]")).hasValue("Beispielstraße");
    assertThat(page.locator("#details [name=birthDate]")).hasValue("01.02.1990");
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

  @Test
  void aYearButtonCreatesTheReturnOnceAndThenOpensIt() {
    Page page = browser.newPage();
    String name = addPerson(page, "01.02.1990", "Musterweg");
    assertThat(section(page, name).locator(".year-buttons button"))
        .hasText(
            new String[] {
              "Steuererklärung 2022",
              "Steuererklärung 2023",
              "Steuererklärung 2024",
              "Steuererklärung 2025",
              "Steuererklärung 2026"
            });
    assertThat(section(page, name).getByLabel("Steuerjahr")).hasCount(0);
    assertThat(section(page, name).getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName("Steuererklärung")))
        .hasCount(0);

    yearButton(page, name, "2024").click();
    assertThat(page).hasURL(Pattern.compile("/returns/\\d+$"));
    assertThat(page.locator("h1")).containsText("2024");
    String firstUrl = page.url();

    page.navigate(home());
    yearButton(page, name, "2024").click();
    assertThat(page).hasURL(firstUrl);
    assertThat(page.locator("h1")).containsText("2024");
  }

  @Test
  void aReturnOutsideTheYearButtonsKeepsItsLink() {
    Page page = browser.newPage();
    String name = addPerson(page, "01.02.1990", "Musterweg");
    long personId =
        household.persons().stream().filter(person -> person.name().equals(name)).findFirst().orElseThrow().id();
    household.addReturn(personId, new TaxYear(2019));

    page.navigate(home());
    section(page, name)
        .getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName("Steuererklärung 2019"))
        .click();
    assertThat(page.locator("h1")).containsText("2019");
  }

  @Test
  void theEditLinkStandsOnTheLineOfTheName() {
    Page page = browser.newPage();
    String name = addPerson(page, "01.02.1990", "Musterweg");
    BoundingBox heading = section(page, name).locator("h2").boundingBox();
    BoundingBox link =
        section(page, name)
            .getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName("Persönliche Angaben ändern"))
            .boundingBox();
    BoundingBox section = section(page, name).boundingBox();

    assertTrue(link.y < heading.y + heading.height && heading.y < link.y + link.height, "same line");
    assertTrue(link.x > heading.x + heading.width, "right of the name");
    assertEquals(section.x + section.width, link.x + link.width, 1.0, "aligned right");
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

  private void openMainForm(Page page, String name, String year) {
    page.navigate(home());
    yearButton(page, name, year).click();
    page.locator("nav").getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName("Hauptvordruck")).click();
    assertThat(page.locator("h1")).containsText(year);
  }

  private static Locator yearButton(Page page, String name, String year) {
    return section(page, name)
        .getByRole(
            AriaRole.BUTTON,
            new Locator.GetByRoleOptions().setName("Steuererklärung " + year).setExact(true));
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
