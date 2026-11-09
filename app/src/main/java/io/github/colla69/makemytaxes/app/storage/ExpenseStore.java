package io.github.colla69.makemytaxes.app.storage;

import io.github.colla69.makemytaxes.app.storage.DocumentStore.NewDocument;
import io.github.colla69.makemytaxes.core.expense.Expense;
import io.github.colla69.makemytaxes.core.expense.ExpenseTopic;
import io.github.colla69.makemytaxes.core.expense.Money;
import io.github.colla69.makemytaxes.core.expense.Payment;
import io.github.colla69.makemytaxes.core.expense.Purpose;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class ExpenseStore {

  /** The document id is null if the expense has no document. */
  public record StoredExpense(long id, Expense expense, Long documentId) {}

  private final JdbcClient jdbc;
  private final DocumentStore documents;

  ExpenseStore(JdbcClient jdbc, DocumentStore documents) {
    this.jdbc = jdbc;
    this.documents = documents;
  }

  /** The caller checks that the document, if any, belongs to this return. */
  public void add(long returnId, Expense expense, Long documentId) {
    jdbc.sql(
            """
            insert into expense(
              tax_return_id, paid_on, amount_cents, topic, description, receipt_reference,
              document_id)
            values (?, ?, ?, ?, ?, ?, ?)
            """)
        .params(
            returnId,
            expense.payment().paidOn().toString(),
            expense.payment().amount().cents(),
            expense.purpose().topic().name(),
            expense.purpose().description(),
            expense.receiptReference(),
            documentId)
        .update();
  }

  /** One transaction, so that a failure cannot leave a document without its expense. */
  @Transactional
  public void addWithNewDocument(long returnId, Expense expense, NewDocument document) {
    add(returnId, expense, documents.add(returnId, document, LocalDate.now()));
  }

  public List<StoredExpense> expensesOf(long returnId) {
    return jdbc.sql(
            """
            select e.id, e.paid_on, e.amount_cents, e.topic, e.description, e.receipt_reference,
              e.document_id, d.file_name
            from expense e left join document d on d.id = e.document_id
            where e.tax_return_id = ? order by e.paid_on desc, e.id desc
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
              String documentName = row.getString("file_name");
              var expense =
                  new Expense(payment, purpose, row.getString("receipt_reference"), documentName);
              Long documentId = documentName == null ? null : row.getLong("document_id");
              return new StoredExpense(row.getLong("id"), expense, documentId);
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
