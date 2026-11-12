package io.github.colla69.makemytaxes.app.assistant;

import io.github.colla69.makemytaxes.app.storage.HouseholdStore;
import org.springframework.stereotype.Service;

/**
 * The only way to the assistant. The data of a person reaches the assistant only while that
 * person has the assistant turned on.
 */
@Service
public class PersonAssistant {

  private final HouseholdStore household;
  private final Assistant assistant;

  public PersonAssistant(HouseholdStore household, Assistant assistant) {
    this.household = household;
    this.assistant = assistant;
  }

  public boolean available() {
    return assistant.available();
  }

  /** Sends the prompt only if the person turned the assistant on. */
  public String ask(long personId, String prompt) throws AssistantException {
    if (!household.assistantOn(personId)) {
      throw new AssistantException("The assistant is off for this person.");
    }
    if (!assistant.available()) {
      throw new AssistantException("The assistant is not available on this machine.");
    }
    return assistant.ask(prompt);
  }
}
