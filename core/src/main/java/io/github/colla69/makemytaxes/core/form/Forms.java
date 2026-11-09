package io.github.colla69.makemytaxes.core.form;

import io.github.colla69.makemytaxes.core.TaxYear;
import io.github.colla69.makemytaxes.core.year.YearPack;
import java.util.List;
import java.util.Optional;

/** The form modules of a tax year. A year without a year pack has none yet. */
public final class Forms {

  private Forms() {}

  public static List<FormModule> of(TaxYear taxYear) {
    return YearPack.of(taxYear)
        .<List<FormModule>>map(
            pack ->
                List.of(
                    new FilingCheckForm(pack),
                    new MainForm(),
                    new AnlageN(pack),
                    new AnlageVorsorgeaufwand(),
                    new HouseholdServicesForm()))
        .orElse(List.of());
  }

  public static Optional<FormModule> find(TaxYear taxYear, String formId) {
    return of(taxYear).stream().filter(form -> form.id().equals(formId)).findFirst();
  }
}
