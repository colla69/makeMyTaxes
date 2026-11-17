package io.github.colla69.makemytaxes.app.web;

import io.github.colla69.makemytaxes.app.storage.DocumentStore;
import io.github.colla69.makemytaxes.app.storage.DocumentStore.StoredDocument;
import io.github.colla69.makemytaxes.app.storage.ExpenseStore;
import io.github.colla69.makemytaxes.app.storage.ExpenseStore.StoredExpense;
import io.github.colla69.makemytaxes.app.storage.FormStore;
import io.github.colla69.makemytaxes.core.GermanFormats;
import io.github.colla69.makemytaxes.core.TaxYear;
import io.github.colla69.makemytaxes.core.document.DocumentRef;
import io.github.colla69.makemytaxes.core.expense.Expense;
import io.github.colla69.makemytaxes.core.expense.ExpenseSummary;
import io.github.colla69.makemytaxes.core.expense.ExpenseTopic;
import io.github.colla69.makemytaxes.core.filing.FilingCheck;
import io.github.colla69.makemytaxes.core.form.AnlageN;
import io.github.colla69.makemytaxes.core.form.Answers;
import io.github.colla69.makemytaxes.core.form.ExtraordinaryBurdensForm;
import io.github.colla69.makemytaxes.core.form.Field;
import io.github.colla69.makemytaxes.core.form.FilingCheckForm;
import io.github.colla69.makemytaxes.core.form.FormModule;
import io.github.colla69.makemytaxes.core.form.Forms;
import io.github.colla69.makemytaxes.core.form.HomeOfficeDay;
import io.github.colla69.makemytaxes.core.form.MedicalCostEstimate;
import io.github.colla69.makemytaxes.core.form.ReviewLine;
import io.github.colla69.makemytaxes.core.form.Source;
import io.github.colla69.makemytaxes.core.form.WorkExpenseEstimate;
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
import java.util.Set;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;
@Controller
class ReviewController {

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

  /** The estimate of the deductible medical costs, as text for the review. */
  record MedicalView(
      boolean hasCosts, String costs, String refunds, String burden, String totalIncome,
      String group, String percents, String reasonableBurden, String deductible,
      boolean wageKnown) {

    static MedicalView of(MedicalCostEstimate estimate) {
      return new MedicalView(
          estimate.hasCosts(),
          GermanFormats.format(estimate.costs()),
          GermanFormats.format(estimate.refunds()),
          GermanFormats.format(estimate.burden()),
          GermanFormats.format(estimate.totalIncome()),
          estimate.group().label(),
          estimate.group().percents(),
          GermanFormats.format(estimate.reasonableBurden()),
          GermanFormats.format(estimate.deductible()),
          estimate.wageKnown());
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

  /** The folder of the documents in the exported ZIP file. */
  private static final String EXPORT_FOLDER = "belege/";

  private final ReturnYears years;
  private final ExpenseStore expenses;
  private final FormStore forms;
  private final DocumentStore documents;
  private final ITemplateEngine templates;

  ReviewController(
      ReturnYears years,
      ExpenseStore expenses,
      FormStore forms,
      DocumentStore documents,
      ITemplateEngine templates) {
    this.years = years;
    this.expenses = expenses;
    this.forms = forms;
    this.documents = documents;
    this.templates = templates;
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
    TaxYear taxYear = years.taxYearOf(returnId);
    YearPack pack = ReturnYears.packOf(taxYear);
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
        AnswerEvidence evidence =
            new AnswerEvidence(
                forms.answerDocuments(returnId, form.id()),
                forms.answersWithoutEvidence(returnId, form.id()));
        Map<String, List<LineView>> bySection = new LinkedHashMap<>();
        for (ReviewLine line : form.review(answers, stored)) {
          bySection
              .computeIfAbsent(line.section(), section -> new ArrayList<>())
              .add(
                  new LineView(
                      line.line(),
                      line.label(),
                      line.value(),
                      view.apply(resolve(line.source(), evidence, byId))));
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
    attributes.put("linesKnown", years.definitionOf(returnId).linesKnown());
    attributes.put(
        "filing",
        FilingView.of(FilingCheck.of(pack, forms.answers(returnId, FilingCheckForm.ID))));
    attributes.put("reviews", reviews);
    attributes.put(
        "estimate",
        EstimateView.of(
            WorkExpenseEstimate.of(pack, forms.answers(returnId, AnlageN.ID), stored), pack));
    attributes.put(
        "medical",
        MedicalView.of(
            MedicalCostEstimate.of(
                pack,
                forms.answers(returnId, AnlageN.ID),
                forms.answers(returnId, ExtraordinaryBurdensForm.ID),
                stored)));
    attributes.put("withoutReceipt", ExpenseSummary.of(stored).expensesWithoutEvidence());
    attributes.put("withoutForm", withoutForm(stored, view));
    return linked;
  }

  /**
   * The evidence of the saved answers of one form, by field.
   *
   * @param documents the document of each answer that comes from a document
   * @param missing the answers that lost their document
   */
  private record AnswerEvidence(Map<String, Long> documents, Set<String> missing) {}

  /**
   * The source of a saved answer is its document, if the storage has one. An answer that lost its
   * document shows that its evidence is missing. Otherwise the user stated the value, as for a
   * personal detail of the main form.
   */
  private static Source resolve(
      Source source, AnswerEvidence evidence, Map<Long, DocumentRef> byId) {
    if (!(source instanceof Source.Answer answer)) {
      return source;
    }
    if (evidence.missing().contains(answer.fieldId())) {
      return new Source.Missing(null);
    }
    DocumentRef document = byId.get(evidence.documents().get(answer.fieldId()));
    return document == null ? new Source.OwnStatement() : new Source.FromDocument(document);
  }

  /** A cost that no form shows yet must stay visible, or its deduction is lost in silence. */
  private static List<WithoutFormView> withoutForm(
      List<Expense> stored, Function<Source, SourceView> view) {
    return stored.stream()
        .filter(expense -> !expense.purpose().topic().isWorkExpense())
        .filter(expense -> expense.purpose().topic() != ExpenseTopic.MEDICAL)
        .map(
            expense ->
                new WithoutFormView(
                    expense.purpose().topic().label(),
                    expense.purpose().description(),
                    GermanFormats.format(expense.payment().amount()),
                    view.apply(Source.of(expense.evidence()))))
        .toList();
  }
}
