package io.github.colla69.makemytaxes.app.web;

import io.github.colla69.makemytaxes.app.storage.ExpenseStore;
import io.github.colla69.makemytaxes.app.storage.ExpenseStore.StoredExpense;
import io.github.colla69.makemytaxes.app.storage.HouseholdStore;
import io.github.colla69.makemytaxes.core.GermanFormats;
import io.github.colla69.makemytaxes.core.TaxYear;
import io.github.colla69.makemytaxes.core.expense.Expense;
import io.github.colla69.makemytaxes.core.expense.ExpenseSummary;
import io.github.colla69.makemytaxes.core.expense.ExpenseTopic;
import io.github.colla69.makemytaxes.core.form.Kind;
import io.github.colla69.makemytaxes.core.expense.Payment;
import io.github.colla69.makemytaxes.core.expense.Purpose;
import io.github.colla69.makemytaxes.core.form.Forms;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ResponseStatusException;

@Controller
class ExpenseController {

  /** What the user typed, kept as text so that a rejected form can show it again. */
  record ExpenseForm(
      String paidOn, String amount, ExpenseTopic topic, String description, String receipt) {

    static ExpenseForm empty() {
      return new ExpenseForm("", "", ExpenseTopic.WORK_EQUIPMENT, "", "");
    }

    /** The next entry often shares a date and topic with this entry, so those two stay. */
    ExpenseForm forNextEntry() {
      return new ExpenseForm(paidOn, "", topic, "", "");
    }

    Expense toExpense(TaxYear taxYear) {
      var payment = new Payment(GermanFormats.parseDate(paidOn), GermanFormats.parseMoney(amount));
      if (!payment.isIn(taxYear)) {
        throw new IllegalArgumentException(
            ("Diese Erklärung gilt für %d, aber das Zahlungsdatum ist %s. Eine Ausgabe zählt im"
                    + " Jahr der Zahlung.")
                .formatted(taxYear.value(), GermanFormats.format(payment.paidOn())));
      }
      return new Expense(payment, new Purpose(topic, description.strip()), receipt.strip());
    }
  }

  record ExpenseRow(long id, String paidOn, String amount, String topic, String description,
      String receipt) {

    static ExpenseRow of(StoredExpense stored) {
      Expense expense = stored.expense();
      return new ExpenseRow(
          stored.id(),
          GermanFormats.format(expense.payment().paidOn()),
          GermanFormats.format(expense.payment().amount()),
          expense.purpose().topic().label(),
          expense.purpose().description(),
          expense.receiptReference());
    }
  }

  record TopicTotal(String topic, String total) {}

  private final HouseholdStore household;
  private final ExpenseStore expenses;

  ExpenseController(HouseholdStore household, ExpenseStore expenses) {
    this.household = household;
    this.expenses = expenses;
  }

  @GetMapping("/returns/{returnId}")
  String page(@PathVariable long returnId, Model model) {
    fillPanel(returnId, ExpenseForm.empty(), model);
    return "return";
  }

  @PostMapping("/returns/{returnId}/expenses")
  String add(@PathVariable long returnId, @ModelAttribute ExpenseForm form, Model model) {
    try {
      expenses.add(returnId, form.toExpense(taxYearOf(returnId)));
      fillPanel(returnId, form.forNextEntry(), model);
    } catch (IllegalArgumentException e) {
      model.addAttribute("error", e.getMessage());
      fillPanel(returnId, form, model);
    }
    return "return :: panel";
  }

  @PostMapping("/returns/{returnId}/expenses/{expenseId}/delete")
  String delete(
      @PathVariable long returnId,
      @PathVariable long expenseId,
      @ModelAttribute ExpenseForm form,
      Model model) {
    expenses.delete(returnId, expenseId);
    // The delete request brings the entry in progress, so a delete does not lose it.
    fillPanel(returnId, form.topic() == null ? ExpenseForm.empty() : form, model);
    return "return :: panel";
  }

  private TaxYear taxYearOf(long returnId) {
    return household
        .taxYearOf(returnId)
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Keine Erklärung mit der ID " + returnId + "."));
  }

  private void fillPanel(long returnId, ExpenseForm form, Model model) {
    List<StoredExpense> stored = expenses.expensesOf(returnId);
    var summary = ExpenseSummary.of(stored.stream().map(StoredExpense::expense).toList());
    model.addAttribute("returnId", returnId);
    model.addAttribute("tab", "expenses");
    model.addAttribute("forms", Forms.of(taxYearOf(returnId)));
    model.addAttribute("taxYear", taxYearOf(returnId).value());
    model.addAttribute("form", form);
    model.addAttribute("topics", topicOptions());
    model.addAttribute("rows", stored.stream().map(ExpenseRow::of).toList());
    model.addAttribute("totals", totalsOf(summary));
    model.addAttribute("withoutReceipt", summary.expensesWithoutEvidence());
  }

  private static List<Kind.Option> topicOptions() {
    return List.of(ExpenseTopic.values()).stream()
        .map(topic -> new Kind.Option(topic.name(), topic.label()))
        .toList();
  }

  private static List<TopicTotal> totalsOf(ExpenseSummary summary) {
    return List.of(ExpenseTopic.values()).stream()
        .filter(topic -> summary.totalFor(topic).isPositive())
        .map(topic -> new TopicTotal(topic.label(), GermanFormats.format(summary.totalFor(topic))))
        .toList();
  }
}
