package io.github.colla69.makemytaxes.core.form;

import io.github.colla69.makemytaxes.core.year.YearPack;
import java.util.List;
import java.util.Map;

/**
 * The forms of 2026, with the sections of 2022. The official forms and the certificate sample of
 * 2026 are not in the app yet, so no field has a line. The app does not guess the lines from an
 * earlier year.
 */
final class Definition2026 {

  private Definition2026() {}

  static YearDefinition of(YearPack pack) {
    WageTaxCertificate certificate = WageTaxCertificate2026.LAYOUT;
    return new YearDefinition(
        pack,
        certificate,
        Map.of(
            MainForm.ID,
            new FormLayout(
                List.of(Sections.GENERAL, Sections.BANK, Sections.BENEFITS, Sections.NOTES),
                Map.of(),
                certificate),
            AnlageN.ID,
            new FormLayout(List.of(Sections.EMPLOYER, Sections.WORK), Map.of(), certificate),
            AnlageVorsorgeaufwand.ID,
            new FormLayout(
                List.of(Sections.SOCIAL_INSURANCE, Sections.PRIVATE_HEALTH),
                Map.of(),
                certificate)),
        false);
  }
}
