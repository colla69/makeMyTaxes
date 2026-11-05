package io.github.colla69.makemytaxes.core.filing;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.colla69.makemytaxes.core.TaxYear;
import io.github.colla69.makemytaxes.core.form.Answers;
import io.github.colla69.makemytaxes.core.form.Field;
import io.github.colla69.makemytaxes.core.form.FilingCheckForm;
import io.github.colla69.makemytaxes.core.form.Kind;
import io.github.colla69.makemytaxes.core.year.YearPack;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class FilingCheckTest {

  private final YearPack pack = YearPack.of(new TaxYear(2022)).orElseThrow();

  private Map<String, String> allNo() {
    Map<String, String> answers = new HashMap<>();
    for (Field field : new FilingCheckForm(pack).fields()) {
      answers.put(field.id(), Kind.NO);
    }
    return answers;
  }

  @Test
  @DisplayName("§ 46(2) no. 8 EStG: without a duty, a return is a voluntary application")
  void allNoIsVoluntary() {
    var result = FilingCheck.of(pack, new Answers(allNo()));

    assertThat(result.status()).isEqualTo(FilingCheck.Status.VOLUNTARY);
  }

  @ParameterizedTest(name = "{1}")
  @CsvSource({
    "otherIncome, § 46(2) no. 1 EStG",
    "benefits, § 46(2) no. 1 EStG",
    "severalEmployers, § 46(2) no. 2 EStG",
    "healthAllowance, § 46(2) no. 3 EStG",
    "wageTaxAllowance, § 46(2) no. 4 EStG",
    "specialPayment, § 46(2) no. 5 EStG",
    "certificateS, § 46(2) no. 5a EStG",
    "officeRequest, § 149(1) AO"
  })
  void oneYesMakesTheReturnMandatoryAndNamesTheSource(String fieldId, String source) {
    Map<String, String> answers = allNo();
    answers.put(fieldId, Kind.YES);

    var result = FilingCheck.of(pack, new Answers(answers));

    assertThat(result.status()).isEqualTo(FilingCheck.Status.MANDATORY);
    assertThat(result.yes()).singleElement().extracting(FilingCheck.Reason::source).isEqualTo(source);
  }

  @Test
  void notSureLeavesTheResultOpen() {
    Map<String, String> answers = allNo();
    answers.put("healthAllowance", Kind.UNSURE);

    var result = FilingCheck.of(pack, new Answers(answers));

    assertThat(result.status()).isEqualTo(FilingCheck.Status.OPEN);
    assertThat(result.open()).hasSize(1);
  }

  @Test
  void aMissingAnswerLeavesTheResultOpen() {
    assertThat(FilingCheck.of(pack, new Answers(Map.of())).status())
        .isEqualTo(FilingCheck.Status.OPEN);
  }

  @Test
  void aYesWinsOverNotSure() {
    Map<String, String> answers = allNo();
    answers.put("healthAllowance", Kind.UNSURE);
    answers.put("benefits", Kind.YES);

    assertThat(FilingCheck.of(pack, new Answers(answers)).status())
        .isEqualTo(FilingCheck.Status.MANDATORY);
  }
}
