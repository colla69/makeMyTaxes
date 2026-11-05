package io.github.colla69.makemytaxes.app;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Checks the project's own classes only, so a library can still open a connection. So the
 * owner reviews each change that could send data off the machine.
 */
@AnalyzeClasses(
    packages = "io.github.colla69.makemytaxes",
    importOptions = ImportOption.DoNotIncludeTests.class)
class LocalOnlyArchitectureTest {

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
}
