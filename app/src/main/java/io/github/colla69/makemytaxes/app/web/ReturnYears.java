package io.github.colla69.makemytaxes.app.web;

import io.github.colla69.makemytaxes.app.storage.HouseholdStore;
import io.github.colla69.makemytaxes.core.TaxYear;
import io.github.colla69.makemytaxes.core.form.FormModule;
import io.github.colla69.makemytaxes.core.form.Forms;
import io.github.colla69.makemytaxes.core.form.YearDefinition;
import io.github.colla69.makemytaxes.core.year.YearPack;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Resolves a return to its tax year, its year definition and its forms. A return or a year that
 * the app does not know gives the answer 404.
 */
@Component
class ReturnYears {

  private final HouseholdStore household;

  ReturnYears(HouseholdStore household) {
    this.household = household;
  }

  TaxYear taxYearOf(long returnId) {
    return household.taxYearOf(returnId).orElseThrow(ReturnYears::notFound);
  }

  /** A year without a definition has no forms, so a page could point nowhere. */
  YearDefinition definitionOf(long returnId) {
    return YearDefinition.of(taxYearOf(returnId)).orElseThrow(ReturnYears::notFound);
  }

  FormModule formOf(long returnId, String formId) {
    return Forms.find(taxYearOf(returnId), formId).orElseThrow(ReturnYears::notFound);
  }

  static YearPack packOf(TaxYear taxYear) {
    return YearPack.of(taxYear).orElseThrow(ReturnYears::notFound);
  }

  static ResponseStatusException notFound() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND);
  }
}
