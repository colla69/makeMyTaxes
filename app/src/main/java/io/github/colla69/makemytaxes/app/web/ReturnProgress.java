package io.github.colla69.makemytaxes.app.web;

import io.github.colla69.makemytaxes.app.storage.FormStore;
import io.github.colla69.makemytaxes.app.storage.HouseholdStore;
import io.github.colla69.makemytaxes.core.form.Answers;
import io.github.colla69.makemytaxes.core.form.FormModule;
import io.github.colla69.makemytaxes.core.form.Interview;
import io.github.colla69.makemytaxes.core.form.Interview.Step;
import io.github.colla69.makemytaxes.core.form.YearDefinition;
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
   * The IDs of the done tabs. A form is done when no required field is missing, and it has saved
   * answers or the interview is done. A form that the return does not need is done when the
   * interview is done, even when it holds answers, such as the occupation of a person without
   * employment. The interview is done when each question has an answer.
   */
  public Set<String> doneTabs(long returnId) {
    Set<String> done = new HashSet<>();
    household
        .taxYearOf(returnId)
        .flatMap(YearDefinition::of)
        .ifPresent(
            definition -> {
              Answers interview = forms.answers(returnId, Interview.ID);
              boolean interviewDone = definition.interview().isDone(interview);
              Set<String> needed = new HashSet<>();
              for (Step step : definition.interview().steps(interview)) {
                if (step.isForm()) {
                  needed.add(step.formId());
                }
              }
              for (FormModule form : definition.forms()) {
                Answers answers = forms.answers(returnId, form.id());
                boolean started = !answers.values().isEmpty() || interviewDone;
                boolean complete = started && form.missing(answers).isEmpty();
                boolean notNeeded = interviewDone && !needed.contains(form.id());
                if (complete || notNeeded) {
                  done.add(form.id());
                }
              }
              if (interviewDone) {
                done.add(Interview.ID);
              }
            });
    return done;
  }
}
