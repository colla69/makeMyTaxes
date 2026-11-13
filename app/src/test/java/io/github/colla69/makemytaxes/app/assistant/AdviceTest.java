package io.github.colla69.makemytaxes.app.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.colla69.makemytaxes.core.TaxYear;
import java.util.List;
import org.junit.jupiter.api.Test;

class AdviceTest {

  private static final String RULE_ON_FACTS =
      "Schlage nie vor, einen Betrag, ein Datum, eine Zahl von Tagen oder eine andere Tatsache zu"
          + " ändern, um ein besseres Ergebnis zu erhalten.";

  @Test
  void theSystemPromptHoldsTheRuleOnFacts() {
    assertTrue(ReturnChat.instructions(new TaxYear(2022)).contains(RULE_ON_FACTS));
  }

  @Test
  void theQuestionOfTheCheckHoldsTheRuleOnFactsAndAsksForASource() {
    assertTrue(Advice.QUESTION.contains(RULE_ON_FACTS));
    assertTrue(Advice.QUESTION.contains("Lass einen Tipp ohne Quelle weg."));
  }

  @Test
  void aTipWithoutASourceDoesNotAppear() throws AssistantException {
    String answer =
        """
        ```json
        {"tipps": [
          {"art": "abzug", "titel": "Made-up deduction", "text": "Made-up text",
           "beleg": "Made-up receipt", "quelle": "§ 35a Abs. 2 EStG, Fassung vom 01.01.2022"},
          {"art": "wahl", "titel": "Made-up choice without source", "text": "x", "quelle": " "},
          {"art": "naechstes_jahr", "titel": "Made-up tip without source field"}
        ]}
        ```
        """;

    List<Advice.Tip> tips = Advice.tips(answer);

    assertEquals(
        List.of(
            new Advice.Tip(
                "abzug",
                "Made-up deduction",
                "Made-up text",
                "Made-up receipt",
                "§ 35a Abs. 2 EStG, Fassung vom 01.01.2022")),
        tips);
    assertEquals("Abzug", tips.getFirst().kindLabel());
  }

  @Test
  void anAnswerWithoutJsonIsAnError() {
    assertThrows(AssistantException.class, () -> Advice.tips("Made-up text without advice"));
    assertThrows(AssistantException.class, () -> Advice.tips("{not json}"));
  }

  @Test
  void theTextForTheConversationNamesTheDocumentAndTheSource() {
    String text =
        Advice.text(
            List.of(new Advice.Tip("wahl", "Made-up title", "Made-up text", "", "R 9.13 LStR 2022")));

    assertEquals("Wahl: Made-up title\nMade-up text\nQuelle: R 9.13 LStR 2022", text);
    assertEquals("Der Assistent hat keinen Rat mit Quelle gefunden.", Advice.text(List.of()));
  }
}
