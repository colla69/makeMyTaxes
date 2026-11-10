package io.github.colla69.makemytaxes.core.form;

import io.github.colla69.makemytaxes.core.TaxYear;
import io.github.colla69.makemytaxes.core.year.YearPack;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * The forms of one tax year: the sections of each form, the line of each field and the layout of
 * the Lohnsteuerbescheinigung. The year pack holds the legal values. A year without a definition
 * has no forms yet.
 *
 * @param formLinesKnown false if the official forms of the year are not checked yet. Then the
 *     fields have no line of the form, and each form with a layout says so.
 */
public record YearDefinition(
    YearPack pack,
    WageTaxCertificate certificate,
    Map<String, FormLayout> layouts,
    boolean formLinesKnown) {

  private static final Map<Integer, Function<YearPack, YearDefinition>> DEFINITIONS =
      Map.of(2022, Definition2022::of, 2023, Definition2023::of);

  public static Optional<YearDefinition> of(TaxYear taxYear) {
    return Optional.ofNullable(DEFINITIONS.get(taxYear.value()))
        .flatMap(definition -> YearPack.of(taxYear).map(definition));
  }

  /** The form modules in the order of the tabs. */
  public List<FormModule> forms() {
    return List.of(
        new FilingCheckForm(pack),
        new MainForm(layouts.get(MainForm.ID)),
        new AnlageN(pack, layouts.get(AnlageN.ID)),
        new AnlageVorsorgeaufwand(layouts.get(AnlageVorsorgeaufwand.ID)),
        new HouseholdServicesForm());
  }

  /** True if this form has fields with lines of the form, but the lines are not known yet. */
  public boolean lacksFormLines(String formId) {
    return !formLinesKnown && layouts.containsKey(formId);
  }

  public Interview interview() {
    return new Interview(certificate);
  }
}
