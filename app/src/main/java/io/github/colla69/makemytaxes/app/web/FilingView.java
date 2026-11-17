package io.github.colla69.makemytaxes.app.web;

import io.github.colla69.makemytaxes.core.GermanFormats;
import io.github.colla69.makemytaxes.core.filing.FilingCheck;
import java.time.LocalDate;
import java.util.List;

/** The result of the filing check, as text for the form of the check and for the review. */
record FilingView(
    String status, List<FilingCheck.Reason> yes, List<FilingCheck.Reason> open,
    String mandatoryDeadline, boolean mandatoryDeadlinePassed, String voluntaryDeadline) {

  static FilingView of(FilingCheck.Result result) {
    return new FilingView(
        result.status().name(),
        result.yes(),
        result.open(),
        GermanFormats.format(result.mandatoryDeadline()),
        LocalDate.now().isAfter(result.mandatoryDeadline()),
        GermanFormats.format(result.voluntaryDeadline()));
  }
}
