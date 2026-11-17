package io.github.colla69.makemytaxes.core.form;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CertificateEvidenceTest {

  @Test
  void aFieldOnTheCertificateComesFromTheDocumentOfItsOwnEmployer() {
    Answers answers =
        new Answers(
            Map.of(
                "wageCertificate", Interview.YES,
                "wageCertificateDocument", "7",
                "employer2-wageCertificate", Interview.YES,
                "employer2-wageCertificateDocument", "9"));

    assertThat(CertificateEvidence.certificateDocument(answers, "grossWage")).hasValue(7);
    assertThat(CertificateEvidence.certificateDocument(answers, "healthEmployee")).hasValue(7);
    assertThat(CertificateEvidence.certificateDocument(answers, "employerName")).hasValue(7);
    assertThat(CertificateEvidence.certificateDocument(answers, "employer2-grossWage"))
        .hasValue(9);
    assertThat(CertificateEvidence.certificateDocument(answers, "commuteKm")).isEmpty();
    assertThat(CertificateEvidence.certificateDocument(answers, "employer3-grossWage"))
        .isEmpty();
  }

  @Test
  void twoUploadedCertificatesAnswerTheQuestionsOfTwoEmployersAndLeaveAThirdOneOpen() {
    Answers answers = new Answers(CertificateEvidence.certificateAnswers(List.of(7L, 9L)));

    assertThat(CertificateEvidence.certificateDocument(answers, "grossWage")).hasValue(7);
    assertThat(CertificateEvidence.certificateDocument(answers, "employer2-grossWage"))
        .hasValue(9);
    assertThat(answers.text("anotherEmployer")).isEqualTo(Interview.YES);
    assertThat(answers.has("employer2-anotherEmployer")).isFalse();
    assertThat(CertificateEvidence.certificateAnswers(List.of())).isEmpty();
  }

  @Test
  void aPrefilledValueThatTheUserChangedIsTheUsersOwnStatement() {
    Map<String, String> values =
        new LinkedHashMap<>(CertificateEvidence.certificateAnswers(List.of(7L)));
    values.put("grossWage", CertificateEvidence.OWN);
    values.put(
        CertificateEvidence.PREFILLED,
        CertificateEvidence.prefilledText(List.of("employed", "wageTax")));
    Answers answers = new Answers(values);

    assertThat(CertificateEvidence.certificateDocument(answers, "grossWage")).isEmpty();
    assertThat(CertificateEvidence.certificateDocument(answers, "wageTax")).hasValue(7);
    assertThat(CertificateEvidence.prefilled(answers)).containsExactly("employed", "wageTax");
    assertThat(CertificateEvidence.prefilled(new Answers(Map.of()))).isEmpty();
  }

  @Test
  void aNoToTheCertificateIgnoresADocumentFromAnEarlierAnswer() {
    Answers answers =
        new Answers(
            Map.of(
                "employed", Interview.YES,
                "wageCertificate", Interview.NO,
                "wageCertificateDocument", "7"));

    assertThat(CertificateEvidence.certificateDocument(answers, "grossWage")).isEmpty();
  }

  @Test
  void theStoredKeysKeepTheirTexts() {
    assertThat(CertificateEvidence.OWN).isEqualTo("OWN");
    assertThat(CertificateEvidence.PREFILLED).isEqualTo("prefilled");
    assertThat(CertificateEvidence.DOCUMENTS_STEP).isEqualTo("documentsStep");
  }
}
