package io.github.colla69.makemytaxes.core.form;

import java.util.Map;
import java.util.Optional;

/**
 * The values that the reader found on a certificate, as text in the formats of the UI. A field that
 * is missing here was not found.
 */
public record CertificateValues(Map<String, String> found) {

  public static final String NOT_FOUND = "nicht gefunden";

  public CertificateValues {
    found = Map.copyOf(found);
  }

  public Optional<String> value(String fieldId) {
    return Optional.ofNullable(found.get(fieldId));
  }

  /** The value of the field, or "nicht gefunden". */
  public String valueOrNotFound(String fieldId) {
    return value(fieldId).orElse(NOT_FOUND);
  }
}
