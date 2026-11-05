package io.github.colla69.makemytaxes.app.web;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import io.github.colla69.makemytaxes.app.MakeMyTaxesApplication;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/** A real stop and start: the data must come back from the encrypted file on disk. */
class RestartBrowserTest {

  @TempDir Path databaseFolder;

  @Test
  void dataSurvivesARestartOfTheApp() {
    try (Playwright playwright = Playwright.create();
        Browser browser = playwright.chromium().launch()) {
      Page page = browser.newPage();

      try (ConfigurableApplicationContext first = start()) {
        page.navigate(url(first));
        page.getByLabel("Name").fill("Made-up restart person");
        page.getByLabel("Name").press("Enter");
        assertThat(page.locator("section").first()).containsText("Made-up restart person");
      }

      try (ConfigurableApplicationContext second = start()) {
        page.navigate(url(second));
        assertThat(page.locator("section").first()).containsText("Made-up restart person");
      }
    }
  }

  private ConfigurableApplicationContext start() {
    // Command-line arguments win over application.properties, so this test cannot reach a real
    // database. Builder properties lose to that file.
    return new SpringApplicationBuilder(MakeMyTaxesApplication.class)
        .run(
            "--server.port=0",
            "--makemytaxes.database.path=" + databaseFolder.resolve("restart.db"),
            "--makemytaxes.database.passphrase=made-up-test-passphrase");
  }

  private static String url(ConfigurableApplicationContext context) {
    return "http://127.0.0.1:" + context.getEnvironment().getProperty("local.server.port") + "/";
  }
}
