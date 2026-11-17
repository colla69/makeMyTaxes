package io.github.colla69.makemytaxes.core.document;

import io.github.colla69.makemytaxes.core.TaxYear;
import io.github.colla69.makemytaxes.core.form.CertificateText;
import io.github.colla69.makemytaxes.core.form.WageTaxCertificateReader2022;
import io.github.colla69.makemytaxes.core.form.YearDefinition;
import java.time.Month;
import java.time.YearMonth;
import java.util.Optional;

/**
 * What an uploaded file is, as far as the app can tell from its text. The app never guesses: a
 * file is a certificate or a payslip only if the reader of that kind finds a value in it.
 */
public sealed interface Recognition {

  /** A Lohnsteuerbescheinigung of the year of the return. */
  record WageTaxCertificate() implements Recognition {}

  /** A payslip of a month of the year of the return. */
  record Payslip(Month month) implements Recognition {}

  /** A certificate or a payslip of another year than the year of the return. */
  record OtherYear(int year) implements Recognition {}

  /** The app cannot tell what the file is, such as a scan, an image or another PDF. */
  record Unknown() implements Recognition {}

  /**
   * Recognizes a file by its text.
   *
   * @param firstPage the text of the first page, in the columns of a certificate
   * @param text the text of all pages
   * @param taxYear the year of the return
   */
  static Recognition of(CertificateText firstPage, String text, TaxYear taxYear) {
    Optional<Integer> certificateYear = certificateYear(firstPage);
    Optional<YearMonth> payslipMonth = payslipMonth(text);
    if (certificateYear.isPresent() == payslipMonth.isPresent()) {
      return new Unknown();
    }
    if (certificateYear.isPresent()) {
      int year = certificateYear.get();
      return year == taxYear.value() ? new WageTaxCertificate() : new OtherYear(year);
    }
    YearMonth month = payslipMonth.get();
    return month.getYear() == taxYear.value()
        ? new Payslip(month.getMonth())
        : new OtherYear(month.getYear());
  }

  /** The year in the title, if the layout of that year reads a value from the certificate. */
  private static Optional<Integer> certificateYear(CertificateText firstPage) {
    return WageTaxCertificateReader2022.yearOf(firstPage.page())
        .filter(year -> year >= 1000)
        .filter(
            year ->
                YearDefinition.of(new TaxYear(year))
                    .map(definition -> definition.certificate().read(firstPage))
                    .filter(values -> !values.found().isEmpty())
                    .isPresent());
  }

  /** The month that the payslip names, if the reader finds an amount of that month. */
  private static Optional<YearMonth> payslipMonth(String text) {
    return PayslipReader.monthOf(text)
        .filter(
            month -> {
              PayslipValues values = PayslipReader.read(text, month);
              return values.taxableGross().isPresent() || values.wageTax().isPresent();
            });
  }
}
