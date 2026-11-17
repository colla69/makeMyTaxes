package io.github.colla69.makemytaxes.app.web;

import io.github.colla69.makemytaxes.app.document.CertificatePdfReader;
import io.github.colla69.makemytaxes.app.document.PayslipPdfReader;
import io.github.colla69.makemytaxes.app.storage.DocumentStore;
import io.github.colla69.makemytaxes.app.storage.DocumentStore.StoredDocument;
import io.github.colla69.makemytaxes.app.storage.DocumentStore.StoredPayslip;
import io.github.colla69.makemytaxes.core.GermanFormats;
import io.github.colla69.makemytaxes.core.TaxYear;
import io.github.colla69.makemytaxes.core.document.DocumentKind;
import io.github.colla69.makemytaxes.core.document.PayslipCheck;
import io.github.colla69.makemytaxes.core.document.PayslipContent;
import io.github.colla69.makemytaxes.core.document.PayslipValues;
import io.github.colla69.makemytaxes.core.document.PayslipYear;
import io.github.colla69.makemytaxes.core.document.PayslipYear.MonthValue;
import io.github.colla69.makemytaxes.core.expense.Money;
import io.github.colla69.makemytaxes.core.form.CertificateValues;
import io.github.colla69.makemytaxes.core.form.Kind;
import io.github.colla69.makemytaxes.core.form.WageTaxCertificate;
import io.github.colla69.makemytaxes.core.form.YearDefinition;
import java.time.Month;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

/**
 * The payslips of a return: which months are present, the amounts that the reader found, and
 * their sums against the Lohnsteuerbescheinigung. The overview only shows. It changes no value of
 * the return.
 */
@Component
class PayslipOverview {

  static final String NOT_READ = "nicht gelesen";

  /**
   * One month of the year. A month without a payslip has no payslip, and a month with neither a
   * payslip nor a correction has no value.
   */
  record MonthRow(String month, StoredPayslip payslip, MonthValue value) {

    public String taxableGross() {
      return text(value.taxableGross());
    }

    public String wageTax() {
      return text(value.wageTax());
    }

    /** Where the value comes from. Empty if the month has no correction. */
    public List<String> sources() {
      if (value == null || value.corrections().isEmpty()) {
        return List.of();
      }
      List<String> sources = new ArrayList<>();
      value.own().ifPresent(own -> sources.add("Abrechnung des Monats: " + amounts(own)));
      value
          .corrections()
          .forEach(
              correction ->
                  sources.add(
                      "Korrektur in "
                          + correction.fileName()
                          + ": "
                          + amounts(correction.values())
                          + repeats(correction.repeatedIn())));
      return sources;
    }

    private static String repeats(List<String> files) {
      return files.isEmpty()
          ? ""
          : " (auch in " + String.join(", ", files) + ", einmal gezählt)";
    }

    private static String amounts(PayslipValues values) {
      return "Steuer-Brutto "
          + text(values.taxableGross())
          + ", Lohnsteuer "
          + text(values.wageTax());
    }
  }

  record CheckRow(String amount, String sum, String compared, String result) {}

  private final DocumentStore documents;

  PayslipOverview(DocumentStore documents) {
    this.documents = documents;
  }

  void fill(long returnId, TaxYear taxYear, Model model) {
    List<StoredPayslip> stored = documents.payslipsOf(returnId);
    PayslipYear year =
        PayslipYear.of(stored.stream().map(payslip -> read(payslip, taxYear)).toList());
    model.addAttribute("months", monthOptions());
    model.addAttribute("payslipMonths", monthRows(stored, year));
    if (stored.isEmpty()) {
      return;
    }
    PayslipYear.Payslip last = year.last().orElseThrow();
    model.addAttribute(
        "payslipYearSource",
        "Die Jahressumme steht in der Lohnabrechnung für "
            + DocumentStore.monthName(last.month().getMonth())
            + " ("
            + last.fileName()
            + ").");
    model.addAttribute(
        "payslipYearChecks",
        List.of(
            yearCheckRow("Steuer-Brutto", year, last, PayslipValues::taxableGross),
            yearCheckRow("Lohnsteuer", year, last, PayslipValues::wageTax)));
    Optional<WageTaxCertificate> layout =
        YearDefinition.of(taxYear).map(YearDefinition::certificate);
    List<StoredDocument> certificates =
        documents.documentsOf(returnId).stream()
            .filter(document -> document.kind() == DocumentKind.WAGE_TAX_CERTIFICATE)
            .toList();
    CertificateValues certificate = new CertificateValues(Map.of());
    if (layout.isPresent() && certificates.size() == 1) {
      certificate =
          CertificatePdfReader.read(documents.content(certificates.getFirst().id()), layout.get());
    } else {
      model.addAttribute("certificateNote", certificateNote(layout, certificates.size()));
    }
    model.addAttribute(
        "payslipChecks",
        List.of(
            checkRow(
                "Bruttoarbeitslohn" + lineOf(layout, "grossWage"),
                year.monthTotals(PayslipValues::taxableGross),
                amountOf(certificate, "grossWage")),
            checkRow(
                "Lohnsteuer" + lineOf(layout, "wageTax"),
                year.monthTotals(PayslipValues::wageTax),
                amountOf(certificate, "wageTax"))));
  }

  private PayslipYear.Payslip read(StoredPayslip stored, TaxYear taxYear) {
    YearMonth month = YearMonth.of(taxYear.value(), stored.month());
    PayslipContent content = PayslipPdfReader.read(documents.content(stored.documentId()), month);
    return new PayslipYear.Payslip(stored.fileName(), month, content);
  }

  private static List<MonthRow> monthRows(List<StoredPayslip> stored, PayslipYear year) {
    Map<Month, StoredPayslip> byMonth =
        stored.stream().collect(Collectors.toMap(StoredPayslip::month, Function.identity()));
    List<MonthRow> rows = new ArrayList<>();
    for (Month month : Month.values()) {
      rows.add(
          new MonthRow(
              DocumentStore.monthName(month), byMonth.get(month), year.month(month).orElse(null)));
    }
    return rows;
  }

  private static CheckRow yearCheckRow(
      String amount,
      PayslipYear year,
      PayslipYear.Payslip last,
      Function<PayslipValues, Optional<Money>> value) {
    PayslipCheck check =
        PayslipCheck.of(year.monthTotals(value), value.apply(last.content().yearToDate()));
    return new CheckRow(
        amount, text(check.sum()), text(check.certificate()), check.result("die Jahressumme"));
  }

  private static CheckRow checkRow(
      String amount, List<Optional<Money>> payslips, Optional<Money> certificate) {
    PayslipCheck check = PayslipCheck.of(payslips, certificate);
    return new CheckRow(amount, text(check.sum()), text(check.certificate()), check.result());
  }

  private static String certificateNote(Optional<WageTaxCertificate> layout, int certificates) {
    if (layout.isEmpty()) {
      return "Für dieses Jahr kann die App die Lohnsteuerbescheinigung nicht lesen.";
    }
    if (certificates == 0) {
      return "Es gibt keine Lohnsteuerbescheinigung. Laden Sie sie hoch, um die Summen zu vergleichen.";
    }
    return "Es gibt mehr als eine Lohnsteuerbescheinigung. Der Vergleich geht nur mit einer.";
  }

  private static String lineOf(Optional<WageTaxCertificate> layout, String fieldId) {
    return layout.flatMap(l -> l.lineOf(fieldId)).map(line -> " (" + line + ")").orElse("");
  }

  private static Optional<Money> amountOf(CertificateValues certificate, String fieldId) {
    return certificate.value(fieldId).map(GermanFormats::parseMoney);
  }

  private static String text(Optional<Money> amount) {
    return amount.map(GermanFormats::format).orElse(NOT_READ);
  }

  private static List<Kind.Option> monthOptions() {
    return Arrays.stream(Month.values())
        .map(
            month ->
                new Kind.Option(String.valueOf(month.getValue()), DocumentStore.monthName(month)))
        .toList();
  }
}
