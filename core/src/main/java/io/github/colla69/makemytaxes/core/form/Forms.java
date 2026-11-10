package io.github.colla69.makemytaxes.core.form;

import io.github.colla69.makemytaxes.core.TaxYear;
import java.util.List;
import java.util.Optional;

/** The form modules of a tax year. A year without a {@link YearDefinition} has none yet. */
public final class Forms {

  private Forms() {}

  public static List<FormModule> of(TaxYear taxYear) {
    return YearDefinition.of(taxYear).map(YearDefinition::forms).orElse(List.of());
  }

  public static Optional<FormModule> find(TaxYear taxYear, String formId) {
    return of(taxYear).stream().filter(form -> form.id().equals(formId)).findFirst();
  }
}
