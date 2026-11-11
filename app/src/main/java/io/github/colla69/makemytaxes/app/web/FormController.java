package io.github.colla69.makemytaxes.app.web;

import io.github.colla69.makemytaxes.app.document.CertificatePdfReader;
import io.github.colla69.makemytaxes.app.storage.DocumentStore;
import io.github.colla69.makemytaxes.app.storage.DocumentStore.StoredDocument;
import io.github.colla69.makemytaxes.app.storage.ExpenseStore;
import io.github.colla69.makemytaxes.app.storage.ExpenseStore.StoredExpense;
import io.github.colla69.makemytaxes.app.storage.FormStore;
import io.github.colla69.makemytaxes.app.storage.HouseholdStore;
import io.github.colla69.makemytaxes.core.GermanFormats;
import io.github.colla69.makemytaxes.core.TaxYear;
import io.github.colla69.makemytaxes.core.document.DocumentRef;
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
import io.github.colla69.makemytaxes.core.form.HomeOfficeDay;
import io.github.colla69.makemytaxes.core.form.Interview;
import io.github.colla69.makemytaxes.core.form.ReviewLine;
import io.github.colla69.makemytaxes.core.form.Source;
import io.github.colla69.makemytaxes.core.form.WageTaxCertificate;
import io.github.colla69.makemytaxes.core.form.WorkExpenseEstimate;
import io.github.colla69.makemytaxes.core.form.YearDefinition;
import io.github.colla69.makemytaxes.core.year.YearPack;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

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
      String homeOfficeLimit, Sources sources) {

    /**
     * The legal sources of the rates. Until 2025, § 9(1) sentence 3 no. 4 sentence 8 EStG sets the
     * higher rate from the 21st km. From 2026, sentence 2 sets one rate from the first km. From
     * 2023, § 52(6) sentence 13 EStG applies the daily home office amount of § 4(5) no. 6c, with
     * other days ({@link HomeOfficeDay}). From 2026, § 9a sentence 3 EStG counts union fees in
     * addition to the employee lump sum.
     */
    record Sources(
        Commute commute, String homeOffice, String homeOfficeDays, boolean unionFeesBesideLumpSum) {

      /** The source of the commute rates, and whether one rate applies from the first km. */
      record Commute(String source, boolean oneRate) {}

      static Sources of(int year) {
        Commute commute =
            year >= 2026
                ? new Commute("§ 9 Abs. 1 Satz 3 Nr. 4 Satz 2 EStG", true)
                : new Commute("§ 9 Abs. 1 Satz 3 Nr. 4 Satz 2 und 8 EStG", false);
        String homeOffice =
            year >= 2023
                ? "§ 4 Abs. 5 Satz 1 Nr. 6c Satz 1 und 2 EStG"
                : "§ 4 Abs. 5 Satz 1 Nr. 6b Satz 4 EStG";
        return new Sources(commute, homeOffice, homeOfficeDays(year), year >= 2026);
      }

      /**
       * The forms of 2023 to 2025 put the days of no. 6c sentence 1 on one line of Anlage N and the
       * days of sentence 2 on the next line, which the app does not cover: lines 61 and 62 in 2023
       * and 2024, lines 58 and 59 in 2025. Other years have no such lines here.
       */
      private static String homeOfficeDays(int year) {
        return switch (year) {
          case 2023, 2024 -> homeOfficeDaysOnLines("61", "62");
          case 2025 -> homeOfficeDaysOnLines("58", "59");
          default -> HomeOfficeDay.reviewText(year);
        };
      }

      private static String homeOfficeDaysOnLines(String line, String lineWithoutWorkplace) {
        return "Tag überwiegend zu Hause ohne Besuch der ersten Tätigkeitsstätte (Anlage N Zeile "
            + line
            + "; Tage ohne dauerhaft anderen Arbeitsplatz gehören in Zeile "
            + lineWithoutWorkplace
            + ", die die App nicht abdeckt)";
      }
    }

    static LimitsView of(YearPack pack) {
      return new LimitsView(
          GermanFormats.format(pack.commutePerKmFirst20()),
          GermanFormats.format(pack.commutePerKmFrom21()),
          GermanFormats.format(pack.commuteLimitWithoutCar()),
          GermanFormats.format(pack.homeOfficePerDay()),
          GermanFormats.format(pack.homeOfficeLimit()),
          Sources.of(pack.taxYear().value()));
    }
  }

  /** The source of a line as the page shows it. {@code href} is null for a source with no file. */
  record SourceView(String label, String href) {}

  record WithoutFormView(String topic, String description, String amount, SourceView source) {}

  record LineView(String line, String label, String value, SourceView source) {}

  record SectionView(String title, List<LineView> lines) {}

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

  /** The folder of the documents in the exported ZIP file. */
  private static final String EXPORT_FOLDER = "belege/";

  private final HouseholdStore household;
  private final ExpenseStore expenses;
  private final FormStore forms;
  private final DocumentStore documents;
  private final ITemplateEngine templates;

  FormController(
      HouseholdStore household,
      ExpenseStore expenses,
      FormStore forms,
      DocumentStore documents,
      ITemplateEngine templates) {
    this.household = household;
    this.expenses = expenses;
    this.forms = forms;
    this.documents = documents;
    this.templates = templates;
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
    WageTaxCertificate certificate = definitionOf(returnId).certificate();
    CertificateValues read = CertificatePdfReader.read(documents.content(document), certificate);
    Answers interview = forms.answers(returnId, Interview.ID);
    List<CheckRow> rows = new ArrayList<>();
    for (Field field : form.fields(forms.answers(returnId, formId))) {
      OptionalLong source = Interview.certificateDocument(interview, field.id());
      if (source.isPresent() && source.getAsLong() == document) {
        // A value that the app did not read stays empty: the app never suggests a value of its own.
        String certificateId = field.hintId();
        String readValue = read.value(certificateId).orElse("");
        String onPage = submitted.getOrDefault(field.id(), "").strip();
        rows.add(
            new CheckRow(
                field.id(),
                field.label(),
                certificate.lineOf(certificateId).orElse("ohne Zeile"),
                field.kind().name(),
                readValue,
                onPage.isEmpty() ? readValue : onPage));
      }
    }
    model.addAttribute("rows", rows);
    return "document-check :: dialog";
  }

  @GetMapping("/returns/{returnId}/review")
  String review(@PathVariable long returnId, Model model) {
    fillReview(
        returnId,
        document -> "/returns/" + returnId + "/documents/" + document.id(),
        model.asMap());
    model.addAttribute("export", false);
    return "review";
  }

  /**
   * One ZIP file with the review as a web page and each document that a line of the review links
   * to. The links of the page point to the files in the ZIP file, so the record works offline.
   */
  @GetMapping("/returns/{returnId}/review/export")
  ResponseEntity<byte[]> export(@PathVariable long returnId) throws IOException {
    Map<String, Object> attributes = new HashMap<>();
    Set<DocumentRef> linked =
        fillReview(returnId, document -> EXPORT_FOLDER + entryName(document), attributes);
    attributes.put("export", true);
    attributes.put("exportedOn", GermanFormats.format(LocalDate.now()));
    String page = templates.process("review", new Context(Locale.GERMANY, attributes));
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
      zip.putNextEntry(new ZipEntry("uebersicht.html"));
      zip.write(page.getBytes(StandardCharsets.UTF_8));
      for (DocumentRef document : linked) {
        zip.putNextEntry(new ZipEntry(EXPORT_FOLDER + entryName(document)));
        zip.write(documents.content(document.id()));
      }
    }
    String fileName = "nachweise-" + attributes.get("taxYear") + ".zip";
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("application/zip"))
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(fileName).build().toString())
        .body(bytes.toByteArray());
  }

  /** The ID keeps two documents with the same name apart. The name keeps only safe characters. */
  static String entryName(DocumentRef document) {
    return document.id() + "-" + document.fileName().replaceAll("[^A-Za-z0-9._-]", "_");
  }

  /**
   * Fills the attributes of the review page. {@code link} gives the address of a document.
   * Returns the documents that the page links to, in the order of the page.
   */
  private Set<DocumentRef> fillReview(
      long returnId, Function<DocumentRef, String> link, Map<String, Object> attributes) {
    TaxYear taxYear = taxYearOf(returnId);
    YearPack pack = packOf(taxYear);
    List<Expense> stored = expenses.expensesOf(returnId).stream().map(StoredExpense::expense).toList();
    Map<Long, DocumentRef> byId = new HashMap<>();
    for (StoredDocument document : documents.documentsOf(returnId)) {
      byId.put(document.id(), new DocumentRef(document.id(), document.fileName()));
    }
    Set<DocumentRef> linked = new LinkedHashSet<>();
    Function<Source, SourceView> view =
        source -> {
          if (source.document() == null) {
            return new SourceView(source.label(), null);
          }
          linked.add(source.document());
          return new SourceView(source.label(), link.apply(source.document()));
        };
    List<FormReview> reviews = new ArrayList<>();
    for (FormModule form : Forms.of(taxYear)) {
      if (!form.id().equals(FilingCheckForm.ID)) {
        Answers answers = forms.answers(returnId, form.id());
        Map<String, Long> answerDocuments = forms.answerDocuments(returnId, form.id());
        Map<String, List<LineView>> bySection = new LinkedHashMap<>();
        for (ReviewLine line : form.review(answers, stored)) {
          bySection
              .computeIfAbsent(line.section(), section -> new ArrayList<>())
              .add(
                  new LineView(
                      line.line(),
                      line.label(),
                      line.value(),
                      view.apply(resolve(line.source(), answerDocuments, byId))));
        }
        List<SectionView> sections =
            bySection.entrySet().stream()
                .map(entry -> new SectionView(entry.getKey(), entry.getValue()))
                .toList();
        reviews.add(new FormReview(form.title(), sections, form.missing(answers)));
      }
    }
    attributes.put("returnId", returnId);
    attributes.put("tab", "review");
    attributes.put("taxYear", taxYear.value());
    attributes.put("forms", Forms.of(taxYear));
    attributes.put("linesKnown", definitionOf(returnId).linesKnown());
    attributes.put("filing", filingOf(returnId, pack));
    attributes.put("reviews", reviews);
    attributes.put(
        "estimate",
        EstimateView.of(
            WorkExpenseEstimate.of(pack, forms.answers(returnId, AnlageN.ID), stored), pack));
    attributes.put("withoutReceipt", ExpenseSummary.of(stored).expensesWithoutEvidence());
    attributes.put("withoutForm", withoutForm(stored, view));
    return linked;
  }

  /**
   * The source of a saved answer is its document, if the storage has one. Otherwise the user
   * stated the value, as for a personal detail of the main form.
   */
  private static Source resolve(
      Source source, Map<String, Long> answerDocuments, Map<Long, DocumentRef> byId) {
    if (!(source instanceof Source.Answer answer)) {
      return source;
    }
    DocumentRef document = byId.get(answerDocuments.get(answer.fieldId()));
    return document == null ? new Source.OwnStatement() : new Source.FromDocument(document);
  }

  /** A cost that no form shows yet must stay visible, or its deduction is lost in silence. */
  private static List<WithoutFormView> withoutForm(
      List<Expense> stored, Function<Source, SourceView> view) {
    return stored.stream()
        .filter(expense -> !expense.purpose().topic().isWorkExpense())
        .map(
            expense ->
                new WithoutFormView(
                    expense.purpose().topic().label(),
                    expense.purpose().description(),
                    GermanFormats.format(expense.payment().amount()),
                    view.apply(Source.of(expense.evidence()))))
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
    model.addAttribute("linesKnown", definitionOf(returnId).linesKnown());
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

  private YearDefinition definitionOf(long returnId) {
    return YearDefinition.of(taxYearOf(returnId)).orElseThrow(FormController::notFound);
  }

  private TaxYear taxYearOf(long returnId) {
    return household.taxYearOf(returnId).orElseThrow(FormController::notFound);
  }

  private static ResponseStatusException notFound() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND);
  }
}
