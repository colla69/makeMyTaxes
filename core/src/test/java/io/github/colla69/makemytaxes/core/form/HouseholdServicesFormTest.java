package io.github.colla69.makemytaxes.core.form;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.colla69.makemytaxes.core.form.HouseholdServicesForm.Category;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The sources are § 35a EStG in the versions of 30.06.2022 and 31.12.2022, with the same text. */
class HouseholdServicesFormTest {

  private final HouseholdServicesForm form = new HouseholdServicesForm();

  @Test
  void eachCategoryHasABlockForEachInvoice() {
    Answers answers =
        new Answers(Map.of("craftsmen2-invoice", "Made-up Maler, R-2, 03.05.2022"));

    assertThat(form.fields(answers).stream().map(Field::section).distinct())
        .containsExactly(
            "Reinigung 1",
            "Gartenpflege 1",
            "Handwerker 1",
            "Handwerker 2",
            "Nebenkostenabrechnung 1");
    assertThat(form.field("craftsmen7-labourCost")).isPresent();
    assertThat(form.field("unknown1-labourCost")).isEmpty();
  }

  @Test
  void craftsmenAreSection3AndTheStatementSplitsTheLabourIntoSection2And3() {
    // § 35a(3) sentence 1 EStG: Handwerkerleistungen. § 35a(2) sentence 1: haushaltsnahe
    // Dienstleistungen.
    assertThat(lineOf("craftsmen1-labourCost")).isEqualTo("§ 35a Abs. 3 EStG");
    assertThat(lineOf("cleaning1-labourCost")).isEqualTo("§ 35a Abs. 2 EStG");
    assertThat(lineOf("ancillaryCosts1-labourServices")).isEqualTo("§ 35a Abs. 2 EStG");
    assertThat(lineOf("ancillaryCosts1-labourCraftsmen")).isEqualTo("§ 35a Abs. 3 EStG");
  }

  @Test
  void aCashPaymentDoesNotCount() {
    // § 35a(5) sentence 3 EStG: the payment must go to the account of the provider.
    Answers cash =
        new Answers(
            Map.of(
                "cleaning1-invoice", "Made-up Reinigung, R-1, 31.01.2022",
                "cleaning1-paidAmount", "300,00",
                "cleaning1-labourCost", "300,00",
                "cleaning1-payment", Kind.CASH));

    assertThat(form.check(cash)).singleElement().asString().contains("Barzahlung");
    assertThat(form.check(withPayment(cash, Kind.TRANSFER))).isEmpty();
  }

  @Test
  void anAmountNeedsAnInvoice() {
    // § 35a(5) sentence 3 EStG: the taxpayer must have received an invoice.
    Answers noInvoice =
        new Answers(Map.of("gardening1-paidAmount", "120,00", "gardening1-payment", Kind.TRANSFER));

    assertThat(form.check(noInvoice)).singleElement().asString().contains("Rechnung an");
  }

  @Test
  void anAmountWithoutAPaymentDoesNotCount() {
    // § 35a(5) sentence 3 EStG: an empty payment is no proof of a transfer to the account.
    Answers noPayment =
        new Answers(
            Map.of(
                "cleaning1-invoice", "Made-up Reinigung, R-1, 31.01.2022",
                "cleaning1-labourCost", "50,00"));

    assertThat(form.check(noPayment)).singleElement().asString().contains("Zahlungsweg");
    assertThat(form.check(new Answers(Map.of("cleaning1-invoice", "Made-up")))).isEmpty();
  }

  @Test
  void theStatementHasNoPaymentField() {
    // § 35a(5) sentence 3 EStG names the account of the provider. The law does not say if the
    // landlord counts, so the app does not ask.
    assertThat(form.field("ancillaryCosts1-payment")).isEmpty();
    assertThat(lineOf("gardening1-labourCost")).isEmpty();
  }

  @Test
  void theLabourCostCannotBeHigherThanThePaidAmount() {
    // § 35a(5) sentence 2 EStG: only the labour costs count, and they are a part of the invoice.
    Answers statement =
        new Answers(
            Map.of(
                "ancillaryCosts1-statement", "Made-up Hausverwaltung, 15.03.2022",
                "ancillaryCosts1-paidAmount", "100,00",
                "ancillaryCosts1-labourServices", "60,00",
                "ancillaryCosts1-labourCraftsmen", "50,00"));

    assertThat(form.check(statement))
        .containsExactly("Nebenkostenabrechnung 1: Die Arbeitskosten sind höher als der bezahlte Betrag.");
  }

  @Test
  void theIdOfAnEntryFieldNamesTheCategoryAndTheEntry() {
    assertThat(Category.GARDENING.fieldId(3, "paidAmount")).isEqualTo("gardening3-paidAmount");
    assertThat(Category.GARDENING.count(new Answers(Map.of("gardening3-invoice", "x"))))
        .isEqualTo(3);
    assertThat(Category.CLEANING.count(new Answers(Map.of("gardening3-invoice", "x"))))
        .isEqualTo(1);
  }

  private String lineOf(String fieldId) {
    return form.field(fieldId).orElseThrow().line();
  }

  private static Answers withPayment(Answers answers, String payment) {
    Map<String, String> values = new HashMap<>(answers.values());
    values.put("cleaning1-payment", payment);
    return new Answers(values);
  }
}
