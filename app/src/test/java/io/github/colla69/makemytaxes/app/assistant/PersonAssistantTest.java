package io.github.colla69.makemytaxes.app.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.colla69.makemytaxes.app.storage.HouseholdStore;
import io.github.colla69.makemytaxes.core.form.Answers;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** The data of a person reaches Claude Code only while that person has the assistant on. */
@SpringBootTest
class PersonAssistantTest {

  @TempDir static Path databaseFolder;

  @TempDir Path claudeFolder;

  @Autowired private HouseholdStore household;

  @Autowired private ReturnChat chat;

  /** Each process that the assistant would start. The starter never starts a real one. */
  private final List<ProcessBuilder> started = new ArrayList<>();

  private ClaudeCodeAssistant claudeCode;

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add(
        "makemytaxes.database.path", () -> databaseFolder.resolve("assistant.db").toString());
    registry.add("makemytaxes.database.passphrase", () -> "made-up-test-passphrase");
  }

  @BeforeEach
  void madeUpClaudeCode() throws IOException {
    Path command = Files.createFile(claudeFolder.resolve("claude"));
    assertTrue(command.toFile().setExecutable(true));
    claudeCode =
        new ClaudeCodeAssistant(
            new ClaudeCodeAssistant.Settings(
                command.toString(), claudeFolder.resolve("config"), Duration.ofSeconds(5)),
            Map.of(
                ClaudeCodeAssistant.TOKEN_VARIABLE, "made-up-token",
                "PATH", "/usr/bin",
                "MAKEMYTAXES_DATABASE_PASSPHRASE", "made-up-test-passphrase"),
            builder -> {
              started.add(builder);
              throw new IOException("A test starts no process.");
            });
  }

  @Test
  void noProcessStartsWhileThePersonHasTheAssistantOff() {
    long personId = addPerson();
    PersonAssistant assistant = new PersonAssistant(household, claudeCode, chat);

    assertFalse(household.assistantOn(personId));
    assertThrows(AssistantException.class, () -> assistant.ask(personId, "Made-up question"));
    assertEquals(0, started.size());
  }

  @Test
  void noProcessStartsAfterThePersonTurnsTheAssistantOffAgain() {
    long personId = addPerson();
    household.switchAssistant(personId, true);
    household.switchAssistant(personId, false);

    PersonAssistant assistant = new PersonAssistant(household, claudeCode, chat);
    assertThrows(AssistantException.class, () -> assistant.ask(personId, "Made-up question"));
    assertEquals(0, started.size());
  }

  @Test
  void theAssistantOfOnePersonDoesNotOpenItForTheOther() {
    long on = addPerson();
    long off = addPerson();
    household.switchAssistant(on, true);

    PersonAssistant assistant = new PersonAssistant(household, claudeCode, chat);
    assertThrows(AssistantException.class, () -> assistant.ask(off, "Made-up question"));
    assertEquals(0, started.size());
  }

  @Test
  void aPersonWithTheAssistantOnStartsClaudeCodeWithoutToolsAndWithoutThePassphrase() {
    long personId = addPerson();
    household.switchAssistant(personId, true);

    PersonAssistant assistant = new PersonAssistant(household, claudeCode, chat);
    assertThrows(AssistantException.class, () -> assistant.ask(personId, "Made-up question"));

    assertEquals(1, started.size());
    List<String> command = started.getFirst().command();
    assertEquals(List.of("-p", "--model", "claude-opus-5-5"), command.subList(1, 4));
    int tools = command.indexOf("--tools");
    assertEquals("", command.get(tools + 1), "no built-in tool");
    assertTrue(command.contains("--strict-mcp-config"), "no MCP server");
    assertTrue(command.contains("--no-session-persistence"), "no session on disk");
    Map<String, String> environment = started.getFirst().environment();
    assertEquals(
        Set.of("PATH", "LANG", "HOME", "CLAUDE_CONFIG_DIR", "CLAUDE_CODE_OAUTH_TOKEN"),
        environment.keySet());
    assertEquals(claudeFolder.resolve("config").toString(), environment.get("CLAUDE_CONFIG_DIR"));
  }

  @Test
  void withoutTheTokenTheAssistantIsNotAvailableAndStartsNoProcess() throws IOException {
    Path command = claudeFolder.resolve("claude");
    ClaudeCodeAssistant withoutToken =
        new ClaudeCodeAssistant(
            new ClaudeCodeAssistant.Settings(
                command.toString(), claudeFolder.resolve("config"), Duration.ofSeconds(5)),
            Map.of("PATH", "/usr/bin"),
            builder -> {
              started.add(builder);
              throw new IOException("A test starts no process.");
            });
    long personId = addPerson();
    household.switchAssistant(personId, true);

    assertFalse(withoutToken.available());
    PersonAssistant assistant = new PersonAssistant(household, withoutToken, chat);
    assertThrows(AssistantException.class, () -> assistant.ask(personId, "Made-up question"));
    assertEquals(0, started.size());
  }

  @Test
  void withoutClaudeCodeTheAssistantIsNotAvailable() {
    ClaudeCodeAssistant withoutClaudeCode =
        new ClaudeCodeAssistant(
            new ClaudeCodeAssistant.Settings(
                "claude-code-that-is-not-installed",
                claudeFolder.resolve("config"),
                Duration.ofSeconds(5)),
            Map.of(ClaudeCodeAssistant.TOKEN_VARIABLE, "made-up-token", "PATH", "/nowhere"),
            builder -> {
              throw new IOException("A test starts no process.");
            });

    assertFalse(withoutClaudeCode.available());
    assertTrue(claudeCode.available());
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
}
