package io.github.colla69.makemytaxes.core.form;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.colla69.makemytaxes.core.expense.ExpenseTopic;
import io.github.colla69.makemytaxes.core.form.Interview.Question;
import io.github.colla69.makemytaxes.core.form.Interview.Step;
import io.github.colla69.makemytaxes.core.form.Interview.Topic;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InterviewTest {

  @Test
  void theQuestionsComeOneAtATimeInOrder() {
    assertThat(Interview.next(new Answers(Map.of())))
        .map(Interview.Question::id)
        .hasValue(Interview.PERSONAL_DETAILS);
    assertThat(
            Interview.next(
                new Answers(
                    Map.of(Interview.PERSONAL_DETAILS, Interview.DONE, "employed", Interview.NO))))
        .map(Interview.Question::id)
        .hasValue("occupation");
  }

  @Test
  void theTopicsComeInTheOrderJobCommuteInsuranceHousehold() {
    assertThat(Interview.questions().stream().map(Question::topic).distinct())
        .containsExactly(Topic.JOB, Topic.COMMUTE, Topic.INSURANCE, Topic.HOUSEHOLD);
    for (Topic topic : Topic.values()) {
      assertThat(Interview.questionsOf(topic)).isNotEmpty();
    }
  }

  @Test
  void aFieldQuestionNamesAFieldOfItsForm() {
    Question occupation = Interview.find("occupation").orElseThrow();
    assertThat(occupation.formId()).isEqualTo(MainForm.ID);
    assertThat(new MainForm().fields()).anyMatch(field -> field.id().equals("occupation"));
    assertThat(Interview.find("commuteKm").orElseThrow().formId()).isEqualTo(AnlageN.ID);
  }

  @Test
  void backAndNextStayInTheTopicAndThenMoveToTheNeighbourTopic() {
    Question first = Interview.questionsOf(Topic.COMMUTE).getFirst();
    assertThat(Interview.previousInTopic(first)).isEmpty();
    assertThat(Interview.nextInTopic(first)).map(Question::topic).hasValue(Topic.COMMUTE);
    assertThat(Interview.previousTopic(Topic.COMMUTE)).hasValue(Topic.JOB);
    assertThat(Interview.previousTopic(Topic.JOB)).isEmpty();
    assertThat(Interview.nextTopic(Topic.HOUSEHOLD)).isEmpty();
  }

  @Test
  void aFieldAnswerDoesNotAddAPage() {
    Map<String, String> values = new LinkedHashMap<>();
    Interview.questions().forEach(question -> values.put(question.id(), Interview.DONE));
    assertThat(Interview.steps(new Answers(values)))
        .containsExactly(new Step(FilingCheckForm.ID, null), new Step("main", null));
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
