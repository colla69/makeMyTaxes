package io.github.colla69.makemytaxes.app.web;

import io.github.colla69.makemytaxes.app.document.CertificatePdfReader;
import io.github.colla69.makemytaxes.app.storage.DocumentStore;
import io.github.colla69.makemytaxes.app.storage.ExpenseStore;
import io.github.colla69.makemytaxes.app.storage.ExpenseStore.StoredExpense;
import io.github.colla69.makemytaxes.app.storage.FormStore;
import io.github.colla69.makemytaxes.app.storage.HouseholdStore;
import io.github.colla69.makemytaxes.core.GermanFormats;
import io.github.colla69.makemytaxes.core.TaxYear;
import io.github.colla69.makemytaxes.core.expense.Expense;
import io.github.colla69.makemytaxes.core.expense.ExpenseSummary;
import io.github.colla69.makemytaxes.core.filing.FilingCheck;
import io.github.colla69.makemytaxes.core.form.AnlageN;
import io.github.colla69.makemytaxes.core.form.Answers;
import io.github.colla69.makemytaxes.core.form.CertificateValues;
import io.github.colla69.makemytaxes.core.form.Field;
import io.github.colla69.makemytaxes.core.form.FilingCheckForm;
import io.github.colla69.makemytaxes.core.form.FormModule;
import io.github.colla69.makemytaxes.core.form.Forms;
import io.github.colla69.makemytaxes.core.form.Interview;
import io.github.colla69.makemytaxes.core.form.ReviewLine;
import io.github.colla69.makemytaxes.core.form.WageTaxCertificate2022;
import io.github.colla69.makemytaxes.core.form.WorkExpenseEstimate;
import io.github.colla69.makemytaxes.core.year.YearPack;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

@Controller
class FormController {

  record FilingView(
      String status, List<FilingCheck.Reason> yes, List<FilingCheck.Reason> open,
      String mandatoryDeadline, boolean mandatoryDeadlinePassed, String voluntaryDeadline) {

    static FilingView of(FilingCheck.Result result) {
      return new FilingView(
          result.status().name(),
          result.yes(),
          result.open(),
          GermanFormats.format(result.mandatoryDeadline()),
          LocalDate.now().isAfter(result.mandatoryDeadline()),
          GermanFormats.format(result.voluntaryDeadline()));
    }
  }

  record EstimateView(
      String commute, String homeOffice, String listed, String total, String lumpSum,
      boolean aboveLumpSum, LimitsView limits) {

    static EstimateView of(WorkExpenseEstimate estimate, YearPack pack) {
      return new EstimateView(
          GermanFormats.format(estimate.commute()),
          GermanFormats.format(estimate.homeOffice()),
          GermanFormats.format(estimate.listed()),
          GermanFormats.format(estimate.total()),
          GermanFormats.format(estimate.lumpSum()),
          estimate.isAboveLumpSum(),
          LimitsView.of(pack));
    }
  }

  /** The rates and limits of the year pack that the estimate uses. The page names their source. */
  record LimitsView(
      String perKmFirst20, String perKmFrom21, String commuteLimit, String homeOfficePerDay,
      String homeOfficeLimit) {

    static LimitsView of(YearPack pack) {
      return new LimitsView(
          GermanFormats.format(pack.commutePerKmFirst20()),
          GermanFormats.format(pack.commutePerKmFrom21()),
          GermanFormats.format(pack.commuteLimitWithoutCar()),
          GermanFormats.format(pack.homeOfficePerDay()),
          GermanFormats.format(pack.homeOfficeLimit()));
    }
  }

  record WithoutFormView(String topic, String description, String amount, String evidence) {}

  record SectionView(String title, List<ReviewLine> lines) {}

  record FormReview(String title, List<SectionView> sections, List<Field> missing) {}

  /**
   * The fields of one section. {@code documentId} is the certificate beside them, or null, and
   * {@code documentHref} its address.
   */
  record FieldGroup(String title, List<Field> fields, Long documentId, String documentHref) {}

  /**
   * One field in the check against a certificate. {@code read} is the value that the app read from
   * the certificate, or empty if it did not find one. {@code input} is the value on the page, or
   * the read value if the page has none.
   */
  record CheckRow(
      String fieldId, String label, String certificateLine, String kind, String read,
      String input) {}

  private final HouseholdStore household;
  private final ExpenseStore expenses;
  private final FormStore forms;
  private final DocumentStore documents;

  FormController(
      HouseholdStore household, ExpenseStore expenses, FormStore forms, DocumentStore documents) {
    this.household = household;
    this.expenses = expenses;
    this.forms = forms;
    this.documents = documents;
  }

  @GetMapping("/returns/{returnId}/forms/{formId}")
  String form(
      @PathVariable long returnId,
      @PathVariable String formId,
      @RequestParam(defaultValue = "false") boolean saved,
      Model model) {
    FormModule form = formOf(returnId, formId);
    fillForm(returnId, form, forms.answers(returnId, formId).values(), Map.of(), List.of(), model);
    model.addAttribute("saved", saved);
    return "form";
  }

  @PostMapping("/returns/{returnId}/forms/{formId}")
  String save(
      @PathVariable long returnId,
      @PathVariable String formId,
      @RequestParam Map<String, String> submitted,
      Model model) {
    FormModule form = formOf(returnId, formId);
    Map<String, String> values = new LinkedHashMap<>();
    Map<String, String> errors = new LinkedHashMap<>();
    // The submitted names tell which list entries, such as employers, the page had.
    for (Field field : form.fields(new Answers(submitted))) {
      String value = submitted.getOrDefault(field.id(), "").strip();
      if (value.isEmpty()) {
        continue;
      }
      values.put(field.id(), value);
      try {
        field.kind().validate(value);
      } catch (IllegalArgumentException e) {
        errors.put(field.id(), e.getMessage());
      }
    }
    // A value that fails its own check cannot go into a check of several fields.
    List<String> formErrors = errors.isEmpty() ? form.check(new Answers(values)) : List.of();
    if (!errors.isEmpty() || !formErrors.isEmpty()) {
      fillForm(returnId, form, values, errors, formErrors, model);
      model.addAttribute("saved", false);
      return "form";
    }
    forms.save(returnId, formId, new Answers(values));
    return "redirect:/returns/" + returnId + "/forms/" + formId + "?saved=true";
  }

  /**
   * Saves one field when the user leaves it. A value that fails its own check is not saved, and
   * the answer holds the error. Otherwise the answer holds the messages of the checks of several
   * fields, one per line. They do not stop the save, because the other value of such a check can
   * still be missing.
   */
  @PostMapping("/returns/{returnId}/forms/{formId}/field")
  @ResponseBody
  ResponseEntity<String> saveField(
      @PathVariable long returnId,
      @PathVariable String formId,
      @RequestParam String field,
      @RequestParam(defaultValue = "") String value) {
    FormModule form = formOf(returnId, formId);
    Field target = form.field(field).orElseThrow(FormController::notFound);
    String text = value.strip();
    if (!text.isEmpty()) {
      try {
        target.kind().validate(text);
      } catch (IllegalArgumentException e) {
        return ResponseEntity.status(422).body(e.getMessage());
      }
    }
    forms.saveAnswer(returnId, formId, field, text);
    return ResponseEntity.ok(String.join("\n", form.check(forms.answers(returnId, formId))));
  }

  /**
   * The dialog that checks the values of the fields from one certificate against the values that
   * the app reads from it. The page sends the values in its fields. This saves nothing: the page
   * saves the values after the user confirms them in the dialog.
   */
  @PostMapping("/returns/{returnId}/forms/{formId}/document-check")
  String documentCheck(
      @PathVariable long returnId,
      @PathVariable String formId,
      @RequestParam long document,
      @RequestParam Map<String, String> submitted,
      Model model) {
    FormModule form = formOf(returnId, formId);
    documents.find(returnId, document).orElseThrow(FormController::notFound);
    CertificateValues read = CertificatePdfReader.read(documents.content(document));
    Answers interview = forms.answers(returnId, Interview.ID);
    List<CheckRow> rows = new ArrayList<>();
    for (Field field : form.fields(forms.answers(returnId, formId))) {
      OptionalLong source = Interview.certificateDocument(interview, field.id());
      if (source.isPresent() && source.getAsLong() == document) {
        rows.add(checkRow(field, read, submitted.getOrDefault(field.id(), "").strip()));
      }
    }
    model.addAttribute("rows", rows);
    return "document-check :: dialog";
  }

  /** A value that the app did not read stays empty: the app never suggests a value of its own. */
  private static CheckRow checkRow(Field field, CertificateValues read, String onPage) {
    String certificateId = field.hintId();
    String readValue = read.value(certificateId).orElse("");
    return new CheckRow(
        field.id(),
        field.label(),
        WageTaxCertificate2022.lineOf(certificateId).orElse("ohne Zeile"),
        field.kind().name(),
        readValue,
        onPage.isEmpty() ? readValue : onPage);
  }

  @GetMapping("/returns/{returnId}/review")
  String review(@PathVariable long returnId, Model model) {
    TaxYear taxYear = taxYearOf(returnId);
    YearPack pack = packOf(taxYear);
    List<Expense> stored = expenses.expensesOf(returnId).stream().map(StoredExpense::expense).toList();
    List<FormReview> reviews = new ArrayList<>();
    for (FormModule form : Forms.of(taxYear)) {
      if (!form.id().equals(FilingCheckForm.ID)) {
        Answers answers = forms.answers(returnId, form.id());
        reviews.add(
            new FormReview(
                form.title(), sections(form.review(answers, stored)), form.missing(answers)));
      }
    }
    model.addAttribute("returnId", returnId);
    model.addAttribute("tab", "review");
    model.addAttribute("taxYear", taxYear.value());
    model.addAttribute("forms", Forms.of(taxYear));
    model.addAttribute("filing", filingOf(returnId, pack));
    model.addAttribute("reviews", reviews);
    model.addAttribute(
        "estimate",
        EstimateView.of(
            WorkExpenseEstimate.of(pack, forms.answers(returnId, AnlageN.ID), stored), pack));
    model.addAttribute("withoutReceipt", ExpenseSummary.of(stored).expensesWithoutEvidence());
    model.addAttribute("withoutForm", withoutForm(stored));
    return "review";
  }

  /** A cost that no form shows yet must stay visible, or its deduction is lost in silence. */
  private static List<WithoutFormView> withoutForm(List<Expense> stored) {
    return stored.stream()
        .filter(expense -> !expense.purpose().topic().isWorkExpense())
        .map(
            expense ->
                new WithoutFormView(
                    expense.purpose().topic().label(),
                    expense.purpose().description(),
                    GermanFormats.format(expense.payment().amount()),
                    expense.evidence().label()))
        .toList();
  }

  private void fillForm(
      long returnId,
      FormModule form,
      Map<String, String> values,
      Map<String, String> errors,
      List<String> formErrors,
      Model model) {
    TaxYear taxYear = taxYearOf(returnId);
    model.addAttribute("returnId", returnId);
    model.addAttribute("tab", form.id());
    model.addAttribute("taxYear", taxYear.value());
    model.addAttribute("forms", Forms.of(taxYear));
    model.addAttribute("form", form);
    model.addAttribute("fieldGroups", fieldGroups(returnId, form.fields(new Answers(values))));
    model.addAttribute("values", values);
    model.addAttribute("errors", errors);
    model.addAttribute("formErrors", formErrors);
    if (form.id().equals(FilingCheckForm.ID) && errors.isEmpty()) {
      model.addAttribute("filing", filingOf(returnId, packOf(taxYear)));
    }
  }

  private FilingView filingOf(long returnId, YearPack pack) {
    return FilingView.of(FilingCheck.of(pack, forms.answers(returnId, FilingCheckForm.ID)));
  }

  private static List<SectionView> sections(List<ReviewLine> lines) {
    Map<String, List<ReviewLine>> bySection = new LinkedHashMap<>();
    for (ReviewLine line : lines) {
      bySection.computeIfAbsent(line.section(), section -> new ArrayList<>()).add(line);
    }
    return bySection.entrySet().stream()
        .map(entry -> new SectionView(entry.getKey(), entry.getValue()))
        .toList();
  }

  private List<FieldGroup> fieldGroups(long returnId, List<Field> fields) {
    Answers interview = forms.answers(returnId, Interview.ID);
    Map<String, List<Field>> bySection = new LinkedHashMap<>();
    for (Field field : fields) {
      bySection.computeIfAbsent(field.section(), section -> new ArrayList<>()).add(field);
    }
    return bySection.entrySet().stream()
        .map(
            entry -> {
              OptionalLong document =
                  Interview.certificateDocument(interview, entry.getValue().getFirst().id());
              return document.isPresent()
                  ? new FieldGroup(
                      entry.getKey(),
                      entry.getValue(),
                      document.getAsLong(),
                      "/returns/" + returnId + "/documents/" + document.getAsLong())
                  : new FieldGroup(entry.getKey(), entry.getValue(), null, null);
            })
        .toList();
  }

  private FormModule formOf(long returnId, String formId) {
    return Forms.find(taxYearOf(returnId), formId).orElseThrow(FormController::notFound);
  }

  private YearPack packOf(TaxYear taxYear) {
    return YearPack.of(taxYear).orElseThrow(FormController::notFound);
  }

  private TaxYear taxYearOf(long returnId) {
    return household.taxYearOf(returnId).orElseThrow(FormController::notFound);
  }

  private static ResponseStatusException notFound() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND);
  }
}
