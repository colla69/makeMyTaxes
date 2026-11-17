package io.github.colla69.makemytaxes.core.document;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.colla69.makemytaxes.core.TaxYear;
import io.github.colla69.makemytaxes.core.document.Recognition.OtherYear;
import io.github.colla69.makemytaxes.core.document.Recognition.Payslip;
import io.github.colla69.makemytaxes.core.document.Recognition.Unknown;
import io.github.colla69.makemytaxes.core.document.Recognition.WageTaxCertificate;
import io.github.colla69.makemytaxes.core.form.CertificateText;
import java.time.Month;
import org.junit.jupiter.api.Test;

/** All texts are made up. */
class RecognitionTest {

  private static final TaxYear YEAR_2025 = new TaxYear(2025);

  private static final String CERTIFICATE_LEFT =
      """
      Steuerklasse/Faktor
      1
      Anschrift und Steuernummer des Arbeitgebers:
      Beispiel Werkstatt GmbH
      """;

  private static final String CERTIFICATE_RIGHT =
      """
      1. Bescheinigungszeitraum 01.01.-31.12.
      3. Bruttoarbeitslohn einschl. Sachbezüge ohne 9. 30.000,00
      und 10.
      4. Einbehaltene Lohnsteuer von 3. 4.321,00
      """;

  private static final String DATE_PERIOD_PAYSLIP =
      """
      Entgeltabrechnung für den Zeitraum vom 01.03. bis 31.03.2025
      Steuerbrutto, lfd. 3.000,00 9.000,00
      Lohnsteuer, lfd. 400,00 1.200,00
      Entgeltabrechnung für den Zeitraum vom 01.01. bis 31.01.2025
      Lohnsteuer, lfd. 10,00
      """;

  private static final String MONTH_NAME_PAYSLIP =
      """
      Abrechnung der Brutto/Netto-Bezüge für März 2025
      Steuer-Brutto 3.200,00
      Lohnsteuer 410,25
      """;

  @Test
  void recognizesACertificateOfTheYear() {
    assertThat(certificate(2025)).isEqualTo(new WageTaxCertificate());
  }

  @Test
  void recognizesAPayslipByItsFirstPeriod() {
    assertThat(payslip(DATE_PERIOD_PAYSLIP)).isEqualTo(new Payslip(Month.MARCH));
  }

  @Test
  void recognizesAPayslipByItsMonthName() {
    assertThat(payslip(MONTH_NAME_PAYSLIP)).isEqualTo(new Payslip(Month.MARCH));
  }

  @Test
  void namesTheYearOfAFileOfAnotherYear() {
    assertThat(certificate(2024)).isEqualTo(new OtherYear(2024));
    assertThat(payslip(MONTH_NAME_PAYSLIP.replace("2025", "2024"))).isEqualTo(new OtherYear(2024));
  }

  @Test
  void doesNotRecognizeAFileWithoutAKnownLayout() {
    assertThat(payslip("")).isEqualTo(new Unknown());
    assertThat(payslip("Rechnung vom 03.02.2025\nBetrag 120,00")).isEqualTo(new Unknown());
  }

  @Test
  void doesNotTakeAMonthNameWithoutAnAmountForAPayslip() {
    assertThat(payslip("Ihr Termin im März 2025")).isEqualTo(new Unknown());
  }

  @Test
  void doesNotGuessBetweenTwoNamedMonths() {
    String twoMonths = MONTH_NAME_PAYSLIP + "Nachzahlung für Februar 2025\n";

    assertThat(payslip(twoMonths)).isEqualTo(new Unknown());
  }

  @Test
  void doesNotTakeACertificateTitleWithoutValuesForACertificate() {
    CertificateText letter =
        new CertificateText("Ihre Lohnsteuerbescheinigung für 2025 folgt.", "", "");

    assertThat(Recognition.of(letter, letter.page(), YEAR_2025)).isEqualTo(new Unknown());
  }

  @Test
  void doesNotRecognizeAYearWithoutACertificateLayout() {
    assertThat(certificate(2021)).isEqualTo(new Unknown());
  }

  private static Recognition certificate(int year) {
    String page = "Ausdruck der elektronischen Lohnsteuerbescheinigung für " + year;
    CertificateText text = new CertificateText(page, CERTIFICATE_LEFT, CERTIFICATE_RIGHT);
    return Recognition.of(text, page + "\n" + CERTIFICATE_LEFT + CERTIFICATE_RIGHT, YEAR_2025);
  }

  private static Recognition payslip(String text) {
    return Recognition.of(new CertificateText(text, text, ""), text, YEAR_2025);
  }
}
