package io.github.colla69.makemytaxes.app.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class LocalRequestFilterTest {

  @TempDir static Path databaseFolder;

  @Autowired private MockMvc mockMvc;

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("makemytaxes.database.path", () -> databaseFolder.resolve("filter.db").toString());
    registry.add("makemytaxes.database.passphrase", () -> "made-up-test-passphrase");
  }

  @Test
  void acceptsTheLocalHost() throws Exception {
    mockMvc.perform(get("/").header("Host", "127.0.0.1:8080")).andExpect(status().isOk());
    mockMvc.perform(get("/").header("Host", "localhost:8080")).andExpect(status().isOk());
  }

  @Test
  void rejectsAForeignHostBecauseOfDnsRebinding() throws Exception {
    mockMvc.perform(get("/").header("Host", "evil.example")).andExpect(status().isForbidden());
    mockMvc
        .perform(get("/").header("Host", "127.0.0.1.evil.example:8080"))
        .andExpect(status().isForbidden());
  }

  @Test
  void rejectsAFormFromAForeignPage() throws Exception {
    mockMvc
        .perform(
            post("/persons")
                .header("Host", "127.0.0.1:8080")
                .header("Origin", "https://evil.example")
                .param("name", "Made-up"))
        .andExpect(status().isForbidden());
  }

  @Test
  void rejectsAFormFromAnotherLocalPort() throws Exception {
    mockMvc
        .perform(
            post("/persons")
                .header("Host", "127.0.0.1:8080")
                .header("Origin", "http://127.0.0.1:9999")
                .param("name", "Made-up"))
        .andExpect(status().isForbidden());
  }

  @Test
  void rejectsAFormFromASandboxedPage() throws Exception {
    mockMvc
        .perform(
            post("/persons")
                .header("Host", "127.0.0.1:8080")
                .header("Origin", "null")
                .param("name", "Made-up"))
        .andExpect(status().isForbidden());
  }

  @Test
  void acceptsAFormFromThisApp() throws Exception {
    mockMvc
        .perform(
            post("/persons")
                .header("Host", "127.0.0.1:8080")
                .header("Origin", "http://127.0.0.1:8080")
                .param("name", "Made-up"))
        .andExpect(status().is3xxRedirection());
  }
}
