package io.github.colla69.makemytaxes.app.storage;

import io.github.colla69.makemytaxes.core.expense.Expense;
import io.github.colla69.makemytaxes.core.expense.ExpenseTopic;
import io.github.colla69.makemytaxes.core.expense.Money;
import io.github.colla69.makemytaxes.core.expense.Payment;
import io.github.colla69.makemytaxes.core.expense.Purpose;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ExpenseStore {

  public record StoredExpense(long id, Expense expense) {}

  private final JdbcClient jdbc;

  ExpenseStore(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public void add(long returnId, Expense expense) {
    jdbc.sql(
            """
            insert into expense(
              tax_return_id, paid_on, amount_cents, topic, description, receipt_reference)
            values (?, ?, ?, ?, ?, ?)
            """)
        .params(
            returnId,
            expense.payment().paidOn().toString(),
            expense.payment().amount().cents(),
            expense.purpose().topic().name(),
            expense.purpose().description(),
            expense.receiptReference())
        .update();
  }

  public List<StoredExpense> expensesOf(long returnId) {
    return jdbc.sql(
            """
            select id, paid_on, amount_cents, topic, description, receipt_reference
            from expense where tax_return_id = ? order by paid_on desc, id desc
            """)
        .param(returnId)
        .query(
            (row, rowNumber) -> {
              var payment =
                  new Payment(
                      LocalDate.parse(row.getString("paid_on")),
                      Money.ofCents(row.getLong("amount_cents")));
              var purpose =
                  new Purpose(
                      ExpenseTopic.valueOf(row.getString("topic")), row.getString("description"));
              var expense = new Expense(payment, purpose, row.getString("receipt_reference"));
              return new StoredExpense(row.getLong("id"), expense);
            })
        .list();
  }

  /** The return id is part of the condition, so one return cannot delete from another. */
  public void delete(long returnId, long expenseId) {
    jdbc.sql("delete from expense where id = ? and tax_return_id = ?")
        .params(expenseId, returnId)
        .update();
  }
}
