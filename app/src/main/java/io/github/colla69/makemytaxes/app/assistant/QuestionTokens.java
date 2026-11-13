package io.github.colla69.makemytaxes.app.assistant;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.server.context.WebServerInitializedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Gives each question its own token and folder. The token opens only the returns of its question,
 * and only until the question ends. Then the folder with the documents is deleted.
 */
@Component
class QuestionTokens {

  private static final SecureRandom RANDOM = new SecureRandom();
  private static final String FOLDER_PREFIX = "makemytaxes-question-";

  private final Map<String, DocumentAccess> open = new ConcurrentHashMap<>();
  private volatile int port;

  QuestionTokens(@Value("${server.port:9091}") int port) {
    this.port = port;
  }

  /** The server can take another port than the setting, such as a random port in a test. */
  @EventListener
  void onServerStart(WebServerInitializedEvent event) {
    port = event.getWebServer().getPort();
  }

  /** A new token for the returns of one question, with its own folder. */
  DocumentAccess open(Set<Long> returnIds) {
    byte[] secret = new byte[32];
    RANDOM.nextBytes(secret);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
    try {
      Path folder =
          Files.createTempDirectory(
              FOLDER_PREFIX,
              PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
      DocumentAccess access =
          new DocumentAccess(
              token, Set.copyOf(returnIds), folder, "http://127.0.0.1:" + port + "/mcp");
      Files.createDirectory(access.documents());
      Files.writeString(access.mcpConfig(), mcpConfig(access), StandardCharsets.UTF_8);
      open.put(token, access);
      return access;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** The access of a question that still runs. */
  Optional<DocumentAccess> find(String token) {
    return Optional.ofNullable(token).map(open::get);
  }

  /**
   * Writes a document into the folder of the question, and only while the question still runs. The
   * lock keeps a late tool call from writing into a folder that {@link #close} deletes.
   */
  synchronized Optional<Path> write(DocumentAccess access, String fileName, byte[] bytes) {
    if (open.get(access.token()) != access) {
      return Optional.empty();
    }
    Path file = access.documents().resolve(fileName);
    try {
      Files.write(file, bytes);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return Optional.of(file);
  }

  /** Ends the question: the token stops working and the folder is deleted. */
  synchronized void close(DocumentAccess access) {
    open.remove(access.token());
    try {
      deleteFolder(access.folder());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * A question folder that is left after the app stopped during a question holds copies of
   * documents, so the app deletes it at the start.
   */
  @EventListener(ApplicationReadyEvent.class)
  void deleteLeftFolders() throws IOException {
    Path temp = Path.of(System.getProperty("java.io.tmpdir"));
    try (DirectoryStream<Path> left = Files.newDirectoryStream(temp, FOLDER_PREFIX + "*")) {
      for (Path folder : left) {
        if (open.values().stream().noneMatch(access -> access.folder().equals(folder))) {
          deleteFolder(folder);
        }
      }
    }
  }

  /** The token and the address are plain ASCII, so they need no escaping in JSON. */
  private static String mcpConfig(DocumentAccess access) {
    return """
        {"mcpServers": {"%s": {"type": "http", "url": "%s", \
        "headers": {"Authorization": "Bearer %s"}}}}
        """
        .formatted(McpController.SERVER_NAME, access.mcpUrl(), access.token());
  }

  private static void deleteFolder(Path folder) throws IOException {
    if (!Files.exists(folder)) {
      return;
    }
    Files.walkFileTree(
        folder,
        new SimpleFileVisitor<>() {
          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attributes)
              throws IOException {
            Files.delete(file);
            return FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult postVisitDirectory(Path dir, IOException error)
              throws IOException {
            Files.delete(dir);
            return FileVisitResult.CONTINUE;
          }
        });
  }
}
