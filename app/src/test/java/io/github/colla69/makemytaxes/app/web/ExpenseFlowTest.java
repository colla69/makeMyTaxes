package io.github.colla69.makemytaxes.app.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.util.LinkedMultiValueMap;

@SpringBootTest
@AutoConfigureMockMvc
class ExpenseFlowTest {

  /** The app reads the type of a file from these first bytes. */
  private static final byte[] MADE_UP_PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

  @TempDir static Path databaseFolder;

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcClient jdbcClient;

  private long returnId;

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add(
        "makemytaxes.database.path", () -> databaseFolder.resolve("flow.db").toString());
    registry.add("makemytaxes.database.passphrase", () -> "made-up-test-passphrase");
  }

  @Test
  void aReturnForAnUnknownPersonIsNotFound() throws Exception {
    mockMvc
        .perform(post("/persons/999999/returns").param("taxYear", "2022"))
        .andExpect(status().isNotFound());
  }

  @BeforeEach
  void createAPersonWithAReturn() throws Exception {
    String lastName = UUID.randomUUID().toString();
    String name = "Made-up " + lastName;
    mockMvc
        .perform(post("/persons").param("firstName", "Made-up").param("lastName", lastName))
        .andExpect(status().is3xxRedirection());
    long personId =
        jdbcClient
            .sql("select id from person where name = ?")
            .param(name)
            .query(Long.class)
            .single();
    mockMvc
        .perform(post("/persons/" + personId + "/returns").param("taxYear", "2022"))
        .andExpect(status().is3xxRedirection());
    returnId =
        jdbcClient
            .sql("select id from tax_return where person_id = ?")
            .param(personId)
            .query(Long.class)
            .single();
  }

  private org.springframework.test.web.servlet.ResultActions addExpense(
      String paidOn, String amount, String receipt) throws Exception {
    return mockMvc.perform(expense(paidOn, amount, receipt).param("statedBasis", "Made-up 12 days"));
  }

  private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder expense(
      String paidOn, String amount, String receipt) {
    return post("/returns/" + returnId + "/expenses")
        .param("paidOn", paidOn)
        .param("amount", amount)
        .param("topic", "WORK_OTHER")
        .param("description", "Made-up laptop")
        .param("receipt", receipt);
  }

  @Test
  void showsThePersonAndTheReturnOnTheHomePage() throws Exception {
    mockMvc
        .perform(get("/"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("2022")))
        .andExpect(content().string(containsString("/returns/" + returnId)));
  }

  @Test
  void aTaxYearCanHaveOnlyOneReturnForAPerson() throws Exception {
    long personId =
        jdbcClient
            .sql("select person_id from tax_return where id = ?")
            .param(returnId)
            .query(Long.class)
            .single();

    mockMvc
        .perform(post("/persons/" + personId + "/returns").param("taxYear", "2022"))
        .andExpect(status().is3xxRedirection());

    Integer returns =
        jdbcClient
            .sql("select count(*) from tax_return where person_id = ?")
            .param(personId)
            .query(Integer.class)
            .single();
    assertThat(returns).isEqualTo(1);
  }

  @Test
  void addsAnExpenseAndShowsItWithItsTotalInGermanFormat() throws Exception {
    addExpense("03.02.2022", "1.234,56", "laptop.pdf")
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Made-up laptop")))
        .andExpect(content().string(containsString("03.02.2022")))
        .andExpect(content().string(containsString("1.234,56")));

    Long stored =
        jdbcClient
            .sql("select amount_cents from expense where tax_return_id = ?")
            .param(returnId)
            .query(Long.class)
            .single();
    assertThat(stored).isEqualTo(123_456L);
  }

  @Test
  void rejectsAPaymentOutsideTheTaxYearAndStoresNothing() throws Exception {
    addExpense("03.02.2023", "10,00", "laptop.pdf")
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("2022")))
        .andExpect(content().string(containsString("class=\"error\"")));

    assertThat(expenseCount()).isZero();
  }

  @Test
  void rejectsAnInvalidAmountAndKeepsWhatTheUserTyped() throws Exception {
    addExpense("03.02.2022", "abc", "laptop.pdf")
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("class=\"error\"")))
        .andExpect(content().string(containsString("value=\"abc\"")));

    assertThat(expenseCount()).isZero();
  }

  @Test
  void anOldExpenseWithNeitherStaysAndKeepsTheMark() throws Exception {
    insertOldExpense("");

    mockMvc
        .perform(get("/returns/" + returnId))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Made-up old cost")))
        .andExpect(content().string(containsString("Beleg fehlt")))
        .andExpect(content().string(containsString("1 ohne Beleg")));
  }

  @Test
  void aStatedBasisIsEnoughAndIsStoredAndShown() throws Exception {
    mockMvc
        .perform(expense("03.02.2022", "10,00", "").param("statedBasis", "  Made-up 12 days  "))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Grundlage: Made-up 12 days")))
        .andExpect(content().string(not(containsString("Beleg fehlt"))))
        .andExpect(content().string(not(containsString("ohne Beleg"))));

    String stored =
        jdbcClient
            .sql("select stated_basis from expense where tax_return_id = ?")
            .param(returnId)
            .query(String.class)
            .single();
    assertThat(stored).isEqualTo("Made-up 12 days");
  }

  @Test
  void anExpenseWithoutEvidenceIsRejectedAndKeepsWhatTheUserTyped() throws Exception {
    mockMvc
        .perform(expense("03.02.2022", "10,00", "").param("statedBasis", ""))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("die Grundlage angeben")))
        .andExpect(content().string(containsString("value=\"Made-up laptop\"")));

    assertThat(expenseCount()).isZero();
  }

  @Test
  void aBlankStatedBasisIsNotEvidence() throws Exception {
    mockMvc
        .perform(expense("03.02.2022", "10,00", "").param("statedBasis", "   "))
        .andExpect(content().string(containsString("die Grundlage angeben")));

    assertThat(expenseCount()).isZero();
  }

  @Test
  void aReceiptNoteAloneIsNotEvidence() throws Exception {
    mockMvc
        .perform(expense("03.02.2022", "10,00", "made-up-folder").param("statedBasis", ""))
        .andExpect(content().string(containsString("die Grundlage angeben")));

    assertThat(expenseCount()).isZero();
  }

  @Test
  void deletesAnExpense() throws Exception {
    addExpense("03.02.2022", "10,00", "laptop.pdf");
    long expenseId =
        jdbcClient
            .sql("select id from expense where tax_return_id = ?")
            .param(returnId)
            .query(Long.class)
            .single();

    mockMvc
        .perform(post("/returns/" + returnId + "/expenses/" + expenseId + "/delete"))
        .andExpect(status().isOk())
        .andExpect(content().string(not(containsString("Made-up laptop"))));

    assertThat(expenseCount()).isZero();
  }

  @Test
  void anOldReceiptNoteStaysVisibleAndTheDocumentIsStillMissing() throws Exception {
    insertOldExpense("made-up-folder");

    mockMvc
        .perform(get("/returns/" + returnId))
        .andExpect(content().string(containsString("made-up-folder")))
        .andExpect(content().string(containsString("Beleg fehlt")))
        .andExpect(content().string(containsString("1 ohne Beleg")));
  }

  @Test
  void aRejectedExpenseDoesNotStoreItsUpload() throws Exception {
    mockMvc
        .perform(expenseWithFile("03.02.2023"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("class=\"error\"")));

    assertThat(expenseCount()).isZero();
    assertThat(documentCount()).isZero();
  }

  @Test
  void aFileOfAnotherTypeStoresNothing() throws Exception {
    mockMvc
        .perform(
            multipart("/returns/" + returnId + "/expenses")
                .file(new MockMultipartFile("file", "made-up.txt", "text/plain", new byte[] {'x'}))
                .params(expenseFields("03.02.2022")))
        .andExpect(content().string(containsString("Nur PDF, JPEG und PNG")));

    assertThat(expenseCount()).isZero();
    assertThat(documentCount()).isZero();
  }

  @Test
  void anEmptyChosenFileIsAnErrorAndNotAnExpenseWithoutADocument() throws Exception {
    mockMvc
        .perform(
            multipart("/returns/" + returnId + "/expenses")
                .file(new MockMultipartFile("file", "empty.png", "image/png", new byte[0]))
                .params(expenseFields("03.02.2022")))
        .andExpect(content().string(containsString("class=\"error\"")));

    assertThat(expenseCount()).isZero();
  }

  @Test
  void noChosenFileNoChosenDocumentAndNoStatedBasisIsRejected() throws Exception {
    var noFile = new MockMultipartFile("file", "", "application/octet-stream", new byte[0]);
    mockMvc
        .perform(
            multipart("/returns/" + returnId + "/expenses")
                .file(noFile)
                .params(expenseFields("03.02.2022"))
                .param("documentId", ""))
        .andExpect(content().string(containsString("die Grundlage angeben")));

    assertThat(expenseCount()).isZero();
    assertThat(documentCount()).isZero();
  }

  @Test
  void aChosenDocumentIsEnoughWithoutAStatedBasis() throws Exception {
    long document = insertDocument(returnId);

    addExpenseWithDocument(String.valueOf(document))
        .andExpect(content().string(not(containsString("Beleg fehlt"))));

    assertThat(expenseCount()).isEqualTo(1);
  }

  @Test
  void aDocumentOfAnotherReturnCannotBeLinked() throws Exception {
    jdbcClient
        .sql("insert into tax_return(person_id, tax_year) select person_id, 2023 from tax_return where id = ?")
        .param(returnId)
        .update();
    long otherReturn = jdbcClient.sql("select max(id) from tax_return").query(Long.class).single();
    long foreignDocument = insertDocument(otherReturn);

    addExpenseWithDocument(String.valueOf(foreignDocument))
        .andExpect(content().string(containsString("gehört nicht zu dieser Erklärung")));

    assertThat(expenseCount()).isZero();
  }

  @Test
  void aChosenDocumentAndANewFileTogetherAreRejected() throws Exception {
    long document = insertDocument(returnId);

    mockMvc
        .perform(expenseWithFile("03.02.2022").param("documentId", String.valueOf(document)))
        .andExpect(content().string(containsString("entweder ein Dokument")));

    assertThat(expenseCount()).isZero();
    assertThat(documentCount()).isEqualTo(1);
  }

  @Test
  void deletingWithNoChosenDocumentWorks() throws Exception {
    addExpense("03.02.2022", "10,00", "");
    long expenseId =
        jdbcClient
            .sql("select id from expense where tax_return_id = ?")
            .param(returnId)
            .query(Long.class)
            .single();

    mockMvc
        .perform(
            post("/returns/" + returnId + "/expenses/" + expenseId + "/delete")
                .param("documentId", ""))
        .andExpect(status().isOk());

    assertThat(expenseCount()).isZero();
  }

  private org.springframework.test.web.servlet.ResultActions addExpenseWithDocument(
      String documentId) throws Exception {
    return mockMvc.perform(
        post("/returns/" + returnId + "/expenses")
            .params(expenseFields("03.02.2022"))
            .param("documentId", documentId));
  }

  private org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder
      expenseWithFile(String paidOn) {
    return multipart("/returns/" + returnId + "/expenses")
        .file(new MockMultipartFile("file", "made-up.png", "image/png", MADE_UP_PNG))
        .params(expenseFields(paidOn));
  }

  private static LinkedMultiValueMap<String, String> expenseFields(String paidOn) {
    var fields = new LinkedMultiValueMap<String, String>();
    fields.add("paidOn", paidOn);
    fields.add("amount", "10,00");
    fields.add("topic", "WORK_OTHER");
    fields.add("description", "Made-up cable");
    fields.add("receipt", "");
    fields.add("statedBasis", "");
    return fields;
  }

  /** Writes the row directly, because the form no longer accepts an expense without evidence. */
  private void insertOldExpense(String receiptNote) {
    jdbcClient
        .sql(
            """
            insert into expense(
              tax_return_id, paid_on, amount_cents, topic, description, receipt_reference)
            values (?, '2022-02-03', 1000, 'WORK_OTHER', 'Made-up old cost', ?)
            """)
        .params(returnId, receiptNote)
        .update();
  }

  private long insertDocument(long ownerReturnId) {
    jdbcClient
        .sql(
            """
            insert into document(
              tax_return_id, kind, file_name, media_type, size_bytes, sha256, uploaded_on)
            values (?, 'OTHER', 'made-up.png', 'image/png', 1, 'made-up', '2022-01-01')
            """)
        .param(ownerReturnId)
        .update();
    return jdbcClient
        .sql("select max(id) from document where tax_return_id = ?")
        .param(ownerReturnId)
        .query(Long.class)
        .single();
  }

  private int documentCount() {
    return jdbcClient
        .sql("select count(*) from document where tax_return_id = ?")
        .param(returnId)
        .query(Integer.class)
        .single();
  }

  private int expenseCount() {
    return jdbcClient
        .sql("select count(*) from expense where tax_return_id = ?")
        .param(returnId)
        .query(Integer.class)
        .single();
  }
}
