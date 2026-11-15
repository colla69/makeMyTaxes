package io.github.colla69.makemytaxes.core.form;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;

import io.github.colla69.makemytaxes.core.TaxYear;
import io.github.colla69.makemytaxes.core.document.DocumentRef;
import io.github.colla69.makemytaxes.core.expense.Evidence;
import io.github.colla69.makemytaxes.core.expense.Expense;
import io.github.colla69.makemytaxes.core.expense.ExpenseTopic;
import io.github.colla69.makemytaxes.core.expense.Money;
import io.github.colla69.makemytaxes.core.expense.Payment;
import io.github.colla69.makemytaxes.core.expense.Purpose;
import io.github.colla69.makemytaxes.core.form.ReasonableBurden.Group;
import io.github.colla69.makemytaxes.core.year.YearPack;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * § 33(1) EStG, versions of 30.06.2022 and 31.12.2022: only the part of the costs above the
 * zumutbare Belastung reduces the Gesamtbetrag der Einkünfte. § 2(3) EStG and § 9a sentence 1 no. 1a
 * and sentence 2 EStG, same versions: the income from employment is the gross wage less the work
 * expenses or the lump sum.
 */
class MedicalCostEstimateTest {

  private static final YearPack PACK = YearPack.of(new TaxYear(2022)).orElseThrow();
  private static final Answers NONE = new Answers(Map.of());

  @Test
  void onlyThePartAboveTheReasonableBurdenCounts() {
    // 30.000 EUR less the lump sum of 1.200 EUR: 28.800 EUR, burden 767,00 + 13.460 x 6 %.
    Answers anlageN = new Answers(Map.of("grossWage", "30.000,00"));
    var estimate =
        MedicalCostEstimate.of(
            PACK, anlageN, NONE, List.of(medical(2_500_00, 300_00), medical(500_00, 0)));

    assertThat(estimate.costs()).isEqualTo(Money.ofCents(3_000_00));
    assertThat(estimate.refunds()).isEqualTo(Money.ofCents(300_00));
    assertThat(estimate.burden()).isEqualTo(Money.ofCents(2_700_00));
    assertThat(estimate.totalIncome()).isEqualTo(Money.ofCents(28_800_00));
    assertThat(estimate.group()).isEqualTo(Group.BASIC_TARIFF);
    assertThat(estimate.reasonableBurden()).isEqualTo(Money.ofCents(1_574_60));
    assertThat(estimate.deductible()).isEqualTo(Money.ofCents(1_125_40));
    assertThat(estimate.wageKnown()).isTrue();
  }

  @Test
  void withoutAGrossWageTheIncomeIsNotKnown() {
    var estimate = MedicalCostEstimate.of(PACK, NONE, NONE, List.of(medical(500_00, 0)));

    assertThat(estimate.wageKnown()).isFalse();
  }

  @Test
  void costsBelowTheReasonableBurdenDeductNothing() {
    Answers anlageN = new Answers(Map.of("grossWage", "30.000,00"));
    var estimate = MedicalCostEstimate.of(PACK, anlageN, NONE, List.of(medical(500_00, 0)));

    assertThat(estimate.deductible()).isEqualTo(Money.ofCents(0));
  }

  @Test
  void theWageOfEachEmployerCountsAndChildrenSetTheRow() {
    Answers anlageN =
        new Answers(Map.of("grossWage", "10.000,00", "employer2-grossWage", "6.540,00"));
    Answers burdens = new Answers(Map.of("children", "1", "splitting", "NO"));
    var estimate = MedicalCostEstimate.of(PACK, anlageN, burdens, List.of(medical(1_000_00, 0)));

    // 16.540 EUR less 1.200 EUR: 15.340 EUR, all of it in the first band at 2 %.
    assertThat(estimate.totalIncome()).isEqualTo(Money.ofCents(15_340_00));
    assertThat(estimate.group()).isEqualTo(Group.ONE_OR_TWO_CHILDREN);
    assertThat(estimate.reasonableBurden()).isEqualTo(Money.ofCents(306_80));
  }

  @Test
  void splittingCountsOnlyForAYes() {
    var unsure = new Answers(Map.of("splitting", "UNSURE"));
    var yes = new Answers(Map.of("splitting", "YES"));

    assertThat(MedicalCostEstimate.of(PACK, NONE, unsure, List.of()).group())
        .isEqualTo(Group.BASIC_TARIFF);
    assertThat(MedicalCostEstimate.of(PACK, NONE, yes, List.of()).group())
        .isEqualTo(Group.SPLITTING_TARIFF);
  }

  @Test
  void higherWorkExpensesReplaceTheLumpSum() {
    Answers anlageN = new Answers(Map.of("grossWage", "30.000,00"));
    var laptop =
        new Expense(
            new Payment(LocalDate.of(2022, 3, 1), Money.ofCents(2_000_00)),
            new Purpose(ExpenseTopic.WORK_EQUIPMENT, "Made-up laptop"),
            "laptop.pdf");

    var estimate = MedicalCostEstimate.of(PACK, anlageN, NONE, List.of(laptop));

    assertThat(estimate.totalIncome()).isEqualTo(Money.ofCents(28_000_00));
  }

  @Test
  void theFormListsEachCostAndRefundWithItsReceiptThenTheSums() {
    List<ReviewLine> lines =
        new ExtraordinaryBurdensForm().review(NONE, List.of(medical(2_500_00, 300_00)));

    assertThat(lines)
        .extracting(ReviewLine::label, ReviewLine::value)
        .containsExactly(
            tuple("Made-up dentist, bezahlt am 01.03.2022", "2.500,00"),
            tuple("Made-up dentist: erstattet", "300,00"),
            tuple("Krankheitskosten: Aufwendungen", "2.500,00"),
            tuple(
                "Krankheitskosten: Erstattungen von Krankenkasse oder Versicherung", "300,00"));
    assertThat(lines.get(1).source().label()).isEqualTo("Beleg: dentist.pdf");
  }

  @Test
  void aRefundBelongsOnlyToAMedicalCostAndIsNotAboveThePayment() {
    var payment = new Payment(LocalDate.of(2022, 3, 1), Money.ofCents(100_00));
    var evidence = new Evidence("", null, "Made-up basis");

    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new Expense(
                    payment,
                    new Purpose(ExpenseTopic.WORK_OTHER, "Made-up"),
                    evidence,
                    Money.ofCents(1_00)));
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new Expense(
                    payment,
                    new Purpose(ExpenseTopic.MEDICAL, "Made-up"),
                    evidence,
                    Money.ofCents(100_01)));
  }

  private static Expense medical(long paidCents, long refundedCents) {
    return new Expense(
        new Payment(LocalDate.of(2022, 3, 1), Money.ofCents(paidCents)),
        new Purpose(ExpenseTopic.MEDICAL, "Made-up dentist"),
        new Evidence("", new DocumentRef(1, "dentist.pdf"), ""),
        Money.ofCents(refundedCents));
  }
}
