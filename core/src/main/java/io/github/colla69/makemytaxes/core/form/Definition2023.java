package io.github.colla69.makemytaxes.core.form;

import io.github.colla69.makemytaxes.core.year.YearPack;
import java.util.List;
import java.util.Map;

/**
 * The forms of 2023: ESt 1 A, Anlage N and Anlage Vorsorgeaufwand, with the sections of 2022.
 *
 * <p>The fields have no line of the form: the official forms of 2023 are not in {@code
 * law/forms/2023} yet, and a line is not guessed. Each form says so on its page.
 */
final class Definition2023 {

  private Definition2023() {}

  static YearDefinition of(YearPack pack) {
    WageTaxCertificate certificate = WageTaxCertificate2023.LAYOUT;
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
