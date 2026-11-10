package io.github.colla69.makemytaxes.core.form;

import io.github.colla69.makemytaxes.core.year.YearPack;
import java.util.List;

/**
 * A block of fields that is the same in each year, such as the employer block of Anlage N, with
 * the rules that do not depend on the year. The fields have no line: the {@link FormLayout} of a
 * year gives each field its line.
 *
 * @param perEmployer true if the form has this block once for each employer
 * @param check the rules of the block. The form runs them once, or once for each employer.
 */
public record Section(boolean perEmployer, List<Field> fields, Check check) {

  /** The rules of a section. The values that depend on the year come from the year pack. */
  @FunctionalInterface
  public interface Check {

    Check NONE = (answers, employer, pack) -> List.of();

    /**
     * The messages for the answers.
     *
     * @param employer the employer of the block, or 1 for a section that the form has once
     */
    List<String> messages(Answers answers, int employer, YearPack pack);
  }

  static Section once(Field... fields) {
    return new Section(false, List.of(fields), Check.NONE);
  }

  static Section perEmployer(Field... fields) {
    return new Section(true, List.of(fields), Check.NONE);
  }

  /** The same section with these rules. */
  Section withCheck(Check check) {
    return new Section(perEmployer, fields, check);
  }
}
