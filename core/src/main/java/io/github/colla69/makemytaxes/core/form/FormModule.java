package io.github.colla69.makemytaxes.core.form;

import io.github.colla69.makemytaxes.core.expense.Expense;
import java.util.List;
import java.util.Optional;

/** One form of a return. A new income type is a new module, and the core does not change. */
public interface FormModule {

  String id();

  String title();

  List<Field> fields();

  /** The fields for these answers. A form with a list, such as employers, has a block for each. */
  default List<Field> fields(Answers answers) {
    return fields();
  }

  /** The field with this ID, also in a list entry that has no answer yet. */
  default Optional<Field> field(String fieldId) {
    return fields().stream().filter(field -> field.id().equals(fieldId)).findFirst();
  }

  /** Checks that need more than one field. Each entry is a message for the user. */
  default List<String> check(Answers answers) {
    return List.of();
  }

  /** The values of this form in the order of the form. */
  default List<ReviewLine> review(Answers answers, List<Expense> expenses) {
    return fields(answers).stream()
        .filter(field -> answers.has(field.id()))
        .map(
            field ->
                new ReviewLine(
                    field.section(),
                    field.line(),
                    field.label(),
                    field.kind().display(answers.text(field.id())),
                    new Source.Answer(field.id())))
        .toList();
  }

  default List<Field> missing(Answers answers) {
    return fields(answers).stream()
        .filter(field -> field.required() && !answers.has(field.id()))
        .toList();
  }
}
