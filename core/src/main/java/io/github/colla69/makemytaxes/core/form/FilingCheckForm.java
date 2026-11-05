package io.github.colla69.makemytaxes.core.form;

import io.github.colla69.makemytaxes.core.GermanFormats;
import io.github.colla69.makemytaxes.core.expense.Expense;
import io.github.colla69.makemytaxes.core.year.YearPack;
import java.util.List;

/** The questions of the filing check. The answers go on no form, so review has no lines. */
public final class FilingCheckForm implements FormModule {

  public static final String ID = "filing-check";

  private static final String SECTION = "Must you file?";

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
    return "Filing check";
  }

  @Override
  public List<Field> fields() {
    String income = GermanFormats.format(pack.benefitAndOtherIncomeLimit());
    String wage = GermanFormats.format(pack.wageLimit());
    return List.of(
        question(
            "otherIncome",
            "§ 46(2) no. 1 EStG",
            "Income without wage tax, such as rent or a side job, above " + income + " euros?"),
        question(
            "benefits",
            "§ 46(2) no. 1 EStG",
            "Benefits such as Arbeitslosengeld, Elterngeld or Krankengeld above "
                + income
                + " euros?"),
        question(
            "severalEmployers", "§ 46(2) no. 2 EStG", "Two or more employers at the same time?"),
        question(
            "healthAllowance",
            "§ 46(2) no. 3 EStG",
            "Wage above "
                + wage
                + " euros, and the Vorsorgepauschale in the wage tax was higher than your"
                + " deductible health and care insurance costs?"),
        question(
            "wageTaxAllowance",
            "§ 46(2) no. 4 EStG",
            "Wage above " + wage + " euros, and an allowance (Freibetrag) in your wage tax data?"),
        question(
            "specialPayment",
            "§ 46(2) no. 5 EStG",
            "Your employer taxed a one-off payment, such as a severance payment, at a reduced rate?"),
        question(
            "certificateS",
            "§ 46(2) no. 5a EStG",
            "The letter S on your wage tax certificate (Großbuchstabe S)?"),
        question(
            "officeRequest",
            "§ 149(1) AO",
            "Did the tax office ask you to file a return for this year?"));
  }

  private static Field question(String id, String source, String label) {
    return Field.required(id, SECTION, source, label, Kind.YES_NO_UNSURE);
  }

  @Override
  public List<ReviewLine> review(Answers answers, List<Expense> expenses) {
    return List.of();
  }
}
