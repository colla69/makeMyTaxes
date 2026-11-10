package io.github.colla69.makemytaxes.core.form;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * The layout of the printed Lohnsteuerbescheinigung of one year: the numbered line of each field,
 * and the reader of its text. A field ID here is the ID without the prefix of the employer.
 *
 * @param lines the line of each field with a numbered line, such as "3" or "22a/b"
 * @param withoutLine the fields that the certificate shows outside its numbered lines
 * @param reader finds the values of the fields in the text of a certificate of this year
 */
public record WageTaxCertificate(
    Map<String, String> lines,
    Set<String> withoutLine,
    Function<CertificateText, CertificateValues> reader) {

  /** The text of the line, for example "Zeile 3", or none for a field without a line. */
  public Optional<String> lineOf(String fieldId) {
    return Optional.ofNullable(lines.get(fieldId)).map(line -> "Zeile " + line);
  }

  /** The IDs of all fields whose value comes from the certificate. */
  public Set<String> fieldIds() {
    Set<String> ids = new TreeSet<>(lines.keySet());
    ids.addAll(withoutLine);
    return ids;
  }

  /** The label of a field: the line on the certificate, if it has one, then the text. */
  String label(String fieldId, String text) {
    return lineOf(fieldId).map(line -> line + " " + text).orElse(text);
  }

  /** The line of a field that has one. */
  String line(String fieldId) {
    return lineOf(fieldId).orElseThrow();
  }

  public CertificateValues read(CertificateText text) {
    return reader.apply(text);
  }
}
