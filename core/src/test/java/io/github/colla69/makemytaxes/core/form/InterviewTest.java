package io.github.colla69.makemytaxes.core.form;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.colla69.makemytaxes.core.expense.ExpenseTopic;
import io.github.colla69.makemytaxes.core.form.Interview.Step;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InterviewTest {

  @Test
  void theQuestionsComeOneAtATimeInOrder() {
    assertThat(Interview.next(new Answers(Map.of()))).map(Interview.Question::id).hasValue("employed");
    assertThat(Interview.next(new Answers(Map.of("employed", Interview.NO))))
        .map(Interview.Question::id)
        .hasValue("workEquipment");
  }

  @Test
  void withoutAnyYesTheReturnNeedsOnlyTheFilingCheckAndTheMainForm() {
    assertThat(Interview.steps(new Answers(Map.of())))
        .containsExactly(new Step(FilingCheckForm.ID, null), new Step("main", null));
  }

  @Test
  void eachYesAddsItsPageAndTheInterviewEndsWhenAllAreAnswered() {
    Map<String, String> values = new LinkedHashMap<>();
    Interview.questions().forEach(question -> values.put(question.id(), Interview.NO));
    values.put("employed", Interview.YES);
    values.put("householdServices", Interview.YES);
    Answers answers = new Answers(values);

    assertThat(Interview.isDone(answers)).isTrue();
    assertThat(Interview.steps(answers))
        .containsExactly(
            new Step(FilingCheckForm.ID, null),
            new Step("main", null),
            new Step(AnlageN.ID, null),
            new Step(null, ExpenseTopic.HOUSEHOLD_SERVICES));
  }
}
