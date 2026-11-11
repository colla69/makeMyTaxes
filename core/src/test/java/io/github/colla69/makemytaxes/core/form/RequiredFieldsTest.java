package io.github.colla69.makemytaxes.core.form;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.colla69.makemytaxes.core.TaxYear;
import io.github.colla69.makemytaxes.core.form.HouseholdServicesForm.Category;
import io.github.colla69.makemytaxes.core.form.Interview.Question;
import io.github.colla69.makemytaxes.core.form.Interview.Step;
import io.github.colla69.makemytaxes.core.form.Interview.Type;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * When the interview is done, each required field of each form that the return needs has a value.
 * The confirmation of the personal details fills the personal fields of the main form, and a field
 * question fills its own field. No form has a default or a document value for a required field yet.
 */
class RequiredFieldsTest {

  static List<Integer> years() {
    return List.of(2022, 2023, 2024, 2025, 2026);
  }

  @ParameterizedTest
  @MethodSource("years")
  void anEmployeeWithEachTopicIsAskedEachRequiredField(int year) {
    for (String insurance : List.of(Interview.STATUTORY, Interview.PRIVATE)) {
      Map<String, String> answers = new LinkedHashMap<>();
      answers.put(Interview.EMPLOYED, Interview.YES);
      answers.put(Interview.WAGE_CERTIFICATE, Interview.NO);
      answers.put(Interview.ANOTHER_EMPLOYER, Interview.NO);
      answers.put(Interview.HEALTH_INSURANCE, insurance);
      for (Category category : Category.values()) {
        answers.put(category.id(), Interview.YES);
        answers.put(Interview.anotherEntryId(category, 1), Interview.NO);
      }
      assertEachRequiredFieldIsAsked(year, new Answers(answers));
    }
  }

  @ParameterizedTest
  @MethodSource("years")
  void anEmployeeWithTwoEmployersIsAskedEachRequiredFieldOfBoth(int year) {
    Map<String, String> answers = new LinkedHashMap<>();
    answers.put(Interview.EMPLOYED, Interview.YES);
    answers.put(Interview.WAGE_CERTIFICATE, Interview.NO);
    answers.put(Interview.anotherEmployerId(1), Interview.YES);
    answers.put(AnlageN.employerFieldId(2, Interview.WAGE_CERTIFICATE), Interview.NO);
    answers.put(Interview.anotherEmployerId(2), Interview.NO);
    answers.put(Interview.HEALTH_INSURANCE, Interview.STATUTORY);
    Answers interviewAnswers = new Answers(answers);
    assertThat(
            definitionOf(year).interview().questions(interviewAnswers).stream()
                .map(Question::id))
        .as("the second employer is asked")
        .anyMatch(id -> id.startsWith("employer2-"));
    assertEachRequiredFieldIsAsked(year, interviewAnswers);
  }

  private static YearDefinition definitionOf(int year) {
    return YearDefinition.of(new TaxYear(year)).orElseThrow();
  }

  @ParameterizedTest
  @MethodSource("years")
  void aReturnWithoutEmploymentIsAskedEachRequiredFieldOfItsForms(int year) {
    assertEachRequiredFieldIsAsked(year, new Answers(Map.of(Interview.EMPLOYED, Interview.NO)));
  }

  private static void assertEachRequiredFieldIsAsked(int year, Answers interviewAnswers) {
    YearDefinition definition = definitionOf(year);
    Interview interview = definition.interview();
    List<Question> questions = interview.questions(interviewAnswers);
    Set<String> needed = new HashSet<>();
    for (Step step : interview.steps(interviewAnswers)) {
      if (step.isForm()) {
        needed.add(step.formId());
      }
    }
    for (FormModule form : definition.forms()) {
      if (!needed.contains(form.id())) {
        continue;
      }
      Set<String> asked = new HashSet<>();
      for (Question question : questions) {
        if (question.type() == Type.FIELD && question.formId().equals(form.id())) {
          asked.add(question.id());
        }
        if (question.type() == Type.CONFIRM && form.id().equals(MainForm.ID)) {
          asked.addAll(MainForm.PERSONAL_FIELDS);
        }
      }
      // The answers have a value in each asked field, so a form with a list has each entry.
      Map<String, String> filled = new LinkedHashMap<>();
      asked.forEach(fieldId -> filled.put(fieldId, "1"));
      assertThat(form.missing(new Answers(filled)))
          .as("%d, %s: required fields that no question asks", year, form.id())
          .isEmpty();
    }
  }
}
