package io.github.colla69.makemytaxes.app.web;

import io.github.colla69.makemytaxes.app.storage.DocumentStore;
import io.github.colla69.makemytaxes.app.storage.ExpenseStore;
import io.github.colla69.makemytaxes.app.storage.ExpenseStore.StoredExpense;
import io.github.colla69.makemytaxes.app.storage.HouseholdStore;
import io.github.colla69.makemytaxes.core.GermanFormats;
import io.github.colla69.makemytaxes.core.TaxYear;
import io.github.colla69.makemytaxes.core.document.DocumentKind;
import io.github.colla69.makemytaxes.core.expense.Evidence;
import io.github.colla69.makemytaxes.core.expense.Expense;
import io.github.colla69.makemytaxes.core.expense.ExpenseSummary;
import io.github.colla69.makemytaxes.core.expense.ExpenseTopic;
import io.github.colla69.makemytaxes.core.form.Kind;
import io.github.colla69.makemytaxes.core.expense.Payment;
import io.github.colla69.makemytaxes.core.expense.Purpose;
import io.github.colla69.makemytaxes.core.form.Forms;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.List;
import org.apache.tomcat.util.http.InvalidParameterException;
import org.apache.tomcat.util.http.fileupload.impl.SizeException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Controller
class ExpenseController {

  /** What the user typed, kept as text so that a rejected form can show it again. */
  record ExpenseForm(
      String paidOn,
      String amount,
      ExpenseTopic topic,
      String description,
      String receipt,
      Long documentId,
      String statedBasis) {

    static ExpenseForm empty() {
      return new ExpenseForm("", "", ExpenseTopic.WORK_EQUIPMENT, "", "", null, "");
    }

    /** The next entry often shares a date and topic with this entry, so those two stay. */
    ExpenseForm forNextEntry() {
      return new ExpenseForm(paidOn, "", topic, "", "", null, "");
    }

    Expense toExpense(TaxYear taxYear) {
      var payment = new Payment(GermanFormats.parseDate(paidOn), GermanFormats.parseMoney(amount));
      if (!payment.isIn(taxYear)) {
        throw new IllegalArgumentException(
            ("Diese Erklärung gilt für %d, aber das Zahlungsdatum ist %s. Eine Ausgabe zählt im"
                    + " Jahr der Zahlung.")
                .formatted(taxYear.value(), GermanFormats.format(payment.paidOn())));
      }
      var evidence = new Evidence(receipt.strip(), null, statedBasis.strip());
      return new Expense(payment, new Purpose(topic, description.strip()), evidence);
    }
  }

  record ExpenseRow(long id, String paidOn, String amount, String topic, String description,
      String receipt, Long documentId, String documentName, String statedBasis) {

    static ExpenseRow of(StoredExpense stored) {
      Expense expense = stored.expense();
      return new ExpenseRow(
          stored.id(),
          GermanFormats.format(expense.payment().paidOn()),
          GermanFormats.format(expense.payment().amount()),
          expense.purpose().topic().label(),
          expense.purpose().description(),
          expense.evidence().receiptNote(),
          stored.documentId(),
          expense.evidence().documentName(),
          expense.evidence().statedBasis());
    }

    public boolean evidenceMissing() {
      return documentId == null && !StringUtils.hasText(statedBasis);
    }
  }

  record TopicTotal(String topic, String total) {}

  private final HouseholdStore household;
  private final ExpenseStore expenses;
  private final DocumentStore documents;

  ExpenseController(HouseholdStore household, ExpenseStore expenses, DocumentStore documents) {
    this.household = household;
    this.expenses = expenses;
    this.documents = documents;
  }

  @GetMapping("/returns/{returnId}")
  String page(@PathVariable long returnId, Model model) {
    fillPanel(returnId, ExpenseForm.empty(), model);
    return "return";
  }

  @PostMapping("/returns/{returnId}/expenses")
  String add(
      @PathVariable long returnId,
      @ModelAttribute ExpenseForm form,
      @RequestParam(required = false) MultipartFile file,
      Model model)
      throws IOException {
    try {
      store(returnId, form, file);
      fillPanel(returnId, form.forNextEntry(), model);
    } catch (IllegalArgumentException e) {
      model.addAttribute("error", e.getMessage());
      fillPanel(returnId, form, model);
    }
    return "return :: panel";
  }

  /** The server stops a file over its own limit before the add method runs. */
  @ExceptionHandler(MaxUploadSizeExceededException.class)
  String tooLarge(HttpServletRequest request, Model model) {
    model.addAttribute("error", DocumentController.TOO_LARGE);
    fillPanel(DocumentController.returnIdOf(request), ExpenseForm.empty(), model);
    return "return :: panel";
  }

  /** The text fields of this form are read first, so Tomcat reports the size in its own way. */
  @ExceptionHandler(InvalidParameterException.class)
  String tooLargeForTheForm(InvalidParameterException e, HttpServletRequest request, Model model) {
    if (!(e.getCause() instanceof SizeException)) {
      throw e;
    }
    return tooLarge(request, model);
  }

  /** Every check runs before the first write, so a rejected form stores nothing. */
  private void store(long returnId, ExpenseForm form, MultipartFile file) throws IOException {
    Expense expense = form.toExpense(taxYearOf(returnId));
    // A browser sends an empty part with no name if the user chose no file.
    if (file == null || !StringUtils.hasText(file.getOriginalFilename())) {
      Long documentId = checkedDocumentId(returnId, form.documentId());
      if (documentId == null && !expense.evidence().hasStatedBasis()) {
        throw new IllegalArgumentException(
            "Bitte ein Dokument wählen, eine Datei hochladen oder die Grundlage angeben.");
      }
      expenses.add(returnId, expense, documentId);
      return;
    }
    if (form.documentId() != null) {
      throw new IllegalArgumentException(
          "Bitte entweder ein Dokument wählen oder eine neue Datei hochladen.");
    }
    expenses.addWithNewDocument(
        returnId,
        expense,
        DocumentController.toNewDocument(DocumentKind.INVOICE_OR_RECEIPT, file));
  }

  private Long checkedDocumentId(long returnId, Long documentId) {
    if (documentId != null && documents.find(returnId, documentId).isEmpty()) {
      throw new IllegalArgumentException("Dieses Dokument gehört nicht zu dieser Erklärung.");
    }
    return documentId;
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
    model.addAttribute("documentOptions", documentOptions(returnId));
    model.addAttribute("rows", stored.stream().map(ExpenseRow::of).toList());
    model.addAttribute("totals", totalsOf(summary));
    model.addAttribute("withoutReceipt", summary.expensesWithoutEvidence());
  }

  private List<Kind.Option> documentOptions(long returnId) {
    return documents.documentsOf(returnId).stream()
        .map(document -> new Kind.Option(String.valueOf(document.id()), document.fileName()))
        .toList();
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
