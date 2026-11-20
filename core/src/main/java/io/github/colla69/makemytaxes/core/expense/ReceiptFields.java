package io.github.colla69.makemytaxes.core.expense;

import io.github.colla69.makemytaxes.core.GermanFormats;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * The fields of an expense that were read from a receipt. A field is empty if it was not read or
 * did not pass its check. The amounts and the date keep the text as it was read, and are never
 * changed. Only a date in the ISO notation, such as 2022-02-03, is written as 03.02.2022.
 *
 * <pre>{@code
 * ReceiptFields read = ReceiptFields.checked("1.234,56", "31.02.2022", "Shop – pens", "MEDICAL", "");
 * read.paidOn(); // "", because the date does not exist
 * }</pre>
 *
 * @param amount the total paid, the gross amount, as 1.234,56
 * @param paidOn the payment date, or else the invoice date, as 03.02.2022
 * @param topic the topic, or null
 * @param labourCost for household services only: the labour part of the invoice. Only this part
 *     counts, § 35a Abs. 5 Satz 2 EStG.
 * @param unsure the names of the fields, such as "paidOn", whose value was read but is unsure
 * @param namesNoPaymentDate the receipt names neither a payment date nor an invoice date
 * @param otherDates other dates on the receipt, such as an order date. They are a hint only, and
 *     never fill {@code paidOn}.
 */
public record ReceiptFields(
    String amount,
    String paidOn,
    String description,
    ExpenseTopic topic,
    String labourCost,
    Set<String> unsure,
    boolean namesNoPaymentDate,
    List<NamedDate> otherDates) {

  /** A date on the receipt with the name that the receipt gives it, such as "Bestelldatum". */
  public record NamedDate(String name, String date) {}

  private static final DateTimeFormatter ISO_DATE =
      DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);

  public ReceiptFields {
    unsure = Set.copyOf(unsure);
    otherDates = List.copyOf(otherDates);
  }

  public static ReceiptFields empty() {
    return new ReceiptFields("", "", "", null, "", Set.of(), false, List.of());
  }

  /** Keeps each field that passes its check, and leaves the others empty. */
  public static ReceiptFields checked(
      String amount, String paidOn, String description, String topicName, String labourCost) {
    ExpenseTopic topic = topicOf(topicName);
    return new ReceiptFields(
        money(amount),
        date(paidOn),
        description.strip(),
        topic,
        topic == ExpenseTopic.HOUSEHOLD_SERVICES ? money(labourCost) : "",
        Set.of(),
        false,
        List.of());
  }

  /** These fields, with the names of the fields whose value was read but is unsure. */
  public ReceiptFields withUnsure(Set<String> fieldNames) {
    return new ReceiptFields(
        amount, paidOn, description, topic, labourCost, fieldNames, namesNoPaymentDate, otherDates);
  }

  /**
   * These fields, with what the receipt says about its dates. A date that does not pass the check,
   * or that has no name, is left out.
   */
  public ReceiptFields withDates(boolean namesNoPaymentDate, List<NamedDate> otherDates) {
    List<NamedDate> checked =
        otherDates.stream()
            .map(other -> new NamedDate(other.name().strip(), date(other.date())))
            .filter(other -> !other.name().isEmpty() && !other.date().isEmpty())
            .toList();
    return new ReceiptFields(
        amount, paidOn, description, topic, labourCost, unsure, namesNoPaymentDate, checked);
  }

  /** No field was read. The notes about the dates alone do not count as a reading. */
  public boolean isEmpty() {
    return amount.isEmpty()
        && paidOn.isEmpty()
        && description.isEmpty()
        && topic == null
        && labourCost.isEmpty();
  }

  /** The field was read, but the reader was not sure, so the user checks it. */
  public boolean isUnsure(String fieldName) {
    return unsure.contains(fieldName);
  }

  /** The date stays empty because the receipt names neither a payment nor an invoice date. */
  public boolean lacksPaymentDate() {
    return paidOn.isEmpty() && namesNoPaymentDate;
  }

  private static String money(String text) {
    try {
      return GermanFormats.parseMoney(text).isPositive() ? text.strip() : "";
    } catch (IllegalArgumentException e) {
      return "";
    }
  }

  private static String date(String text) {
    try {
      GermanFormats.parseDate(text);
      return text.strip();
    } catch (IllegalArgumentException e) {
      return isoDate(text);
    }
  }

  /** An ISO date names the same day in another notation, so it is written as 03.02.2022. */
  private static String isoDate(String text) {
    try {
      return GermanFormats.format(LocalDate.parse(text.strip(), ISO_DATE));
    } catch (DateTimeParseException e) {
      return "";
    }
  }

  private static ExpenseTopic topicOf(String name) {
    return Arrays.stream(ExpenseTopic.values())
        .filter(topic -> topic.name().equals(name.strip()))
        .findFirst()
        .orElse(null);
  }
}
