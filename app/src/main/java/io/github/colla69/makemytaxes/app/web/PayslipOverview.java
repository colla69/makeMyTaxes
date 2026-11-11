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
import io.github.colla69.makemytaxes.core.document.PayslipValues;
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

  /** One month of the year. A month without a payslip has no payslip. */
  record MonthRow(String month, ReadPayslip payslip) {

    public String taxableGross() {
      return text(payslip.values().taxableGross());
    }

    public String wageTax() {
      return text(payslip.values().wageTax());
    }
  }

  record CheckRow(String amount, String sum, String certificate, String result) {}

  record ReadPayslip(StoredPayslip stored, PayslipValues values) {}

  private final DocumentStore documents;

  PayslipOverview(DocumentStore documents) {
    this.documents = documents;
  }

  void fill(long returnId, TaxYear taxYear, Model model) {
    List<ReadPayslip> payslips =
        documents.payslipsOf(returnId).stream()
            .map(stored -> new ReadPayslip(stored, valuesOf(stored, taxYear)))
            .toList();
    model.addAttribute("months", monthOptions());
    model.addAttribute("payslipMonths", monthRows(payslips));
    if (payslips.isEmpty()) {
      return;
    }
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
                payslips.stream().map(p -> p.values().taxableGross()).toList(),
                amountOf(certificate, "grossWage")),
            checkRow(
                "Lohnsteuer" + lineOf(layout, "wageTax"),
                payslips.stream().map(p -> p.values().wageTax()).toList(),
                amountOf(certificate, "wageTax"))));
  }

  private PayslipValues valuesOf(StoredPayslip stored, TaxYear taxYear) {
    YearMonth month = YearMonth.of(taxYear.value(), stored.month());
    return PayslipPdfReader.read(documents.content(stored.documentId()), month);
  }

  private static List<MonthRow> monthRows(List<ReadPayslip> payslips) {
    Map<Month, ReadPayslip> byMonth =
        payslips.stream()
            .collect(Collectors.toMap(p -> p.stored().month(), Function.identity()));
    List<MonthRow> rows = new ArrayList<>();
    for (Month month : Month.values()) {
      rows.add(new MonthRow(DocumentStore.monthName(month), byMonth.get(month)));
    }
    return rows;
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
