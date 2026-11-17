package io.github.colla69.makemytaxes.core.form;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;

/**
 * Where the value of an interview answer comes from: an uploaded document or the user's own
 * statement. The keys live among the interview answers, so their texts must not change.
 */
public final class CertificateEvidence {

  /**
   * The interview answer of a field question whose value the user changed after a document
   * prefilled it. The value is then the user's own statement, not the document's.
   */
  public static final String OWN = "OWN";

  /** The key of the step "Dokumente hochladen". It has a value once the user leaves that step. */
  public static final String DOCUMENTS_STEP = "documentsStep";

  /** The key of the IDs of the questions that the uploaded documents answered, comma-separated. */
  public static final String PREFILLED = "prefilled";

  private CertificateEvidence() {}

  /**
   * The document that a field comes from: the certificate that the user named for the employer of
   * the field. There is none for a field that is not on the certificate, or after a no.
   */
  public static OptionalLong certificateDocument(Answers answers, String fieldId) {
    int employer = AnlageN.employerOf(fieldId);
    String document =
        answers.text(AnlageN.employerFieldId(employer, Interview.WAGE_CERTIFICATE_DOCUMENT));
    boolean fromCertificate =
        Sections.isOnCertificate(fieldId.substring(fieldId.indexOf('-') + 1))
            && Interview.YES.equals(
                answers.text(AnlageN.employerFieldId(employer, Interview.WAGE_CERTIFICATE)))
            && !document.isEmpty()
            && !OWN.equals(answers.text(fieldId));
    return fromCertificate ? OptionalLong.of(Long.parseLong(document)) : OptionalLong.empty();
  }

  /**
   * The interview answers that one uploaded certificate for each employer gives: the user is
   * employed, and employer {@code n} has the {@code n}-th certificate. The question for one more
   * employer after the last certificate stays open.
   */
  public static Map<String, String> certificateAnswers(List<Long> certificates) {
    Map<String, String> values = new LinkedHashMap<>();
    if (certificates.isEmpty()) {
      return values;
    }
    values.put(Interview.EMPLOYED, Interview.YES);
    for (int employer = 1; employer <= certificates.size(); employer++) {
      values.put(AnlageN.employerFieldId(employer, Interview.WAGE_CERTIFICATE), Interview.YES);
      values.put(
          AnlageN.employerFieldId(employer, Interview.WAGE_CERTIFICATE_DOCUMENT),
          Long.toString(certificates.get(employer - 1)));
      if (employer < certificates.size()) {
        values.put(Interview.anotherEmployerId(employer), Interview.YES);
      }
    }
    return values;
  }

  /** The IDs of the questions that the uploaded documents answered. */
  public static Set<String> prefilled(Answers answers) {
    String ids = answers.text(PREFILLED);
    return ids.isEmpty() ? Set.of() : new LinkedHashSet<>(List.of(ids.split(",")));
  }

  /** The text of {@link #PREFILLED} for these question IDs. */
  public static String prefilledText(Collection<String> ids) {
    return String.join(",", ids);
  }
}
