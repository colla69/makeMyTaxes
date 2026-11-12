package io.github.colla69.makemytaxes.app.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
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
