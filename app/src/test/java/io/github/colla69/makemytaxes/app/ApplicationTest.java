package io.github.colla69.makemytaxes.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.colla69.makemytaxes.app.storage.EncryptedDatabase;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ApplicationTest {

  private static final String PASSPHRASE = "made-up-test-passphrase";
  private static final String MADE_UP_MARKER = "MADE_UP_MARKER_VALUE";

  @TempDir static Path databaseFolder;

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcClient jdbcClient;
  @Autowired private Environment environment;

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("makemytaxes.database.path", () -> databaseFile().toString());
    registry.add("makemytaxes.database.passphrase", () -> PASSPHRASE);
  }

  private static Path databaseFile() {
    return databaseFolder.resolve("test.db");
  }

  @Test
  void bindsOnlyToTheLoopbackAddress() {
    assertThat(environment.getProperty("server.address")).isEqualTo("127.0.0.1");
  }

  @Test
  void servesTheHomePage() throws Exception {
    mockMvc
        .perform(get("/"))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("makeMyTaxes")));
  }

  @Test
  void runsTheMigrationsOnStartup() {
    Integer applied =
        jdbcClient
            .sql("select count(*) from flyway_schema_history where success = 1")
            .query(Integer.class)
            .single();

    assertThat(applied).isPositive();
  }

  @Test
  void storesDataInAFileThatDoesNotShowTheData() throws Exception {
    jdbcClient.sql("insert into household(name) values (?)").param(MADE_UP_MARKER).update();

    String fileContent =
        new String(Files.readAllBytes(databaseFile()), StandardCharsets.ISO_8859_1);

    assertThat(fileContent).doesNotStartWith("SQLite format 3").doesNotContain(MADE_UP_MARKER);
  }

  @Test
  void rejectsAWrongPassphrase() {
    var wrongPassphrase = EncryptedDatabase.dataSource(databaseFile(), "wrong-passphrase");

    assertThatThrownBy(
            () -> {
              try (var connection = wrongPassphrase.getConnection();
                  var statement = connection.createStatement()) {
                statement.executeQuery("select count(*) from household");
              }
            })
        .isInstanceOf(SQLException.class);
  }
}
