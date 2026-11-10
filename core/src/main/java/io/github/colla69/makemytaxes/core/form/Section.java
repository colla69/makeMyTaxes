package io.github.colla69.makemytaxes.core.form;

import java.util.List;

/**
 * A block of fields that is the same in each year, such as the employer block of Anlage N. The
 * fields have no line: the {@link FormLayout} of a year gives each field its line.
 *
 * @param perEmployer true if the form has this block once for each employer
 */
public record Section(boolean perEmployer, List<Field> fields) {

  static Section once(Field... fields) {
    return new Section(false, List.of(fields));
  }

  static Section perEmployer(Field... fields) {
    return new Section(true, List.of(fields));
  }
}
