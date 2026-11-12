package io.github.colla69.makemytaxes.app;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaCall.Predicates.target;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.type;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.name;
import static com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import io.github.colla69.makemytaxes.app.assistant.Assistant;
import io.github.colla69.makemytaxes.app.assistant.AssistantException;
import io.github.colla69.makemytaxes.app.assistant.PersonAssistant;
import java.io.IOException;
import java.net.Socket;
import org.junit.jupiter.api.Test;

/**
 * Checks the project's own classes only, so a library can still open a connection. So the
 * owner reviews each change that could send data off the machine.
 *
 * <p>The one exception is the assistant. It starts Claude Code as a process, and Claude Code sends
 * the prompt to Anthropic. No other package may start a process.
 */
@AnalyzeClasses(
    packages = "io.github.colla69.makemytaxes",
    importOptions = ImportOption.DoNotIncludeTests.class)
class LocalOnlyArchitectureTest {

  private static final String ASSISTANT_PACKAGE = "io.github.colla69.makemytaxes.app.assistant..";

  @ArchTest
  static final ArchRule projectCodeDoesNotOpenOutgoingConnections =
      noClasses()
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "java.net.http..",
              "org.springframework.web.client..",
              "org.springframework.web.reactive.function.client..")
          .orShould()
          .dependOnClassesThat()
          .belongToAnyOf(
              java.net.Socket.class,
              java.net.DatagramSocket.class,
              java.net.URLConnection.class);

  @ArchTest
  static final ArchRule onlyTheAssistantStartsAProcess =
      noClasses()
          .that()
          .resideOutsideOfPackage(ASSISTANT_PACKAGE)
          .should()
          .dependOnClassesThat()
          .belongToAnyOf(ProcessBuilder.class, Process.class, ProcessHandle.class)
          .orShould()
          .callMethodWhere(target(name("exec")).and(target(owner(type(Runtime.class)))));

  /** The switch of each person sits in PersonAssistant, so no other class reaches the assistant. */
  @ArchTest
  static final ArchRule onlyPersonAssistantCallsTheAssistant =
      noClasses()
          .that()
          .resideOutsideOfPackage(ASSISTANT_PACKAGE)
          .should()
          .dependOnClassesThat(
              resideInAPackage(ASSISTANT_PACKAGE)
                  .and(not(type(PersonAssistant.class)))
                  .and(not(type(AssistantException.class))));

  @Test
  void theRulesFailForAClassOutsideTheAssistantThatCallsItDirectly() {
    JavaClasses offender = new ClassFileImporter().importClasses(CallsTheAssistant.class);
    assertThrows(AssertionError.class, () -> onlyPersonAssistantCallsTheAssistant.check(offender));
  }

  @Test
  void theRulesFailForAClassOutsideTheAssistantThatStartsAProcess() {
    JavaClasses offender = new ClassFileImporter().importClasses(StartsAProcess.class);
    assertThrows(AssertionError.class, () -> onlyTheAssistantStartsAProcess.check(offender));
  }

  @Test
  void theRulesFailForAClassOutsideTheAssistantThatRunsACommand() {
    JavaClasses offender = new ClassFileImporter().importClasses(RunsACommand.class);
    assertThrows(AssertionError.class, () -> onlyTheAssistantStartsAProcess.check(offender));
  }

  @Test
  void theRulesFailForAClassThatOpensAConnection() {
    JavaClasses offender = new ClassFileImporter().importClasses(OpensAConnection.class);
    assertThrows(
        AssertionError.class, () -> projectCodeDoesNotOpenOutgoingConnections.check(offender));
  }

  /** Made-up offenders. The rules above skip test classes, so these never break the build. */
  static class StartsAProcess {
    void start() throws IOException {
      new ProcessBuilder("true").start();
    }
  }

  static class CallsTheAssistant {
    String ask(Assistant assistant) throws AssistantException {
      return assistant.ask("Made-up question");
    }
  }

  static class RunsACommand {
    void run() throws IOException {
      Runtime.getRuntime().exec(new String[] {"true"});
    }
  }

  static class OpensAConnection {
    void open() throws IOException {
      try (Socket socket = new Socket("127.0.0.1", 1)) {
        socket.isConnected();
      }
    }
  }
}
