package io.github.colla69.makemytaxes.app.web;

import io.github.colla69.makemytaxes.app.storage.FormStore;
import io.github.colla69.makemytaxes.app.storage.HouseholdStore;
import io.github.colla69.makemytaxes.core.form.Answers;
import io.github.colla69.makemytaxes.core.form.FormModule;
import io.github.colla69.makemytaxes.core.form.Forms;
import io.github.colla69.makemytaxes.core.form.Interview;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Tells the tabs which pages of a return are done. The nav template calls it. */
@Component
public class ReturnProgress {

  private final HouseholdStore household;
  private final FormStore forms;

  ReturnProgress(HouseholdStore household, FormStore forms) {
    this.household = household;
    this.forms = forms;
  }

  /**
   * The IDs of the done tabs. A form is done when it has saved answers and no required field is
   * missing. The interview is done when each question has an answer.
   */
  public Set<String> doneTabs(long returnId) {
    Set<String> done = new HashSet<>();
    household
        .taxYearOf(returnId)
        .ifPresent(
            taxYear -> {
              for (FormModule form : Forms.of(taxYear)) {
                Answers answers = forms.answers(returnId, form.id());
                if (!answers.values().isEmpty() && form.missing(answers).isEmpty()) {
                  done.add(form.id());
                }
              }
            });
    if (Interview.isDone(forms.answers(returnId, Interview.ID))) {
      done.add(Interview.ID);
    }
    return done;
  }
}
