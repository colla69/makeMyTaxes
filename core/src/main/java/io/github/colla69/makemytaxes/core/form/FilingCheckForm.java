package io.github.colla69.makemytaxes.core.form;

import io.github.colla69.makemytaxes.core.GermanFormats;
import io.github.colla69.makemytaxes.core.expense.Expense;
import io.github.colla69.makemytaxes.core.year.YearPack;
import java.util.List;

/** The questions of the filing check. The answers go on no form, so review has no lines. */
public final class FilingCheckForm implements FormModule {

  public static final String ID = "filing-check";

  private static final String SECTION = "Müssen Sie eine Erklärung abgeben?";

  private static final int FIRST_YEAR_WITH_REFUND_RULE = 2026;

  private static final int FIRST_YEAR_WITHOUT_REDUCED_PAYROLL_RATE = 2025;

  private final YearPack pack;

  public FilingCheckForm(YearPack pack) {
    this.pack = pack;
  }

  @Override
  public String id() {
    return ID;
  }

  @Override
  public String title() {
    return "Abgabepflicht";
  }

  @Override
  public List<Field> fields() {
    String income = GermanFormats.format(pack.benefitAndOtherIncomeLimit());
    String wage = GermanFormats.format(pack.wageLimit());
    return List.of(
        question(
            "otherIncome",
            "§ 46 Abs. 2 Nr. 1 EStG",
            "Einkünfte ohne Lohnsteuerabzug, etwa aus Vermietung oder einem Nebenjob, über "
                + income
                + " Euro?"),
        question(
            "benefits",
            "§ 46 Abs. 2 Nr. 1 EStG",
            "Lohnersatzleistungen wie Arbeitslosengeld, Elterngeld oder Krankengeld über "
                + income
                + " Euro?"),
        question(
            "severalEmployers",
            "§ 46 Abs. 2 Nr. 2 EStG",
            "Arbeitslohn von zwei oder mehr Arbeitgebern gleichzeitig?"),
        question("healthAllowance", "§ 46 Abs. 2 Nr. 3 EStG", healthQuestion(income, wage)),
        question(
            "wageTaxAllowance",
            "§ 46 Abs. 2 Nr. 4 EStG",
            "Arbeitslohn über "
                + wage
                + " Euro, und ein Freibetrag nach § 39a Abs. 1 Satz 1 Nr. 1 bis 3, 5 oder 6 EStG in"
                + " Ihren Lohnsteuerabzugsmerkmalen?"),
        question(
            "specialPayment",
            "§ 46 Abs. 2 Nr. 5 EStG",
            specialPaymentQuestion()),
        question(
            "certificateS",
            "§ 46 Abs. 2 Nr. 5a EStG",
            "Steht der Großbuchstabe S auf Ihrer Lohnsteuerbescheinigung?"),
        question(
            "officeRequest",
            "§ 149 Abs. 1 AO",
            "Hat das Finanzamt Sie aufgefordert, für dieses Jahr eine Erklärung abzugeben?"));
  }

  /**
   * § 46(2) no. 3 EStG. From 2026, a refund of health and care insurance contributions over 410
   * euros decides, and no longer the Vorsorgepauschale: § 52(36) sentence 3 EStG. Both versions
   * name the same 410 euros as no. 1.
   */
  private String healthQuestion(String income, String wage) {
    if (pack.taxYear().value() >= FIRST_YEAR_WITH_REFUND_RULE) {
      return "Arbeitslohn über "
          + wage
          + " Euro, und Ihre Beiträge zur Kranken- und Pflegeversicherung (§ 10 Abs. 1 Nr. 3"
          + " EStG) wurden mit mehr als "
          + income
          + " Euro erstattet?";
    }
    return "Arbeitslohn über "
        + wage
        + " Euro, und die Teilbeträge der Vorsorgepauschale für die Kranken- und"
        + " Pflegeversicherung (§ 39b Abs. 2 Satz 5 Nr. 3 Buchst. b bis d EStG) beim"
        + " Lohnsteuerabzug waren höher als Ihre abziehbaren Vorsorgeaufwendungen nach"
        + " § 10 Abs. 1 Nr. 3 und 3a in Verbindung mit Abs. 4 EStG?";
  }

  /**
   * § 46(2) no. 5 EStG. The versions of 30.06.2022 and 31.12.2022, up to 31.12.2024, name two
   * cases: a one-off payment of § 34 taxed by § 39b(3) sentence 9, and a one-off payment taxed by
   * § 39c(3). From the version of 30.06.2025, it names only § 39c(3), and § 52 has no own rule for
   * it, so § 52(1) applies it from 2025.
   */
  private String specialPaymentQuestion() {
    if (pack.taxYear().value() >= FIRST_YEAR_WITHOUT_REDUCED_PAYROLL_RATE) {
      return "Hat ein Dritter, der Ihnen Arbeitslohn zahlt (§ 38 Abs. 3a EStG), die Lohnsteuer"
          + " für eine Einmalzahlung mit 20 Prozent ermittelt (§ 39c Abs. 3 EStG)?";
    }
    return "Hat Ihr Arbeitgeber eine Einmalzahlung, etwa eine Abfindung, ermäßigt besteuert (§ 39b"
        + " Abs. 3 Satz 9 EStG), oder hat ein Dritter, der Ihnen Arbeitslohn zahlt (§ 38 Abs. 3a"
        + " EStG), die Lohnsteuer für eine Einmalzahlung mit 20 Prozent ermittelt (§ 39c Abs. 3"
        + " EStG)?";
  }

  private static Field question(String id, String source, String label) {
    return Field.required(id, SECTION, source, label, Kind.YES_NO_UNSURE);
  }

  @Override
  public List<ReviewLine> review(Answers answers, List<Expense> expenses) {
    return List.of();
  }
}
