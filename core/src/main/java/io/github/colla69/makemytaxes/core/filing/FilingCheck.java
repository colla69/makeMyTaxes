package io.github.colla69.makemytaxes.core.filing;

import io.github.colla69.makemytaxes.core.form.Answers;
import io.github.colla69.makemytaxes.core.form.Field;
import io.github.colla69.makemytaxes.core.form.FilingCheckForm;
import io.github.colla69.makemytaxes.core.form.Kind;
import io.github.colla69.makemytaxes.core.year.YearPack;
import java.time.LocalDate;
import java.util.List;

/**
 * Decides whether a person with a wage must file. One "yes" makes a return mandatory, and without
 * one a return is voluntary under § 46(2) no. 8 EStG. A question without a clear answer leaves the
 * result open, because a guess can hide a duty.
 */
public final class FilingCheck {

  public enum Status {
    MANDATORY,
    VOLUNTARY,
    OPEN
  }

  public record Reason(String question, String source) {}

  public record Result(
      Status status,
      List<Reason> yes,
      List<Reason> open,
      LocalDate mandatoryDeadline,
      LocalDate voluntaryDeadline) {}

  private FilingCheck() {}

  public static Result of(YearPack pack, Answers answers) {
    List<Field> questions = new FilingCheckForm(pack).fields();
    List<Reason> yes = reasons(questions, answers, true);
    List<Reason> open = reasons(questions, answers, false);
    Status status =
        !yes.isEmpty() ? Status.MANDATORY : !open.isEmpty() ? Status.OPEN : Status.VOLUNTARY;
    return new Result(status, yes, open, pack.mandatoryDeadline(), pack.voluntaryDeadline());
  }

  private static List<Reason> reasons(List<Field> questions, Answers answers, boolean wantYes) {
    return questions.stream()
        .filter(
            question -> {
              String answer = answers.text(question.id());
              return wantYes
                  ? Kind.YES.equals(answer)
                  : !Kind.YES.equals(answer) && !Kind.NO.equals(answer);
            })
        .map(question -> new Reason(question.label(), question.line()))
        .toList();
  }
}
