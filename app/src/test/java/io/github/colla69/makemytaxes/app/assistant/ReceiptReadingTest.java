package io.github.colla69.makemytaxes.app.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.colla69.makemytaxes.core.expense.ExpenseTopic;
import io.github.colla69.makemytaxes.core.expense.ReceiptFields;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The app fills a field only with a value from the answer that passes its check. */
class ReceiptReadingTest {

  @Test
  void aFullAnswerFillsEveryField() throws AssistantException {
    ReceiptFields read =
        ReceiptReading.fields(
            """
            {"betrag": "1.234,56", "bezahltAm": "03.02.2022",
             "beschreibung": "Made-up optician – glasses", "thema": "MEDICAL",
             "arbeitskosten": "", "unsicher": []}
            """);

    assertEquals(
        ReceiptFields.checked("1.234,56", "03.02.2022", "Made-up optician – glasses", "MEDICAL", ""),
        read);
  }

  @Test
  void anAnswerInACodeBlockIsRead() throws AssistantException {
    ReceiptFields read = ReceiptReading.fields("```json\n{\"betrag\": \"10,00\"}\n```");

    assertEquals("10,00", read.amount());
  }

  @Test
  void anAmountThatDoesNotParseStaysEmpty() throws AssistantException {
    assertEquals("", ReceiptReading.fields("{\"betrag\": \"1,234.56\"}").amount());
    assertEquals("", ReceiptReading.fields("{\"betrag\": \"0,00\"}").amount());
    assertEquals("", ReceiptReading.fields("{\"betrag\": 10}").amount());
  }

  @ParameterizedTest
  @CsvSource({"03.02.2022, 03.02.2022", "3.2.2022, 3.2.2022", "2022-02-03, 03.02.2022"})
  void aDateInEachAcceptedFormatFillsTheSameDay(String answered, String filled)
      throws AssistantException {
    String answer = "{\"bezahltAm\": \"%s\"}".formatted(answered);

    assertEquals(filled, ReceiptReading.fields(answer).paidOn());
  }

  @Test
  void aBadDateStaysEmpty() throws AssistantException {
    assertEquals("", ReceiptReading.fields("{\"bezahltAm\": \"31.02.2022\"}").paidOn());
    assertEquals("", ReceiptReading.fields("{\"bezahltAm\": \"2022-02-31\"}").paidOn());
    assertEquals("", ReceiptReading.fields("{\"bezahltAm\": \"3.2.22\"}").paidOn());
    assertEquals("", ReceiptReading.fields("{\"bezahltAm\": \"3. Februar 2022\"}").paidOn());
  }

  @Test
  void anUnknownTopicStaysEmpty() throws AssistantException {
    assertEquals(null, ReceiptReading.fields("{\"thema\": \"MADE_UP_TOPIC\"}").topic());
    assertEquals(null, ReceiptReading.fields("{\"thema\": \"medical\"}").topic());
  }

  @Test
  void aFieldTheAssistantWasUnsureAboutKeepsItsValueWithTheUnsureMark() throws AssistantException {
    ReceiptFields read =
        ReceiptReading.fields(
            """
            {"betrag": "10,00", "bezahltAm": "03.02.2022", "beschreibung": "Made-up shop",
             "unsicher": ["bezahltAm"]}
            """);

    assertEquals("03.02.2022", read.paidOn());
    assertTrue(read.isUnsure("paidOn"));
    assertFalse(read.isUnsure("amount"));
    assertFalse(read.isUnsure("description"));
  }

  @Test
  void anOrderConfirmationLeavesTheDateEmptyWithTheReasonAndTheOrderDateAsAHint()
      throws AssistantException {
    ReceiptFields read =
        ReceiptReading.fields(
            """
            {"betrag": "49,90", "bezahltAm": "", "ohneZahlungsdatum": true,
             "andereDaten": [{"name": "Bestelldatum", "datum": "05.03.2022"}],
             "beschreibung": "Made-up shop – order", "unsicher": []}
            """);

    assertEquals("", read.paidOn());
    assertTrue(read.lacksPaymentDate());
    assertEquals(List.of(new ReceiptFields.NamedDate("Bestelldatum", "05.03.2022")), read.otherDates());
  }

  @Test
  void anOtherDateThatDoesNotPassTheCheckIsLeftOut() throws AssistantException {
    ReceiptFields read =
        ReceiptReading.fields(
            """
            {"andereDaten": [{"name": "Lieferdatum", "datum": "31.02.2022"},
                             {"name": "", "datum": "05.03.2022"},
                             {"name": "Bestelldatum", "datum": "2022-03-05"}]}
            """);

    assertEquals(List.of(new ReceiptFields.NamedDate("Bestelldatum", "05.03.2022")), read.otherDates());
  }

  @Test
  void anUnknownEntryInTheUnsureListMarksNoField() throws AssistantException {
    ReceiptFields read =
        ReceiptReading.fields("{\"betrag\": \"10,00\", \"unsicher\": [\"10,00\", 3]}");

    assertEquals(Set.of(), read.unsure());
  }

  @Test
  void anAnswerWithOnlyNotesAboutTheDatesReadsNothing() throws AssistantException {
    ReceiptFields read =
        ReceiptReading.fields(
            """
            {"ohneZahlungsdatum": true, "unsicher": ["betrag"],
             "andereDaten": [{"name": "Bestelldatum", "datum": "05.03.2022"}]}
            """);

    assertTrue(read.isEmpty());
  }

  @Test
  void aDateThatWasReadIsNotMissing() throws AssistantException {
    ReceiptFields read =
        ReceiptReading.fields("{\"bezahltAm\": \"03.02.2022\", \"ohneZahlungsdatum\": true}");

    assertFalse(read.lacksPaymentDate());
  }

  @Test
  void theAppKeepsTheAmountAndTheDateAsTheAssistantWroteThem() throws AssistantException {
    ReceiptFields read = ReceiptReading.fields("{\"betrag\": \"1234,5\", \"bezahltAm\": \"3.2.2022\"}");

    assertEquals("1234,5", read.amount());
    assertEquals("3.2.2022", read.paidOn());
  }

  /** § 35a Abs. 5 Satz 2 EStG, Fassung vom 30.06.2022: the reduction applies to labour costs only. */
  @Test
  void theLabourCostCountsOnlyForHouseholdServices() throws AssistantException {
    String answer =
        """
        {"betrag": "500,00", "thema": "%s", "arbeitskosten": "300,00"}
        """;

    assertEquals("300,00", ReceiptReading.fields(answer.formatted("HOUSEHOLD_SERVICES")).labourCost());
    assertEquals("", ReceiptReading.fields(answer.formatted("WORK_EQUIPMENT")).labourCost());
  }

  @Test
  void anAnswerWithoutAnObjectIsAFailure() {
    assertThrows(AssistantException.class, () -> ReceiptReading.fields("Made-up answer"));
    assertThrows(AssistantException.class, () -> ReceiptReading.fields("{not json}"));
  }

  @Test
  void theQuestionNamesTheFileAndEveryTopic() {
    String question = ReceiptReading.question("/made-up/beleg.pdf");

    assertTrue(question.contains("/made-up/beleg.pdf"));
    assertTrue(question.contains("\"bezahltAm\""));
    assertTrue(question.contains("TT.MM.JJJJ"));
    assertTrue(question.contains("\"ohneZahlungsdatum\""));
    assertTrue(question.contains("\"andereDaten\""));
    for (ExpenseTopic topic : ExpenseTopic.values()) {
      assertTrue(question.contains(topic.name()), topic.name());
    }
  }
}
