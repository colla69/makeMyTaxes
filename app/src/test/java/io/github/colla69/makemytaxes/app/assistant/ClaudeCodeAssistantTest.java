package io.github.colla69.makemytaxes.app.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Runs small shell scripts in place of Claude Code, so the handling of the process is tested
 * without Claude Code and without the network.
 */
class ClaudeCodeAssistantTest {

  @TempDir Path folder;

  @Test
  void theAnswerIsWhatTheProcessWrites() throws Exception {
    ClaudeCodeAssistant assistant = withScript("cat", Duration.ofSeconds(10));

    assertEquals("Made-up question", assistant.ask("Made-up question\n"));
  }

  @Test
  void aLongPromptReachesTheProcessWhole() throws Exception {
    ClaudeCodeAssistant assistant = withScript("wc -c", Duration.ofSeconds(10));
    String prompt = "x".repeat(1_000_000);

    assertEquals("1000000", assistant.ask(prompt));
  }

  @Test
  void anErrorOfTheProcessGivesNoAnswer() throws IOException {
    ClaudeCodeAssistant assistant = withScript("cat > /dev/null; exit 3", Duration.ofSeconds(10));

    AssistantException error =
        assertThrows(AssistantException.class, () -> assistant.ask("Made-up question"));
    assertTrue(error.getMessage().contains("3"));
  }

  @Test
  void aProcessThatDoesNotReadTheLongPromptStopsAtTheTimeout() throws IOException {
    ClaudeCodeAssistant assistant = withScript("exec sleep 30", Duration.ofSeconds(1));
    String prompt = "x".repeat(1_000_000);

    long start = System.nanoTime();
    assertThrows(AssistantException.class, () -> assistant.ask(prompt));
    assertTrue(Duration.ofNanos(System.nanoTime() - start).toSeconds() < 10, "stops at the timeout");
  }

  @Test
  void withoutAnAccessClaudeCodeGetsNoTool() throws IOException {
    List<String> command = withScript("true", Duration.ofSeconds(1)).command(folder, null);

    assertEquals("", command.get(command.indexOf("--tools") + 1));
    assertFalse(command.contains("--mcp-config"));
    assertFalse(command.contains("--allowedTools"));
  }

  @Test
  void withAnAccessClaudeCodeGetsOnlyTheDocumentToolPlusReadInTheFolderOfTheQuestion()
      throws IOException {
    Path question = folder.resolve("question");
    DocumentAccess access =
        new DocumentAccess("made-up-token", Set.of(1L), question, "http://127.0.0.1:1/mcp");

    List<String> command = withScript("true", Duration.ofSeconds(1)).command(folder, access);

    assertEquals("Read", command.get(command.indexOf("--tools") + 1));
    assertEquals(
        question.resolve("mcp.json").toString(), command.get(command.indexOf("--mcp-config") + 1));
    int allowed = command.indexOf("--allowedTools");
    assertEquals(
        List.of(
            "mcp__makemytaxes__read_document",
            "Read(/" + question.resolve("documents") + "/**)"),
        command.subList(allowed + 1, allowed + 3));
    assertTrue(command.contains("--strict-mcp-config"));
    assertFalse(String.join(" ", command).contains("made-up-token"), "the token is not on the command line");
  }

  @Test
  void withAnAccessClaudeCodeStartsInTheFolderOfTheDocuments() throws Exception {
    Path question = folder.resolve("question");
    Files.createDirectories(question.resolve("documents"));
    DocumentAccess access =
        new DocumentAccess("made-up-token", Set.of(1L), question, "http://127.0.0.1:1/mcp");

    assertEquals(
        question.resolve("documents").toRealPath().toString(),
        withScript("cat > /dev/null; pwd -P", Duration.ofSeconds(10)).ask("Made-up", access));
  }

  private ClaudeCodeAssistant withScript(String body, Duration timeout) throws IOException {
    Path script = folder.resolve("claude");
    Files.writeString(script, "#!/bin/sh\n" + body + "\n");
    assertTrue(script.toFile().setExecutable(true));
    return new ClaudeCodeAssistant(
        new ClaudeCodeAssistant.Settings(script.toString(), folder.resolve("config"), timeout),
        Map.of(ClaudeCodeAssistant.TOKEN_VARIABLE, "made-up-token", "PATH", "/usr/bin:/bin"),
        ProcessBuilder::start);
  }
}
