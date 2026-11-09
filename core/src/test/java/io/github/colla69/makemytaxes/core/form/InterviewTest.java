package io.github.colla69.makemytaxes.core.form;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.colla69.makemytaxes.core.TaxYear;
import io.github.colla69.makemytaxes.core.expense.ExpenseTopic;
import io.github.colla69.makemytaxes.core.form.Interview.Question;
import io.github.colla69.makemytaxes.core.form.Interview.Step;
import io.github.colla69.makemytaxes.core.form.Interview.Topic;
import io.github.colla69.makemytaxes.core.form.Interview.Type;
import io.github.colla69.makemytaxes.core.year.YearPack;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InterviewTest {

  private static final Answers NONE = new Answers(Map.of());

  @Test
  void theQuestionsComeOneAtATimeInOrder() {
    assertThat(Interview.next(NONE))
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
    assertThat(Interview.questions(NONE).stream().map(Question::topic).distinct())
        .containsExactly(Topic.JOB, Topic.COMMUTE, Topic.INSURANCE, Topic.HOUSEHOLD);
    for (Topic topic : Topic.values()) {
      assertThat(Interview.questionsOf(NONE, topic)).isNotEmpty();
    }
  }

  @Test
  void aFieldQuestionNamesAFieldOfItsForm() {
    Question occupation = Interview.find(NONE, "occupation").orElseThrow();
    assertThat(occupation.formId()).isEqualTo(MainForm.ID);
    assertThat(new MainForm().fields()).anyMatch(field -> field.id().equals("occupation"));
    assertThat(Interview.find(NONE, "commuteKm").orElseThrow().formId()).isEqualTo(AnlageN.ID);
  }

  @Test
  void anEmployeeIsAskedTheLinesOfTheCertificateAndThenForAnotherEmployer() {
    Answers employed = new Answers(Map.of("employed", Interview.YES));

    assertThat(ids(Interview.questionsOf(employed, Topic.JOB)))
        .containsExactly(
            Interview.PERSONAL_DETAILS,
            "employed",
            "employerName",
            "employedFrom",
            "employedTo",
            "taxClass",
            "grossWage",
            "wageTax",
            "solidarity",
            "churchTax",
            "shortTimeWork",
            "employerCommute",
            "anotherEmployer",
            "occupation");
    AnlageN anlageN = new AnlageN(YearPack.of(new TaxYear(2022)).orElseThrow());
    for (Question question : Interview.questionsOf(employed, Topic.JOB)) {
      if (question.type() == Type.FIELD && question.formId().equals(AnlageN.ID)) {
        assertThat(anlageN.field(question.id())).isPresent();
      }
    }
    assertThat(Interview.find(employed, "grossWage").orElseThrow().text()).contains("Nr. 3");
  }

  @Test
  void aYesToAnotherEmployerAsksTheSameLinesForTheNextEmployerAndANoEndsTheEmployers() {
    Answers twoEmployers =
        new Answers(
            Map.of(
                "employed", Interview.YES,
                "anotherEmployer", Interview.YES,
                "employer2-anotherEmployer", Interview.NO));

    List<String> ids = ids(Interview.questionsOf(twoEmployers, Topic.JOB));
    assertThat(ids).contains("employer2-employerName", "employer2-grossWage");
    assertThat(ids).doesNotContain("employer3-employerName");
    assertThat(ids.subList(ids.indexOf("employer2-anotherEmployer"), ids.size()))
        .containsExactly("employer2-anotherEmployer", "occupation");
    assertThat(Interview.find(twoEmployers, "employer2-grossWage").orElseThrow().text())
        .startsWith("Arbeitgeber 2:");
    assertThat(Interview.find(twoEmployers, "employer2-grossWage").orElseThrow().hintId())
        .isEqualTo("grossWage");
    // The next question after a yes is the first one of the next employer.
    Question another = Interview.find(twoEmployers, "anotherEmployer").orElseThrow();
    assertThat(Interview.nextInTopic(twoEmployers, another))
        .map(Question::id)
        .hasValue("employer2-employerName");
  }

  @Test
  void withoutEmploymentThereAreNoEmployerQuestions() {
    Answers notEmployed = new Answers(Map.of("employed", Interview.NO));
    assertThat(ids(Interview.questions(notEmployed))).doesNotContain("employerName", "grossWage");
  }

  @Test
  void backAndNextStayInTheTopicAndThenMoveToTheNeighbourTopic() {
    Question first = Interview.questionsOf(NONE, Topic.COMMUTE).getFirst();
    assertThat(Interview.previousInTopic(NONE, first)).isEmpty();
    assertThat(Interview.nextInTopic(NONE, first)).map(Question::topic).hasValue(Topic.COMMUTE);
    assertThat(Interview.previousTopic(Topic.COMMUTE)).hasValue(Topic.JOB);
    assertThat(Interview.previousTopic(Topic.JOB)).isEmpty();
    assertThat(Interview.nextTopic(Topic.HOUSEHOLD)).isEmpty();
  }

  @Test
  void aFieldAnswerDoesNotAddAPage() {
    Map<String, String> values = new LinkedHashMap<>();
    Interview.questions(NONE).forEach(question -> values.put(question.id(), Interview.DONE));
    assertThat(Interview.steps(new Answers(values)))
        .containsExactly(new Step(FilingCheckForm.ID, null), new Step("main", null));
  }

  @Test
  void withoutAnyYesTheReturnNeedsOnlyTheFilingCheckAndTheMainForm() {
    assertThat(Interview.steps(NONE))
        .containsExactly(new Step(FilingCheckForm.ID, null), new Step("main", null));
  }

  @Test
  void eachYesAddsItsPageAndTheInterviewEndsWhenAllAreAnswered() {
    Map<String, String> values = new LinkedHashMap<>();
    values.put("employed", Interview.YES);
    values.put("householdServices", Interview.YES);
    Interview.questions(new Answers(values))
        .forEach(question -> values.putIfAbsent(question.id(), Interview.NO));
    Answers answers = new Answers(values);

    assertThat(Interview.isDone(answers)).isTrue();
    assertThat(Interview.steps(answers))
        .containsExactly(
            new Step(FilingCheckForm.ID, null),
            new Step("main", null),
            new Step(AnlageN.ID, null),
            new Step(null, ExpenseTopic.HOUSEHOLD_SERVICES));
  }

  @Test
  void aYesToAnotherEmployerAddsNoPage() {
    Answers answers =
        new Answers(Map.of("employed", Interview.YES, "anotherEmployer", Interview.YES));
    assertThat(Interview.steps(answers))
        .containsExactly(
            new Step(FilingCheckForm.ID, null), new Step("main", null), new Step(AnlageN.ID, null));
  }

  @Test
  void aStatutoryInsuranceAsksTheSocialInsuranceLinesOfEachCertificate() {
    Answers answers =
        new Answers(
            Map.of(
                "employed", Interview.YES,
                "anotherEmployer", Interview.YES,
                "employer2-anotherEmployer", Interview.NO,
                Interview.HEALTH_INSURANCE, Interview.STATUTORY));

    List<Question> insurance = Interview.questionsOf(answers, Topic.INSURANCE);
    assertThat(ids(insurance))
        .containsExactly(
            Interview.HEALTH_INSURANCE,
            "pensionEmployer",
            "pensionEmployee",
            "healthAllowanceStatutory",
            "careAllowance",
            "healthEmployee",
            "careEmployee",
            "unemploymentEmployee",
            "employer2-pensionEmployer",
            "employer2-pensionEmployee",
            "employer2-healthAllowanceStatutory",
            "employer2-careAllowance",
            "employer2-healthEmployee",
            "employer2-careEmployee",
            "employer2-unemploymentEmployee",
            "insurance",
            "medical");
    assertFieldsExist(insurance);
    Question health = Interview.find(answers, "employer2-healthEmployee").orElseThrow();
    assertThat(health.text()).startsWith("Arbeitgeber 2:").contains("Nr. 25");
    assertThat(health.hintId()).isEqualTo("healthEmployee");
  }

  @Test
  void aPrivateInsuranceAsksItsCertificateLinesAndThenTheCertificateOfTheInsurer() {
    Answers answers =
        new Answers(
            Map.of("employed", Interview.YES, Interview.HEALTH_INSURANCE, Interview.PRIVATE));

    List<Question> insurance = Interview.questionsOf(answers, Topic.INSURANCE);
    assertThat(ids(insurance))
        .containsExactly(
            Interview.HEALTH_INSURANCE,
            "pensionEmployer",
            "pensionEmployee",
            "healthAllowancePrivate",
            "unemploymentEmployee",
            "privateHealthBasic",
            "privateCare",
            "privateRefunds",
            "insurance",
            "medical");
    assertFieldsExist(insurance);
  }

  @Test
  void withoutEmploymentAPrivateInsuranceAsksOnlyTheInsurer() {
    Answers answers =
        new Answers(Map.of("employed", Interview.NO, Interview.HEALTH_INSURANCE, Interview.PRIVATE));
    assertThat(ids(Interview.questionsOf(answers, Topic.INSURANCE)))
        .containsExactly(
            Interview.HEALTH_INSURANCE,
            "privateHealthBasic",
            "privateCare",
            "privateRefunds",
            "insurance",
            "medical");
  }

  @Test
  void beforeTheAnswerToTheHealthInsuranceThereAreNoCertificateLines() {
    Answers answers = new Answers(Map.of("employed", Interview.YES));
    assertThat(ids(Interview.questionsOf(answers, Topic.INSURANCE)))
        .containsExactly(Interview.HEALTH_INSURANCE, "insurance", "medical");
    Question health = Interview.find(answers, Interview.HEALTH_INSURANCE).orElseThrow();
    assertThat(health.type()).isEqualTo(Type.CHOICE);
    assertThat(health.allows(Interview.STATUTORY)).isTrue();
    assertThat(health.allows(Interview.YES)).isFalse();
    assertThat(health.labelOf(Interview.PRIVATE)).isEqualTo("Privat");
  }

  @Test
  void eachAnswerToTheHealthInsuranceAddsAnlageVorsorgeaufwand() {
    for (String kind : List.of(Interview.STATUTORY, Interview.PRIVATE)) {
      assertThat(Interview.steps(new Answers(Map.of(Interview.HEALTH_INSURANCE, kind))))
          .containsExactly(
              new Step(FilingCheckForm.ID, null),
              new Step("main", null),
              new Step(AnlageVorsorgeaufwand.ID, null));
    }
  }

  private static void assertFieldsExist(List<Question> questions) {
    AnlageVorsorgeaufwand form = new AnlageVorsorgeaufwand();
    for (Question question : questions) {
      if (question.type() == Type.FIELD) {
        assertThat(question.formId()).isEqualTo(AnlageVorsorgeaufwand.ID);
        assertThat(form.field(question.id())).as(question.id()).isPresent();
      }
    }
  }

  private static List<String> ids(List<Question> questions) {
    return questions.stream().map(Question::id).toList();
  }
}
