package io.github.colla69.makemytaxes.app.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ExpenseFlowTest {

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
    String name = "Made-up " + UUID.randomUUID();
    mockMvc.perform(post("/persons").param("name", name)).andExpect(status().is3xxRedirection());
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
    return mockMvc.perform(
        post("/returns/" + returnId + "/expenses")
            .param("paidOn", paidOn)
            .param("amount", amount)
            .param("topic", "WORK_OTHER")
            .param("description", "Made-up laptop")
            .param("receipt", receipt));
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
  void flagsAnExpenseWithoutAReceipt() throws Exception {
    addExpense("03.02.2022", "10,00", "")
        .andExpect(content().string(containsString("1 without a receipt")));
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

  private int expenseCount() {
    return jdbcClient
        .sql("select count(*) from expense where tax_return_id = ?")
        .param(returnId)
        .query(Integer.class)
        .single();
  }
}
