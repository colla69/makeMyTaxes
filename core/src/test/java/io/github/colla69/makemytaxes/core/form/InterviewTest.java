package io.github.colla69.makemytaxes.core.form;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.colla69.makemytaxes.core.TaxYear;
import io.github.colla69.makemytaxes.core.expense.ExpenseTopic;
import io.github.colla69.makemytaxes.core.form.Interview.Question;
import io.github.colla69.makemytaxes.core.form.Interview.Step;
import io.github.colla69.makemytaxes.core.form.Interview.Topic;
import io.github.colla69.makemytaxes.core.form.Interview.Type;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InterviewTest {

  private static final Answers NONE = new Answers(Map.of());
  private static final TaxYear YEAR = new TaxYear(2022);
  private static final Interview INTERVIEW = YearDefinition.of(YEAR).orElseThrow().interview();

  private static FormModule form(String formId) {
    return Forms.find(YEAR, formId).orElseThrow();
  }

  @Test
  void theQuestionsComeOneAtATimeInOrder() {
    assertThat(INTERVIEW.next(NONE))
        .map(Interview.Question::id)
        .hasValue(Interview.PERSONAL_DETAILS);
    assertThat(
            INTERVIEW.next(
                new Answers(
                    Map.of(Interview.PERSONAL_DETAILS, Interview.DONE, "employed", Interview.NO))))
        .map(Interview.Question::id)
        .hasValue("occupation");
  }

  @Test
  void theTopicsComeInTheOrderJobCommuteInsuranceHousehold() {
    assertThat(INTERVIEW.questions(NONE).stream().map(Question::topic).distinct())
        .containsExactly(Topic.JOB, Topic.COMMUTE, Topic.INSURANCE, Topic.HOUSEHOLD);
    for (Topic topic : Topic.values()) {
      assertThat(INTERVIEW.questionsOf(NONE, topic)).isNotEmpty();
    }
  }

  @Test
  void aFieldQuestionNamesAFieldOfItsForm() {
    Question occupation = INTERVIEW.find(NONE, "occupation").orElseThrow();
    assertThat(occupation.formId()).isEqualTo(MainForm.ID);
    assertThat(form(MainForm.ID).fields()).anyMatch(field -> field.id().equals("occupation"));
    assertThat(INTERVIEW.find(NONE, "commuteKm").orElseThrow().formId()).isEqualTo(AnlageN.ID);
  }

  @Test
  void anEmployeeIsAskedTheLinesOfTheCertificateAndThenForAnotherEmployer() {
    Answers employed = new Answers(Map.of("employed", Interview.YES));

    assertThat(ids(INTERVIEW.questionsOf(employed, Topic.JOB)))
        .containsExactly(
            Interview.PERSONAL_DETAILS,
            "employed",
            "wageCertificate",
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
    FormModule anlageN = form(AnlageN.ID);
    for (Question question : INTERVIEW.questionsOf(employed, Topic.JOB)) {
      if (question.type() == Type.FIELD && question.formId().equals(AnlageN.ID)) {
        assertThat(anlageN.field(question.id())).isPresent();
      }
    }
    assertThat(INTERVIEW.find(employed, "grossWage").orElseThrow().text()).contains("Zeile 3");
  }

  @Test
  void aYesToTheCertificateAsksForItsDocumentBeforeTheLines() {
    Answers answers =
        new Answers(Map.of("employed", Interview.YES, "wageCertificate", Interview.YES));

    List<String> ids = ids(INTERVIEW.questionsOf(answers, Topic.JOB));
    assertThat(ids.subList(ids.indexOf("wageCertificate"), ids.indexOf("wageCertificate") + 3))
        .containsExactly("wageCertificate", "wageCertificateDocument", "employerName");
    assertThat(INTERVIEW.find(answers, "wageCertificate").orElseThrow().text())
        .contains("Hast du deine Lohnsteuerbescheinigung?");
    assertThat(INTERVIEW.find(answers, "wageCertificateDocument").orElseThrow().type())
        .isEqualTo(Type.DOCUMENT);
  }

  @Test
  void aFieldOnTheCertificateComesFromTheDocumentOfItsOwnEmployer() {
    Answers answers =
        new Answers(
            Map.of(
                "wageCertificate", Interview.YES,
                "wageCertificateDocument", "7",
                "employer2-wageCertificate", Interview.YES,
                "employer2-wageCertificateDocument", "9"));

    assertThat(Interview.certificateDocument(answers, "grossWage")).hasValue(7);
    assertThat(Interview.certificateDocument(answers, "healthEmployee")).hasValue(7);
    assertThat(Interview.certificateDocument(answers, "employerName")).hasValue(7);
    assertThat(Interview.certificateDocument(answers, "employer2-grossWage")).hasValue(9);
    assertThat(Interview.certificateDocument(answers, "commuteKm")).isEmpty();
    assertThat(Interview.certificateDocument(answers, "employer3-grossWage")).isEmpty();
  }

  @Test
  void aNoToTheCertificateIgnoresADocumentFromAnEarlierAnswer() {
    Answers answers =
        new Answers(
            Map.of(
                "employed", Interview.YES,
                "wageCertificate", Interview.NO,
                "wageCertificateDocument", "7"));

    assertThat(Interview.certificateDocument(answers, "grossWage")).isEmpty();
    assertThat(ids(INTERVIEW.questions(answers))).doesNotContain("wageCertificateDocument");
  }

  @Test
  void aYesToAnotherEmployerAsksTheSameLinesForTheNextEmployerAndANoEndsTheEmployers() {
    Answers twoEmployers =
        new Answers(
            Map.of(
                "employed", Interview.YES,
                "anotherEmployer", Interview.YES,
                "employer2-anotherEmployer", Interview.NO));

    List<String> ids = ids(INTERVIEW.questionsOf(twoEmployers, Topic.JOB));
    assertThat(ids).contains("employer2-employerName", "employer2-grossWage");
    assertThat(ids).doesNotContain("employer3-employerName");
    assertThat(ids.subList(ids.indexOf("employer2-anotherEmployer"), ids.size()))
        .containsExactly("employer2-anotherEmployer", "occupation");
    assertThat(INTERVIEW.find(twoEmployers, "employer2-grossWage").orElseThrow().text())
        .startsWith("Arbeitgeber 2:");
    assertThat(INTERVIEW.find(twoEmployers, "employer2-grossWage").orElseThrow().hintId())
        .isEqualTo("grossWage");
    // The next question after a yes is the first one of the next employer.
    Question another = INTERVIEW.find(twoEmployers, "anotherEmployer").orElseThrow();
    assertThat(INTERVIEW.nextInTopic(twoEmployers, another))
        .map(Question::id)
        .hasValue("employer2-wageCertificate");
  }

  @Test
  void withoutEmploymentThereAreNoEmployerQuestions() {
    Answers notEmployed = new Answers(Map.of("employed", Interview.NO));
    assertThat(ids(INTERVIEW.questions(notEmployed))).doesNotContain("employerName", "grossWage");
  }

  @Test
  void theCommuteTopicAsksTheAnlageNFieldsWithTheTransportBeforeTheDays() {
    assertThat(INTERVIEW.questionsOf(NONE, Topic.COMMUTE).stream().map(Question::id))
        .containsExactly(
            "commuteKm",
            "commuteTransport",
            "commuteDays",
            "homeOfficeDays",
            "workEquipment",
            "workTraining");
    for (String id : new String[] {"commuteTransport", "commuteDays", "homeOfficeDays"}) {
      assertThat(INTERVIEW.find(NONE, id).orElseThrow().formId()).isEqualTo(AnlageN.ID);
      assertThat(form(AnlageN.ID).fields()).anyMatch(field -> field.id().equals(id));
    }
  }

  @Test
  void backAndNextStayInTheTopicAndThenMoveToTheNeighbourTopic() {
    Question first = INTERVIEW.questionsOf(NONE, Topic.COMMUTE).getFirst();
    assertThat(INTERVIEW.previousInTopic(NONE, first)).isEmpty();
    assertThat(INTERVIEW.nextInTopic(NONE, first)).map(Question::topic).hasValue(Topic.COMMUTE);
    assertThat(Interview.previousTopic(Topic.COMMUTE)).hasValue(Topic.JOB);
    assertThat(Interview.previousTopic(Topic.JOB)).isEmpty();
    assertThat(Interview.nextTopic(Topic.HOUSEHOLD)).isEmpty();
  }

  @Test
  void aFieldAnswerDoesNotAddAPage() {
    Map<String, String> values = new LinkedHashMap<>();
    INTERVIEW.questions(NONE).forEach(question -> values.put(question.id(), Interview.DONE));
    assertThat(INTERVIEW.steps(new Answers(values)))
        .containsExactly(new Step(FilingCheckForm.ID, null), new Step("main", null));
  }

  @Test
  void withoutAnyYesTheReturnNeedsOnlyTheFilingCheckAndTheMainForm() {
    assertThat(INTERVIEW.steps(NONE))
        .containsExactly(new Step(FilingCheckForm.ID, null), new Step("main", null));
  }

  @Test
  void eachYesAddsItsPageAndTheInterviewEndsWhenAllAreAnswered() {
    Map<String, String> values = new LinkedHashMap<>();
    values.put("employed", Interview.YES);
    values.put("medical", Interview.YES);
    values.put("cleaning", Interview.YES);
    values.put("craftsmen", Interview.YES);
    INTERVIEW.questions(new Answers(values))
        .forEach(question -> values.putIfAbsent(question.id(), Interview.NO));
    Answers answers = new Answers(values);

    assertThat(INTERVIEW.isDone(answers)).isTrue();
    assertThat(INTERVIEW.steps(answers))
        .containsExactly(
            new Step(FilingCheckForm.ID, null),
            new Step("main", null),
            new Step(AnlageN.ID, null),
            new Step(null, ExpenseTopic.MEDICAL),
            new Step(HouseholdServicesForm.ID, null));
  }

  @Test
  void theHouseholdAsksEachCategoryAndAfterAYesTheInvoiceTheAmountTheLabourAndThePayment() {
    assertThat(ids(INTERVIEW.questionsOf(NONE, Topic.HOUSEHOLD)))
        .containsExactly(
            "cleaning", "gardening", "craftsmen", "ancillaryCosts", "childcare", "donations");

    Answers cleaning = new Answers(Map.of("cleaning", Interview.YES));
    List<String> ids = ids(INTERVIEW.questionsOf(cleaning, Topic.HOUSEHOLD));
    assertThat(ids.subList(0, 7))
        .containsExactly(
            "cleaning",
            "cleaning1-invoice",
            "cleaning1-payment",
            "cleaning1-paidAmount",
            "cleaning1-labourCost",
            "cleaning1-another",
            "gardening");
    HouseholdServicesForm form = new HouseholdServicesForm();
    for (String id : ids.subList(1, 5)) {
      Question question = INTERVIEW.find(cleaning, id).orElseThrow();
      assertThat(question.formId()).isEqualTo(HouseholdServicesForm.ID);
      assertThat(form.field(id)).isPresent();
      assertThat(question.text()).startsWith("Reinigung 1:");
    }
  }

  @Test
  void theAncillaryCostStatementAsksTheLabourForServicesAndForCraftsmen() {
    Answers statement = new Answers(Map.of("ancillaryCosts", Interview.YES));
    assertThat(ids(INTERVIEW.questionsOf(statement, Topic.HOUSEHOLD)))
        .contains(
            "ancillaryCosts1-statement",
            "ancillaryCosts1-paidAmount",
            "ancillaryCosts1-labourServices",
            "ancillaryCosts1-labourCraftsmen",
            "ancillaryCosts1-another");
    assertThat(ids(INTERVIEW.questionsOf(statement, Topic.HOUSEHOLD)))
        .doesNotContain("ancillaryCosts1-payment");
  }

  @Test
  void aYesToAnotherInvoiceAsksTheNextInvoiceOfTheSameCategory() {
    Answers two =
        new Answers(Map.of("craftsmen", Interview.YES, "craftsmen1-another", Interview.YES));
    List<String> ids = ids(INTERVIEW.questionsOf(two, Topic.HOUSEHOLD));
    assertThat(ids).contains("craftsmen2-invoice", "craftsmen2-another");
    assertThat(ids).doesNotContain("craftsmen3-invoice");
    assertThat(INTERVIEW.find(two, "craftsmen2-paidAmount").orElseThrow().hintId())
        .isEqualTo("paidAmount");
  }

  @Test
  void aYesToAnotherEmployerAddsNoPage() {
    Answers answers =
        new Answers(Map.of("employed", Interview.YES, "anotherEmployer", Interview.YES));
    assertThat(INTERVIEW.steps(answers))
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

    List<Question> insurance = INTERVIEW.questionsOf(answers, Topic.INSURANCE);
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
    Question health = INTERVIEW.find(answers, "employer2-healthEmployee").orElseThrow();
    assertThat(health.text()).startsWith("Arbeitgeber 2:").contains("Zeile 25");
    assertThat(health.hintId()).isEqualTo("healthEmployee");
  }

  @Test
  void aPrivateInsuranceAsksItsCertificateLinesAndThenTheCertificateOfTheInsurer() {
    Answers answers =
        new Answers(
            Map.of("employed", Interview.YES, Interview.HEALTH_INSURANCE, Interview.PRIVATE));

    List<Question> insurance = INTERVIEW.questionsOf(answers, Topic.INSURANCE);
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
    assertThat(ids(INTERVIEW.questionsOf(answers, Topic.INSURANCE)))
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
    assertThat(ids(INTERVIEW.questionsOf(answers, Topic.INSURANCE)))
        .containsExactly(Interview.HEALTH_INSURANCE, "insurance", "medical");
    Question health = INTERVIEW.find(answers, Interview.HEALTH_INSURANCE).orElseThrow();
    assertThat(health.type()).isEqualTo(Type.CHOICE);
    assertThat(health.allows(Interview.STATUTORY)).isTrue();
    assertThat(health.allows(Interview.YES)).isFalse();
    assertThat(health.labelOf(Interview.PRIVATE)).isEqualTo("Privat");
  }

  @Test
  void eachAnswerToTheHealthInsuranceAddsAnlageVorsorgeaufwand() {
    for (String kind : List.of(Interview.STATUTORY, Interview.PRIVATE)) {
      assertThat(INTERVIEW.steps(new Answers(Map.of(Interview.HEALTH_INSURANCE, kind))))
          .containsExactly(
              new Step(FilingCheckForm.ID, null),
              new Step("main", null),
              new Step(AnlageVorsorgeaufwand.ID, null));
    }
  }

  private static void assertFieldsExist(List<Question> questions) {
    FormModule form = form(AnlageVorsorgeaufwand.ID);
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
