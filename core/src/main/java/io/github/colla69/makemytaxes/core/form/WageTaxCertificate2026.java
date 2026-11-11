package io.github.colla69.makemytaxes.core.form;

import java.util.Map;

/**
 * The Lohnsteuerbescheinigung for 2026, with the field IDs of 2025 and without line numbers. The app
 * does not hold the official sample of 2026 yet, so it shows no line and reads no value from a
 * certificate. A later change adds the lines and a reader from the official sample.
 */
public final class WageTaxCertificate2026 {

  public static final WageTaxCertificate LAYOUT =
      new WageTaxCertificate(
          Map.of(),
          WageTaxCertificate2025.LAYOUT.fieldIds(),
          text -> new CertificateValues(Map.of()));

  private WageTaxCertificate2026() {}
}
