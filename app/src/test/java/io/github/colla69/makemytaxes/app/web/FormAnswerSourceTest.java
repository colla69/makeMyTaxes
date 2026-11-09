package io.github.colla69.makemytaxes.app.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.colla69.makemytaxes.core.form.AnlageN;
import io.github.colla69.makemytaxes.core.form.AnlageVorsorgeaufwand;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
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

/** Each form answer records its source: a document, or the statement of the user. */
@SpringBootTest
@AutoConfigureMockMvc
class FormAnswerSourceTest {

  private static final byte[] MADE_UP_PDF =
      "%PDF-1.4\n% made-up test document\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);

  private static final String DOCUMENT_QUESTION = "wageCertificateDocument";

  @TempDir static Path databaseFolder;

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcClient jdbcClient;

  private long returnId;

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add(
        "makemytaxes.database.path", () -> databaseFolder.resolve("sources.db").toString());
    registry.add("makemytaxes.database.passphrase", () -> "made-up-test-passphrase");
  }

  @BeforeEach
  void createAReturnOfAnEmployee() throws Exception {
    returnId = newReturn();
    answer("employed", "YES");
  }

  @Test
  void aNoToTheCertificateMakesTheAnswersAnOwnStatement() throws Exception {
    answer("wageCertificate", "NO");
    answer("grossWage", "30.000,00");

    assertOwnStatement(AnlageN.ID, "grossWage");
  }

  @Test
  void anUploadedCertificateIsTheSourceOfTheFieldsThatComeFromIt() throws Exception {
    long document = certificateByUpload(DOCUMENT_QUESTION, "made-up.pdf");
    answer("grossWage", "30.000,00");
    answer("employerName", "Made-up GmbH");

    assertFromDocument(AnlageN.ID, "grossWage", document);
    assertFromDocument(AnlageN.ID, "employerName", document);
  }

  @Test
  void aFieldThatIsNotOnTheCertificateStaysAnOwnStatement() throws Exception {
    certificateByUpload(DOCUMENT_QUESTION, "made-up.pdf");
    saveField(AnlageN.ID, "commuteKm", "12");

    assertOwnStatement(AnlageN.ID, "commuteKm");
  }

  @Test
  void aChosenDocumentOfTheReturnIsTheSource() throws Exception {
    long document = uploadOnDocumentsPage("WAGE_TAX_CERTIFICATE");
    answer("wageCertificate", "YES");

    choose(DOCUMENT_QUESTION, document).andExpect(status().is3xxRedirection());
    answer("grossWage", "30.000,00");

    assertFromDocument(AnlageN.ID, "grossWage", document);
  }

  @Test
  void twoEmployersHaveTwoDifferentDocuments() throws Exception {
    long first = certificateByUpload(DOCUMENT_QUESTION, "made-up-first.pdf");
    answer("grossWage", "30.000,00");
    answer("anotherEmployer", "YES");
    answer("employer2-wageCertificate", "YES");
    upload("employer2-" + DOCUMENT_QUESTION, "made-up-second.pdf", MADE_UP_PDF);
    long second =
        jdbcClient
            .sql("select max(id) from document where tax_return_id = ?")
            .param(returnId)
            .query(Long.class)
            .single();
    answer("employer2-grossWage", "15.000,00");

    assertThat(second).isNotEqualTo(first);
    assertFromDocument(AnlageN.ID, "grossWage", first);
    assertFromDocument(AnlageN.ID, "employer2-grossWage", second);
  }

  @Test
  void theValuesOfAnlageVorsorgeaufwandRecordTheirSourceToo() throws Exception {
    long document = certificateByUpload(DOCUMENT_QUESTION, "made-up.pdf");
    saveField(AnlageVorsorgeaufwand.ID, "healthEmployee", "2.400,00");

    assertFromDocument(AnlageVorsorgeaufwand.ID, "healthEmployee", document);
  }

  @Test
  void aSaveOfTheFormTabKeepsTheSource() throws Exception {
    long document = certificateByUpload(DOCUMENT_QUESTION, "made-up.pdf");

    mockMvc
        .perform(
            post("/returns/" + returnId + "/forms/" + AnlageN.ID)
                .param("employerName", "Made-up GmbH")
                .param("taxClass", "1")
                .param("grossWage", "30.000,00")
                .param("wageTax", "3.000,00"))
        .andExpect(status().is3xxRedirection());

    assertFromDocument(AnlageN.ID, "grossWage", document);
  }

  @Test
  void anAutosaveOfOneFieldOnTheFormTabKeepsTheSource() throws Exception {
    long document = certificateByUpload(DOCUMENT_QUESTION, "made-up.pdf");
    saveField(AnlageN.ID, "grossWage", "30.000,00");
    saveField(AnlageN.ID, "grossWage", "31.000,00");

    assertFromDocument(AnlageN.ID, "grossWage", document);
  }

  @Test
  void aChangeFromYesToNoTurnsTheValuesIntoOwnStatements() throws Exception {
    long document = certificateByUpload(DOCUMENT_QUESTION, "made-up.pdf");
    answer("grossWage", "30.000,00");
    assertFromDocument(AnlageN.ID, "grossWage", document);

    answer("wageCertificate", "NO");

    assertOwnStatement(AnlageN.ID, "grossWage");
  }

  @Test
  void aDocumentOfAnotherKindIsNotACertificate() throws Exception {
    long invoice = uploadOnDocumentsPage("INVOICE_OR_RECEIPT");
    answer("wageCertificate", "YES");

    choose(DOCUMENT_QUESTION, invoice)
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Bitte wählen Sie")));
  }

  @Test
  void aDocumentOfAnotherReturnIsNotACertificate() throws Exception {
    long ownReturn = returnId;
    returnId = newReturn();
    long foreign = uploadOnDocumentsPage("WAGE_TAX_CERTIFICATE");
    returnId = ownReturn;
    answer("wageCertificate", "YES");

    choose(DOCUMENT_QUESTION, foreign)
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Bitte wählen Sie")));
  }

  @Test
  void aFileOverTheLimitOfTheAppIsRefused() throws Exception {
    answer("wageCertificate", "YES");
    byte[] large = new byte[(int) DocumentController.MAX_BYTES + 1];
    System.arraycopy(MADE_UP_PDF, 0, large, 0, MADE_UP_PDF.length);

    upload(DOCUMENT_QUESTION, "large.pdf", large)
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("größer als 20 MB")));
  }

  @Test
  void theDocumentQuestionCannotBeAnsweredWithAPlainPost() throws Exception {
    answer("wageCertificate", "YES");

    mockMvc
        .perform(
            post("/returns/" + returnId + "/interview/questions/" + DOCUMENT_QUESTION)
                .param("answer", "1"))
        .andExpect(status().isBadRequest());
  }

  private void answer(String questionId, String value) throws Exception {
    mockMvc
        .perform(
            post("/returns/" + returnId + "/interview/questions/" + questionId)
                .param("answer", value))
        .andExpect(status().is3xxRedirection());
  }

  private void saveField(String formId, String field, String value) throws Exception {
    mockMvc
        .perform(
            post("/returns/" + returnId + "/forms/" + formId + "/field")
                .param("field", field)
                .param("value", value))
        .andExpect(status().isOk());
  }

  private long certificateByUpload(String questionId, String fileName) throws Exception {
    answer("wageCertificate", "YES");
    upload(questionId, fileName, MADE_UP_PDF).andExpect(status().is3xxRedirection());
    return jdbcClient
        .sql("select id from document where tax_return_id = ?")
        .param(returnId)
        .query(Long.class)
        .single();
  }

  private ResultActions upload(String questionId, String fileName, byte[] bytes) throws Exception {
    return mockMvc.perform(
        multipart("/returns/" + returnId + "/interview/questions/" + questionId + "/document")
            .file(new MockMultipartFile("file", fileName, "application/pdf", bytes)));
  }

  private ResultActions choose(String questionId, long documentId) throws Exception {
    return mockMvc.perform(
        multipart("/returns/" + returnId + "/interview/questions/" + questionId + "/document")
            .param("documentId", Long.toString(documentId)));
  }

  private long uploadOnDocumentsPage(String kind) throws Exception {
    mockMvc.perform(
        multipart("/returns/" + returnId + "/documents")
            .file(new MockMultipartFile("file", "made-up.pdf", "application/pdf", MADE_UP_PDF))
            .param("kind", kind));
    return jdbcClient
        .sql("select id from document where tax_return_id = ?")
        .param(returnId)
        .query(Long.class)
        .single();
  }

  private void assertFromDocument(String formId, String field, long document) {
    Map<String, Object> source = sourceOf(formId, field);
    assertThat(((Number) source.get("document_id")).longValue()).isEqualTo(document);
    assertThat(((Number) source.get("own_statement")).intValue()).isZero();
  }

  private void assertOwnStatement(String formId, String field) {
    Map<String, Object> source = sourceOf(formId, field);
    assertThat(source.get("document_id")).isNull();
    assertThat(((Number) source.get("own_statement")).intValue()).isEqualTo(1);
  }

  private Map<String, Object> sourceOf(String formId, String field) {
    return jdbcClient
        .sql(
            "select document_id, own_statement from form_answer"
                + " where tax_return_id = ? and form_id = ? and field_id = ?")
        .params(returnId, formId, field)
        .query()
        .singleRow();
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
}
