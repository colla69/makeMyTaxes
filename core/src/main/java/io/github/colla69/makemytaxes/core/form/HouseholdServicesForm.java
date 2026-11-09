package io.github.colla69.makemytaxes.core.form;

import static io.github.colla69.makemytaxes.core.form.Field.optional;

import io.github.colla69.makemytaxes.core.expense.Money;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Haushaltsnahe Aufwendungen, § 35a EStG: one block for each invoice of a category, and one for
 * each ancillary cost statement of a rental. The form keeps the values of each invoice. It does
 * not add them up and does not compute the tax reduction.
 */
public final class HouseholdServicesForm implements FormModule {

  public static final String ID = "household-services";

  /** The paid amount of an invoice. */
  public static final String PAID = "paidAmount";

  /** The labour cost share of an invoice. Only this share counts: § 35a(5) sentence 2 EStG. */
  public static final String LABOUR = "labourCost";

  /** The payment of an invoice. It must go to an account: § 35a(5) sentence 3 EStG. */
  public static final String PAYMENT = "payment";

  /** The labour costs for household services on an ancillary cost statement. */
  public static final String LABOUR_SERVICES = "labourServices";

  /** The labour costs for craftsmen on an ancillary cost statement. */
  public static final String LABOUR_CRAFTSMEN = "labourCraftsmen";

  private static final String SECTION_2 = "§ 35a Abs. 2 EStG";
  private static final String SECTION_3 = "§ 35a Abs. 3 EStG";

  private static final Field PAID_FIELD =
      optional(PAID, "", "", "Bezahlter Betrag (EUR)", Kind.MONEY);

  private static final Field PAYMENT_FIELD =
      optional(PAYMENT, "", "§ 35a Abs. 5 Satz 3 EStG", "Zahlungsweg", Kind.PAYMENT)
          .withHelp(
              "Nur eine Zahlung auf das Konto des Leistenden zählt, zum Beispiel eine Überweisung"
                  + " oder eine Lastschrift. Eine Barzahlung zählt nicht.");

  private static List<Field> invoiceFields(String line) {
    return List.of(
        optional("invoice", "", line, "Rechnung: Aussteller, Nummer und Datum", Kind.TEXT),
        PAYMENT_FIELD,
        PAID_FIELD,
        optional(LABOUR, "", line, "Davon Arbeitskosten (EUR)", Kind.MONEY)
            .withHelp("Der Anteil der Arbeitskosten, wie er auf der Rechnung steht."));
  }

  /**
   * The statement has no payment field: the law library does not say whether a statement of the
   * landlord, paid to the landlord, meets § 35a(5) sentence 3 EStG.
   */
  private static final List<Field> STATEMENT_FIELDS =
      List.of(
          optional("statement", "", "", "Nebenkostenabrechnung: Aussteller und Datum", Kind.TEXT),
          PAID_FIELD,
          optional(
              LABOUR_SERVICES,
              "",
              SECTION_2,
              "Davon Arbeitskosten für haushaltsnahe Dienstleistungen (EUR)",
              Kind.MONEY),
          optional(
              LABOUR_CRAFTSMEN,
              "",
              SECTION_3,
              "Davon Arbeitskosten für Handwerkerleistungen (EUR)",
              Kind.MONEY));

  /** The kinds of costs, in the order of the interview. */
  public enum Category {
    CLEANING("cleaning", "Reinigung", invoiceFields(SECTION_2)),
    // The law does not say if gardening is a service of Abs. 2 or a craft of Abs. 3.
    GARDENING("gardening", "Gartenpflege", invoiceFields("")),
    CRAFTSMEN("craftsmen", "Handwerker", invoiceFields(SECTION_3)),
    ANCILLARY_COSTS("ancillaryCosts", "Nebenkostenabrechnung", STATEMENT_FIELDS);

    private final String id;
    private final String label;
    private final List<Field> fields;

    Category(String id, String label, List<Field> fields) {
      this.id = id;
      this.label = label;
      this.fields = fields;
    }

    public String id() {
      return id;
    }

    public String label() {
      return label;
    }

    /** The ID of a field of one entry, for example {@code cleaning2-paidAmount}. */
    public String fieldId(int entry, String fieldId) {
      return id + entry + "-" + fieldId;
    }

    /** The fields of one entry. Each entry has its own block. */
    public List<Field> fields(int entry) {
      return fields.stream()
          .map(field -> field.inEntry(fieldId(entry, field.id()), label + " " + entry))
          .toList();
    }

    /** The number of entries in the answers. The form always has the first entry. */
    public int count(Answers answers) {
      Pattern pattern = Pattern.compile(Pattern.quote(id) + "(\\d+)-.+");
      int count = 1;
      for (String fieldId : answers.values().keySet()) {
        Matcher matcher = pattern.matcher(fieldId);
        if (matcher.matches()) {
          count = Math.max(count, Integer.parseInt(matcher.group(1)));
        }
      }
      return count;
    }
  }

  private static final Pattern ENTRY_ID = Pattern.compile("([a-zA-Z]+)(\\d+)-(.+)");

  @Override
  public String id() {
    return ID;
  }

  @Override
  public String title() {
    return "Haushaltsnahe Aufwendungen";
  }

  @Override
  public List<Field> fields() {
    return fields(new Answers(Map.of()));
  }

  /** One block for each entry of each category. */
  @Override
  public List<Field> fields(Answers answers) {
    List<Field> fields = new ArrayList<>();
    for (Category category : Category.values()) {
      for (int entry = 1; entry <= category.count(answers); entry++) {
        fields.addAll(category.fields(entry));
      }
    }
    return fields;
  }

  @Override
  public Optional<Field> field(String fieldId) {
    Matcher matcher = ENTRY_ID.matcher(fieldId);
    if (!matcher.matches()) {
      return Optional.empty();
    }
    int entry = Integer.parseInt(matcher.group(2));
    for (Category category : Category.values()) {
      if (category.id().equals(matcher.group(1))) {
        return category.fields(entry).stream()
            .filter(field -> field.id().equals(fieldId))
            .findFirst();
      }
    }
    return Optional.empty();
  }

  /**
   * § 35a(5) EStG, version of 30.06.2022 and 31.12.2022: only labour costs count (sentence 2),
   * and only with an invoice and a payment to the account of the provider (sentence 3).
   */
  @Override
  public List<String> check(Answers answers) {
    List<String> messages = new ArrayList<>();
    for (Category category : Category.values()) {
      for (int entry = 1; entry <= category.count(answers); entry++) {
        messages.addAll(checkEntry(answers, category, entry));
      }
    }
    return messages;
  }

  private static List<String> checkEntry(Answers answers, Category category, int entry) {
    List<String> messages = new ArrayList<>();
    String name = category.label() + " " + entry + ": ";
    if (category != Category.ANCILLARY_COSTS) {
      messages.addAll(checkInvoice(answers, category, entry));
    }
    Money labour =
        answers
            .money(category.fieldId(entry, LABOUR))
            .plus(answers.money(category.fieldId(entry, LABOUR_SERVICES)))
            .plus(answers.money(category.fieldId(entry, LABOUR_CRAFTSMEN)));
    Money paid = answers.money(category.fieldId(entry, PAID));
    if (answers.has(category.fieldId(entry, PAID)) && labour.cents() > paid.cents()) {
      messages.add(name + "Die Arbeitskosten sind höher als der bezahlte Betrag.");
    }
    return messages;
  }

  /** An amount needs an invoice and a transfer. The interview asks both before the amounts. */
  private static List<String> checkInvoice(Answers answers, Category category, int entry) {
    String name = category.label() + " " + entry + ": ";
    String payment = answers.text(category.fieldId(entry, PAYMENT));
    if (Kind.CASH.equals(payment)) {
      return List.of(
          name
              + "Eine Barzahlung oder eine Zahlung an eine andere Person zählt nicht. Die Zahlung"
              + " muss auf das Konto des Leistenden gehen (§ 35a Abs. 5 Satz 3 EStG). Erfassen Sie diese Rechnung nicht.");
    }
    boolean hasAmounts =
        answers.has(category.fieldId(entry, PAID)) || answers.has(category.fieldId(entry, LABOUR));
    if (!hasAmounts) {
      return List.of();
    }
    List<String> messages = new ArrayList<>();
    if (!answers.has(category.fieldId(entry, "invoice"))) {
      messages.add(
          name
              + "Geben Sie die Rechnung an. Ohne Rechnung zählt die Ausgabe nicht (§ 35a Abs. 5"
              + " Satz 3 EStG).");
    }
    if (!Kind.TRANSFER.equals(payment)) {
      messages.add(
          name
              + "Wählen Sie den Zahlungsweg. Nur eine Zahlung auf das Konto des Leistenden zählt,"
              + " zum Beispiel eine Überweisung oder eine Lastschrift (§ 35a Abs. 5 Satz 3 EStG).");
    }
    return messages;
  }
}
