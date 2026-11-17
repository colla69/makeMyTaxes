package io.github.colla69.makemytaxes.core.form;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The values that the reader found on a certificate, as text in the formats of the UI. A field in
 * {@code empty} has its line printed on the certificate, with no amount on it. A field that is in
 * neither was not found.
 */
public record CertificateValues(Map<String, String> found, Set<String> empty) {

  public static final String NOT_FOUND = "nicht gefunden";
  public static final String EMPTY = "leer auf der Bescheinigung";

  public CertificateValues {
    found = Map.copyOf(found);
    empty = Set.copyOf(empty);
  }

  public CertificateValues(Map<String, String> found) {
    this(found, Set.of());
  }

  public Optional<String> value(String fieldId) {
    return Optional.ofNullable(found.get(fieldId));
  }

  /**
   * The value of the field, or "leer auf der Bescheinigung", or "nicht gefunden". The text for an
   * empty line is no amount: the app never fills a field that the certificate leaves empty.
   */
  public String readText(String fieldId) {
    if (empty.contains(fieldId)) {
      return EMPTY;
    }
    return value(fieldId).orElse(NOT_FOUND);
  }
}
