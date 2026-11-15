package io.github.colla69.makemytaxes.core.form;

import static io.github.colla69.makemytaxes.core.form.Field.optional;

import io.github.colla69.makemytaxes.core.GermanFormats;
import io.github.colla69.makemytaxes.core.expense.Expense;
import io.github.colla69.makemytaxes.core.expense.ExpenseTopic;
import io.github.colla69.makemytaxes.core.expense.Money;
import java.util.ArrayList;
import java.util.List;

/**
 * Anlage Außergewöhnliche Belastungen, with the Krankheitskosten of § 33 EStG only. The costs are
 * the medical expenses of the expense page, each with its receipt. The fields hold the facts for
 * the estimate of the zumutbare Belastung ({@link MedicalCostEstimate}). The tax office computes
 * that amount itself, so the fields are not values for Mein ELSTER.
 *
 * <p>The law library has no form instruction of this Anlage, so the lines have no number.
 */
public final class ExtraordinaryBurdensForm implements FormModule {

  public static final String ID = "extraordinary-burdens";

  /** The number of children of § 33(3) sentence 2 EStG. */
  public static final String CHILDREN = "children";

  /** Yes if the splitting tariff of § 32a(6) EStG applies. */
  public static final String SPLITTING = "splitting";

  static final String MEDICAL = "Krankheitskosten";

  private static final String FACTS = "Angaben zur zumutbaren Belastung";

  private static final List<Field> FIELDS =
      List.of(
          optional(
                  CHILDREN,
                  FACTS,
                  "§ 33 Abs. 3 Satz 2 EStG",
                  "Zahl der Kinder",
                  Kind.WHOLE_NUMBER)
              .withHelp(
                  "Kinder, für die Sie Anspruch auf Kindergeld oder auf einen Freibetrag nach"
                      + " § 32 Abs. 6 EStG haben. Die Zahl bestimmt den Prozentsatz der"
                      + " zumutbaren Belastung."),
          optional(
                  SPLITTING,
                  FACTS,
                  "§ 32a Abs. 6 EStG",
                  "Splitting-Verfahren",
                  Kind.YES_NO_UNSURE)
              .withHelp(
                  "Die App erstellt keine Zusammenveranlagung. Wählen Sie Ja nur, wenn für Sie"
                      + " das Splitting-Verfahren nach § 32a Abs. 6 EStG gilt. Bei der"
                      + " Einzelveranlagung von Ehegatten gilt es nicht (§ 32a Abs. 6 Satz 2"
                      + " EStG). Ohne Ja schätzt die App mit dem Grundtarif."));

  @Override
  public String id() {
    return ID;
  }

  @Override
  public String title() {
    return "Außergewöhnliche Belastungen";
  }

  @Override
  public List<Field> fields() {
    return FIELDS;
  }

  /**
   * Each medical cost with its payment and its refund, then the sums. The library has no form
   * instruction for this Anlage yet, so the lines have no numbers. The fields are facts for the
   * estimate only.
   */
  @Override
  public List<ReviewLine> review(Answers answers, List<Expense> expenses) {
    List<Expense> medical = medicalCosts(expenses);
    if (medical.isEmpty()) {
      return List.of();
    }
    List<ReviewLine> lines = new ArrayList<>();
    Money paid = Money.ofCents(0);
    Money refunded = Money.ofCents(0);
    for (Expense expense : medical) {
      String name = expense.purpose().description();
      Source source = Source.of(expense.evidence());
      lines.add(
          line(
              name + ", bezahlt am " + GermanFormats.format(expense.payment().paidOn()),
              expense.payment().amount(),
              source));
      if (expense.refunded().isPositive()) {
        lines.add(line(name + ": erstattet", expense.refunded(), source));
      }
      paid = paid.plus(expense.payment().amount());
      refunded = refunded.plus(expense.refunded());
    }
    lines.add(line("Krankheitskosten: Aufwendungen", paid, new Source.Sum()));
    lines.add(
        line(
            "Krankheitskosten: Erstattungen von Krankenkasse oder Versicherung",
            refunded,
            new Source.Sum()));
    return lines;
  }

  static List<Expense> medicalCosts(List<Expense> expenses) {
    return expenses.stream()
        .filter(expense -> expense.purpose().topic() == ExpenseTopic.MEDICAL)
        .toList();
  }

  private static ReviewLine line(String label, Money amount, Source source) {
    return new ReviewLine(MEDICAL, "", label, GermanFormats.format(amount), source);
  }
}
