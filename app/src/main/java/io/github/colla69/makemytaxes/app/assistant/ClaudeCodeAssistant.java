package io.github.colla69.makemytaxes.app.assistant;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Starts Claude Code in headless mode ({@code claude -p}) and reads its answer. Claude Code logs in
 * with the token in {@code CLAUDE_CODE_OAUTH_TOKEN}. The app never logs or stores the token.
 */
@Component
class ClaudeCodeAssistant implements Assistant {

  static final String MODEL = "claude-opus-5-5";
  static final String TOKEN_VARIABLE = "CLAUDE_CODE_OAUTH_TOKEN";

  /** Where Claude Code is installed and where it keeps its own settings. */
  record Settings(String command, Path configDir, Duration timeout) {}

  /** Starts a process. A test replaces it to see that no process starts. */
  @FunctionalInterface
  interface ProcessStarter {
    Process start(ProcessBuilder builder) throws IOException;
  }

  private final Settings settings;
  private final Map<String, String> environment;
  private final ProcessStarter starter;

  @Autowired
  ClaudeCodeAssistant(
      @Value("${makemytaxes.assistant.command}") String command,
      @Value("${makemytaxes.assistant.config-dir}") Path configDir) {
    this(new Settings(command, configDir, Duration.ofMinutes(5)), System.getenv(), ProcessBuilder::start);
  }

  ClaudeCodeAssistant(Settings settings, Map<String, String> environment, ProcessStarter starter) {
    this.settings = settings;
    this.environment = environment;
    this.starter = starter;
  }

  @Override
  public boolean available() {
    return !token().isBlank() && executable().isPresent();
  }

  @Override
  public String ask(String prompt) throws AssistantException {
    return ask(prompt, null);
  }

  @Override
  public String ask(String prompt, DocumentAccess access) throws AssistantException {
    Path executable =
        executable().orElseThrow(() -> new AssistantException("Claude Code is not installed."));
    if (token().isBlank()) {
      throw new AssistantException("The token for Claude Code is missing.");
    }
    Process process;
    try {
      Files.createDirectories(settings.configDir());
      process = starter.start(builder(executable, access));
    } catch (IOException e) {
      throw new AssistantException("Claude Code did not start.", e);
    }
    try {
      return answer(process, prompt);
    } finally {
      process.destroyForcibly();
    }
  }

  /**
   * Claude Code gets no slash command and no settings file, so it cannot run a command, write a
   * file or reach the network. It keeps no session on disk, because a session holds the prompt as
   * plain text. Without an access it gets no tool and no MCP server. With an access it gets only
   * the document tool and the law tools of the app, and {@code Read}. It starts in the folder of the documents, and
   * the rules let it read only there.
   */
  List<String> command(Path executable, DocumentAccess access) {
    List<String> command =
        new ArrayList<>(
            List.of(
                executable.toString(),
                "-p",
                "--model",
                MODEL,
                "--output-format",
                "text",
                "--restricted",
                "--strict-mcp-config",
                "--disable-slash-commands",
                "--no-session-persistence",
                "--tools"));
    if (access == null) {
      command.add("");
      return command;
    }
    command.addAll(
        List.of(
            "Read",
            "--mcp-config",
            access.mcpConfig().toString(),
            "--allowedTools",
            McpController.READ_DOCUMENT_TOOL));
    LawTools.NAMES.forEach(tool -> command.add(McpController.ruleName(tool)));
    command.add("Read(/" + access.documents() + "/**)");
    return command;
  }

  private ProcessBuilder builder(Path executable, DocumentAccess access) {
    ProcessBuilder builder = new ProcessBuilder(command(executable, access));
    builder.directory(
        access == null ? settings.configDir().toFile() : access.documents().toFile());
    // The error output can hold parts of the prompt, so it does not reach the log of the app.
    builder.redirectError(ProcessBuilder.Redirect.DISCARD);
    // Claude Code gets only what it needs. The passphrase of the database stays out of it, and its
    // own settings folder keeps it away from the user's Claude Code settings.
    Map<String, String> childEnvironment = builder.environment();
    childEnvironment.clear();
    childEnvironment.put("PATH", environment.getOrDefault("PATH", "/usr/bin:/bin"));
    childEnvironment.put("LANG", "C.UTF-8");
    childEnvironment.put("HOME", settings.configDir().toString());
    childEnvironment.put("CLAUDE_CONFIG_DIR", settings.configDir().toString());
    childEnvironment.put(TOKEN_VARIABLE, token());
    return builder;
  }

  private String answer(Process process, String prompt) throws AssistantException {
    // Both pipes run in the background, so the timeout also covers a process that does not read.
    CompletableFuture<Void> input =
        CompletableFuture.runAsync(() -> writeAll(process, prompt));
    CompletableFuture<String> output =
        CompletableFuture.supplyAsync(() -> readAll(process.getInputStream()));
    try {
      if (!process.waitFor(settings.timeout().toMillis(), TimeUnit.MILLISECONDS)) {
        throw new AssistantException("Claude Code did not answer in time.");
      }
      if (process.exitValue() != 0) {
        throw new AssistantException("Claude Code ended with code " + process.exitValue() + ".");
      }
      input.get(10, TimeUnit.SECONDS);
      return output.get(10, TimeUnit.SECONDS).strip();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssistantException("The wait for Claude Code stopped.", e);
    } catch (ExecutionException | TimeoutException e) {
      throw new AssistantException("Claude Code did not take the prompt or give the answer.", e);
    }
  }

  private static void writeAll(Process process, String prompt) {
    try (Writer input = process.outputWriter(StandardCharsets.UTF_8)) {
      input.write(prompt);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static String readAll(InputStream stream) {
    try (stream) {
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private String token() {
    return environment.getOrDefault(TOKEN_VARIABLE, "");
  }

  /** The command as a path, or the first match on the PATH for a bare name. */
  private Optional<Path> executable() {
    String command = settings.command();
    if (command.isBlank()) {
      return Optional.empty();
    }
    if (command.contains(File.separator)) {
      return Optional.of(Path.of(command)).filter(ClaudeCodeAssistant::isExecutableFile);
    }
    String path = environment.getOrDefault("PATH", "");
    return Arrays.stream(path.split(File.pathSeparator))
        .filter(folder -> !folder.isBlank())
        .map(folder -> Path.of(folder, command))
        .filter(ClaudeCodeAssistant::isExecutableFile)
        .findFirst();
  }

  private static boolean isExecutableFile(Path file) {
    return Files.isRegularFile(file) && Files.isExecutable(file);
  }
}
