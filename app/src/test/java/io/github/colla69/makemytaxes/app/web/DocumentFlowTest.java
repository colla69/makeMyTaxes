package io.github.colla69.makemytaxes.app.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
class DocumentFlowTest {

  /** A made-up PDF. Only the first bytes matter for the type check. */
  private static final byte[] MADE_UP_PDF =
      "%PDF-1.4\n% made-up test document\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);

  @TempDir static Path databaseFolder;

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcClient jdbcClient;

  private long returnId;

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add(
        "makemytaxes.database.path", () -> databaseFolder.resolve("documents.db").toString());
    registry.add("makemytaxes.database.passphrase", () -> "made-up-test-passphrase");
  }

  @BeforeEach
  void createAPersonWithAReturn() throws Exception {
    returnId = newReturn();
  }

  @Test
  void uploadsAPdfAndListsItWithItsDetails() throws Exception {
    upload("lohnsteuer.pdf", MADE_UP_PDF, "WAGE_TAX_CERTIFICATE")
        .andExpect(status().is3xxRedirection());

    mockMvc
        .perform(get("/returns/" + returnId + "/documents"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("lohnsteuer.pdf")))
        .andExpect(content().string(containsString("Lohnsteuerbescheinigung")))
        .andExpect(content().string(containsString("1 KB")));

    var stored =
        jdbcClient
            .sql("select media_type, size_bytes, sha256 from document where tax_return_id = ?")
            .param(returnId)
            .query()
            .singleRow();
    assertThat(stored.get("media_type")).isEqualTo("application/pdf");
    assertThat(((Number) stored.get("size_bytes")).longValue()).isEqualTo(MADE_UP_PDF.length);
    assertThat((String) stored.get("sha256")).hasSize(64);
  }

  @Test
  void opensADocumentInlineWithItsBytes() throws Exception {
    upload("rechnung.pdf", MADE_UP_PDF, "INVOICE_OR_RECEIPT");
    long documentId = onlyDocumentId();

    mockMvc
        .perform(get("/returns/" + returnId + "/documents/" + documentId))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", "application/pdf"))
        .andExpect(header().string("Content-Disposition", containsString("inline")))
        .andExpect(header().string("Content-Disposition", containsString("rechnung.pdf")))
        .andExpect(header().string("X-Content-Type-Options", "nosniff"))
        .andExpect(content().bytes(MADE_UP_PDF));
  }

  @Test
  void anotherReturnCannotOpenTheDocument() throws Exception {
    upload("rechnung.pdf", MADE_UP_PDF, "INVOICE_OR_RECEIPT");
    long documentId = onlyDocumentId();
    long otherReturnId = newReturn();

    mockMvc
        .perform(get("/returns/" + otherReturnId + "/documents/" + documentId))
        .andExpect(status().isNotFound());
  }

  @Test
  void anotherReturnCannotDeleteTheDocument() throws Exception {
    upload("rechnung.pdf", MADE_UP_PDF, "INVOICE_OR_RECEIPT");
    long documentId = onlyDocumentId();
    long otherReturnId = newReturn();

    mockMvc
        .perform(post("/returns/" + otherReturnId + "/documents/" + documentId + "/delete"))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(get("/returns/" + otherReturnId + "/documents").param("delete", "" + documentId))
        .andExpect(status().isNotFound());

    assertThat(documentCount()).isEqualTo(1);
  }

  @Test
  void deletesTheDocumentWithItsBytesItsPayslipMonthAndAWaitingProposal() throws Exception {
    mockMvc.perform(
        multipart("/returns/" + returnId + "/payslips")
            .file(new MockMultipartFile("file", "januar.pdf", "application/pdf", MADE_UP_PDF))
            .param("month", "1"));
    long documentId = onlyDocumentId();
    jdbcClient
        .sql(
            "insert into field_proposal(tax_return_id, form_id, field_id, value, source,"
                + " document_id) values (?, 'anlage-n', 'made-up', '1,00', 'Made-up', ?)")
        .params(returnId, documentId)
        .update();

    mockMvc
        .perform(post("/returns/" + returnId + "/documents/" + documentId + "/delete"))
        .andExpect(status().is3xxRedirection());

    assertThat(documentCount()).isZero();
    assertThat(count("select count(*) from document_content where document_id = ?", documentId))
        .isZero();
    assertThat(count("select count(*) from payslip where document_id = ?", documentId)).isZero();
    assertThat(count("select count(*) from field_proposal where document_id = ?", documentId))
        .isZero();
  }

  @Test
  void keepsADocumentThatAFormAnswerUsesAndNamesTheField() throws Exception {
    upload("rechnung.pdf", MADE_UP_PDF, "INVOICE_OR_RECEIPT");
    long documentId = onlyDocumentId();
    jdbcClient
        .sql(
            "insert into form_answer(tax_return_id, form_id, field_id, value, document_id,"
                + " own_statement) values (?, 'household-services', 'cleaning1-paidAmount', '100,00', ?, 0)")
        .params(returnId, documentId)
        .update();

    mockMvc
        .perform(post("/returns/" + returnId + "/documents/" + documentId + "/delete"))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .string(containsString("<li>Haushaltsnahe Aufwendungen: Bezahlter Betrag (EUR)")));

    assertThat(documentCount()).isEqualTo(1);
  }

  @Test
  void rejectsAFileThatIsNotPdfJpegOrPng() throws Exception {
    byte[] text = "made-up text".getBytes(StandardCharsets.US_ASCII);
    upload("notes.pdf", text, "OTHER")
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Nur PDF, JPEG und PNG")));

    assertThat(documentCount()).isZero();
  }

  @Test
  void rejectsAFileOverTwentyMegabytes() throws Exception {
    byte[] large = new byte[(int) DocumentController.MAX_BYTES + 1];
    System.arraycopy(MADE_UP_PDF, 0, large, 0, MADE_UP_PDF.length);
    upload("large.pdf", large, "OTHER")
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("größer als 20 MB")));

    assertThat(documentCount()).isZero();
  }

  @Test
  void acceptsAFileOfExactlyTwentyMegabytes() throws Exception {
    byte[] limit = new byte[(int) DocumentController.MAX_BYTES];
    System.arraycopy(MADE_UP_PDF, 0, limit, 0, MADE_UP_PDF.length);
    upload("limit.pdf", limit, "OTHER").andExpect(status().is3xxRedirection());

    assertThat(documentCount()).isEqualTo(1);
  }

  @Test
  void detectsJpegAndPngFromTheFirstBytes() {
    assertThat(DocumentController.mediaTypeOf(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}))
        .isEqualTo("image/jpeg");
    assertThat(
            DocumentController.mediaTypeOf(
                new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0}))
        .isEqualTo("image/png");
    assertThat(DocumentController.mediaTypeOf(new byte[] {'G', 'I', 'F', '8'})).isNull();
  }

  private ResultActions upload(String fileName, byte[] bytes, String kind) throws Exception {
    return mockMvc.perform(
        multipart("/returns/" + returnId + "/documents")
            .file(new MockMultipartFile("file", fileName, "application/octet-stream", bytes))
            .param("kind", kind));
  }

  private long newReturn() throws Exception {
    String lastName = UUID.randomUUID().toString();
    mockMvc.perform(post("/persons").param("firstName", "Made-up").param("lastName", lastName));
    long personId =
        jdbcClient
            .sql("select id from person where name = ?")
            .param("Made-up " + lastName)
            .query(Long.class)
            .single();
    mockMvc.perform(post("/persons/" + personId + "/returns").param("taxYear", "2022"));
    return jdbcClient
        .sql("select id from tax_return where person_id = ?")
        .param(personId)
        .query(Long.class)
        .single();
  }

  private long onlyDocumentId() {
    return jdbcClient
        .sql("select id from document where tax_return_id = ?")
        .param(returnId)
        .query(Long.class)
        .single();
  }

  private int count(String sql, long id) {
    return jdbcClient.sql(sql).param(id).query(Integer.class).single();
  }

  private int documentCount() {
    return jdbcClient
        .sql("select count(*) from document where tax_return_id = ?")
        .param(returnId)
        .query(Integer.class)
        .single();
  }
}
