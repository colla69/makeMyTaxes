package io.github.colla69.makemytaxes.core.form;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.colla69.makemytaxes.core.TaxYear;
import io.github.colla69.makemytaxes.core.year.YearPack;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** A form that reuses a section also gets the rules of the section. */
class FormLayoutTest {

  private final YearPack pack = YearPack.of(new TaxYear(2022)).orElseThrow();

  private final FormLayout layout =
      new FormLayout(
          List.of(Sections.EMPLOYER, Sections.WORK), Map.of(), WageTaxCertificate2022.LAYOUT);

  @Test
  void theEmployerSectionChecksThePeriodOfEachEmployer() {
    Answers answers =
        new Answers(Map.of("employedFrom", "01.02.2021", "employer2-employedTo", "01.02.2023"));

    assertThat(layout.check(answers, pack))
        .containsExactly(
            "Arbeitgeber 1: Die Dauer des Dienstverhältnisses muss in 2022 liegen.",
            "Arbeitgeber 2: Die Dauer des Dienstverhältnisses muss in 2022 liegen.");
  }

  @Test
  void theWorkSectionChecksTheDaysOnce() {
    Answers answers =
        new Answers(
            Map.of(
                "commuteDays", "300",
                "homeOfficeDays", "100",
                "commuteTransport", "CAR",
                "employer2-employerName", "Beispiel GmbH"));

    assertThat(layout.check(answers, pack))
        .containsExactly(
            "Tage mit Wegen zur Arbeit und Homeoffice-Tage ergeben zusammen 400, aber 2022 hat nur"
                + " 365 Tage.");
  }

  @Test
  void aLayoutWithoutTheseSectionsHasNoMessages() {
    FormLayout other =
        new FormLayout(List.of(Sections.PRIVATE_HEALTH), Map.of(), WageTaxCertificate2022.LAYOUT);

    assertThat(other.check(new Answers(Map.of("employedFrom", "01.02.2021")), pack)).isEmpty();
  }
}
