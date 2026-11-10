package io.github.colla69.makemytaxes.core.form;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.colla69.makemytaxes.core.TaxYear;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AnlageVorsorgeaufwandTest {

  private static final Answers BOTH_KINDS =
      new Answers(
          Map.of(
              "pensionEmployee", "1.000,00",
              "healthEmployee", "800,00",
              "employer2-careEmployee", "100,00",
              "employer2-healthAllowancePrivate", "300,00",
              "privateHealthBasic", "2.000,00"));

  @Test
  void aPrivateInsuranceDropsTheStatutoryLinesOfEachEmployer() {
    assertThat(AnlageVorsorgeaufwand.forHealthInsurance(BOTH_KINDS, true).values())
        .containsOnlyKeys("pensionEmployee", "employer2-healthAllowancePrivate", "privateHealthBasic");
  }

  @Test
  void aStatutoryInsuranceDropsThePrivateLinesAndTheInsurer() {
    assertThat(AnlageVorsorgeaufwand.forHealthInsurance(BOTH_KINDS, false).values())
        .containsOnlyKeys("pensionEmployee", "healthEmployee", "employer2-careEmployee");
  }

  @Test
  void eachEmployerWithAnAnswerHasItsOwnBlock() {
    FormModule form =
        Forms.find(new TaxYear(2022), AnlageVorsorgeaufwand.ID).orElseThrow();
    assertThat(form.fields(BOTH_KINDS).stream().map(Field::section).distinct())
        .containsExactly(
            "Arbeitgeber 1", "Arbeitgeber 2", "Private Kranken- und Pflegeversicherung");
    assertThat(form.field("employer3-healthEmployee")).isPresent();
    assertThat(form.missing(BOTH_KINDS)).isEmpty();
  }
}
