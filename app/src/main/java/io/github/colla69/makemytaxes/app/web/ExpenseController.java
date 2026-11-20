package io.github.colla69.makemytaxes.app.web;

import io.github.colla69.makemytaxes.app.assistant.AssistantException;
import io.github.colla69.makemytaxes.app.assistant.PersonAssistant;
import io.github.colla69.makemytaxes.app.storage.DocumentStore;
import io.github.colla69.makemytaxes.app.storage.DocumentStore.NewDocument;
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
import io.github.colla69.makemytaxes.core.expense.Money;
import io.github.colla69.makemytaxes.core.form.Kind;
import io.github.colla69.makemytaxes.core.expense.Payment;
import io.github.colla69.makemytaxes.core.expense.Purpose;
import io.github.colla69.makemytaxes.core.expense.ReceiptFields;
import io.github.colla69.makemytaxes.core.form.Forms;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
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
      String statedBasis,
      String refunded) {

    static ExpenseForm empty() {
      return new ExpenseForm("", "", ExpenseTopic.WORK_EQUIPMENT, "", "", null, "", "");
    }

    /** The fields that the assistant read from a receipt. A field it did not read keeps its value. */
    ExpenseForm with(ReceiptFields read) {
      return new ExpenseForm(
          read.paidOn().isEmpty() ? paidOn : read.paidOn(),
          read.amount().isEmpty() ? amount : read.amount(),
          read.topic() == null ? topic : read.topic(),
          read.description().isEmpty() ? description : read.description(),
          receipt,
          documentId,
          statedBasis,
          refunded);
    }

    /** The next entry often shares a date and topic with this entry, so those two stay. */
    ExpenseForm forNextEntry() {
      return new ExpenseForm(paidOn, "", topic, "", "", null, "", "");
    }

    Expense toExpense(TaxYear taxYear) {
      var payment =
          new Payment(
              FieldException.of("paidOn", () -> GermanFormats.parseDate(paidOn)),
              FieldException.of("amount", () -> GermanFormats.parseMoney(amount)));
      if (!payment.isIn(taxYear)) {
        throw new FieldException(
            "paidOn",
            ("Diese Erklärung gilt für %d, aber das Zahlungsdatum ist %s. Eine Ausgabe zählt im"
                    + " Jahr der Zahlung.")
                .formatted(taxYear.value(), GermanFormats.format(payment.paidOn())));
      }
      var evidence = new Evidence(receipt.strip(), null, statedBasis.strip());
      return new Expense(
          payment, new Purpose(topic, description.strip()), evidence, refundedAmount());
    }

    /** The refund is optional, so an empty field is no refund. */
    private Money refundedAmount() {
      return StringUtils.hasText(refunded)
          ? FieldException.of("refunded", () -> GermanFormats.parseMoney(refunded))
          : Money.ofCents(0);
    }
  }

  /** A rejected value, with the name of the field it came from, so the error can stand beside it. */
  static final class FieldException extends IllegalArgumentException {
    private final String field;

    FieldException(String field, String message) {
      super(message);
      this.field = field;
    }

    static <T> T of(String field, Supplier<T> parse) {
      try {
        return parse.get();
      } catch (IllegalArgumentException e) {
        throw new FieldException(field, e.getMessage());
      }
    }

    String field() {
      return field;
    }
  }

  record ExpenseRow(long id, String paidOn, String amount, String refunded, String topic,
      String description, String receipt, Long documentId, String documentName,
      String statedBasis) {

    static ExpenseRow of(StoredExpense stored) {
      Expense expense = stored.expense();
      return new ExpenseRow(
          stored.id(),
          GermanFormats.format(expense.payment().paidOn()),
          GermanFormats.format(expense.payment().amount()),
          expense.refunded().isPositive() ? GermanFormats.format(expense.refunded()) : "",
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

  /**
   * The fields that the receipt check shows. An error about another field, such as the refund, goes
   * to the page, because the modal dialog hides the field that the user must correct.
   */
  private static final Set<String> RECEIPT_CHECK_FIELDS =
      Set.of("paidOn", "amount", "topic", "description");

  private final HouseholdStore household;
  private final ExpenseStore expenses;
  private final DocumentStore documents;
  private final PersonAssistant assistant;

  ExpenseController(
      HouseholdStore household,
      ExpenseStore expenses,
      DocumentStore documents,
      PersonAssistant assistant) {
    this.household = household;
    this.expenses = expenses;
    this.documents = documents;
    this.assistant = assistant;
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
      @RequestParam(defaultValue = "false") boolean fromReceiptCheck,
      HttpServletResponse response,
      Model model)
      throws IOException {
    try {
      store(returnId, form, file);
      fillPanel(returnId, form.forNextEntry(), model);
    } catch (IllegalArgumentException e) {
      if (fromReceiptCheck
          && e instanceof FieldException rejected
          && RECEIPT_CHECK_FIELDS.contains(rejected.field())) {
        return receiptCheckError(rejected, response, model);
      }
      model.addAttribute("error", e.getMessage());
      fillPanel(returnId, form, model);
    }
    return "return :: panel";
  }

  /**
   * Reads a receipt that the user just chose through the assistant, and answers with the receipt
   * check: a modal dialog with the read fields, where the user completes them and saves. The app
   * stores nothing here: the add method stores the file with the expense. A field that the
   * assistant did not read keeps what the user typed. If the reading fails or the assistant is
   * off, the dialog still opens, with a message.
   */
  @PostMapping("/returns/{returnId}/expenses/read")
  String read(
      @PathVariable long returnId,
      @ModelAttribute ExpenseForm typed,
      @RequestParam(required = false) MultipartFile file,
      HttpServletResponse response,
      Model model)
      throws IOException {
    taxYearOf(returnId);
    if (file == null || file.isEmpty()) {
      return "return :: receipt-check";
    }
    NewDocument receipt;
    try {
      receipt = UploadedFiles.toNewDocument(DocumentKind.INVOICE_OR_RECEIPT, file);
    } catch (IllegalArgumentException e) {
      return readError(e.getMessage(), response, model);
    }
    ReceiptFields read = ReceiptFields.empty();
    if (!assistant.onForReturn(returnId) || !assistant.available()) {
      model.addAttribute("readProblem", "off");
      household.personOf(returnId).ifPresent(id -> model.addAttribute("personId", id));
    } else {
      try {
        read = assistant.readReceipt(returnId, extensionOf(receipt.mediaType()), receipt.bytes());
      } catch (AssistantException e) {
        read = ReceiptFields.empty();
      }
      if (read.isEmpty()) {
        model.addAttribute("readProblem", "failed");
      }
    }
    ExpenseForm form = typed.topic() == null ? ExpenseForm.empty() : typed;
    model.addAttribute("form", form.with(read));
    model.addAttribute("read", read);
    model.addAttribute("topics", topicOptions());
    model.addAttribute("checkReceipt", true);
    return "return :: receipt-check";
  }

  /**
   * Only the error box of the receipt check is swapped, so the dialog stays open with the values
   * and the chosen file, and a second save can store them.
   */
  private static String receiptCheckError(
      FieldException rejected, HttpServletResponse response, Model model) {
    response.setHeader("HX-Retarget", "#receipt-check-error");
    response.setHeader("HX-Reswap", "outerHTML");
    model.addAttribute("checkError", rejected.getMessage());
    model.addAttribute("checkErrorField", rejected.field());
    return "return :: receipt-check-error";
  }

  /** Only the error box is swapped, so the form keeps what the user typed. */
  private static String readError(String message, HttpServletResponse response, Model model) {
    response.setHeader("HX-Retarget", "#read-error");
    response.setHeader("HX-Reswap", "outerHTML");
    model.addAttribute("readError", message);
    return "return :: read-error";
  }

  private static String extensionOf(String mediaType) {
    return switch (mediaType) {
      case "application/pdf" -> "pdf";
      case "image/png" -> "png";
      default -> "jpg";
    };
  }

  /** The server stops a file over its own limit before the add method runs. */
  @ExceptionHandler(MaxUploadSizeExceededException.class)
  String tooLarge(HttpServletRequest request, HttpServletResponse response, Model model) {
    if (request.getRequestURI().endsWith("/expenses/read")) {
      // The typed fields never reached the server, so only the error box is swapped.
      return readError(UploadedFiles.TOO_LARGE, response, model);
    }
    model.addAttribute("error", UploadedFiles.TOO_LARGE);
    fillPanel(UploadedFiles.returnIdOf(request), ExpenseForm.empty(), model);
    return "return :: panel";
  }

  /** The text fields of this form are read first, so Tomcat reports the size in its own way. */
  @ExceptionHandler(InvalidParameterException.class)
  String tooLargeForTheForm(
      InvalidParameterException e,
      HttpServletRequest request,
      HttpServletResponse response,
      Model model) {
    if (!(e.getCause() instanceof SizeException)) {
      throw e;
    }
    return tooLarge(request, response, model);
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
        UploadedFiles.toNewDocument(DocumentKind.INVOICE_OR_RECEIPT, file));
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
    model.addAttribute("read", ReceiptFields.empty());
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
