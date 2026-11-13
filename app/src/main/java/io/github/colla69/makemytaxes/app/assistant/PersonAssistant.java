package io.github.colla69.makemytaxes.app.assistant;

import io.github.colla69.makemytaxes.app.storage.ChatStore.Message;
import io.github.colla69.makemytaxes.app.storage.HouseholdStore;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * The only way to the assistant. The data of a person reaches the assistant only while that
 * person has the assistant turned on.
 */
@Service
public class PersonAssistant {

  private final HouseholdStore household;
  private final Assistant assistant;
  private final ReturnChat chat;

  PersonAssistant(HouseholdStore household, Assistant assistant, ReturnChat chat) {
    this.household = household;
    this.assistant = assistant;
    this.chat = chat;
  }

  public boolean available() {
    return assistant.available();
  }

  /** True if the person of the return turned the assistant on. */
  public boolean onForReturn(long returnId) {
    return household.personOf(returnId).map(household::assistantOn).orElse(false);
  }

  /**
   * The names of the returns that the assistant sees from this return: the open return, and the
   * linked return of the other adult only while both adults have the assistant on.
   */
  public List<String> seenReturns(long returnId) {
    return chat.seenReturns(returnId);
  }

  /**
   * Asks a question about the return, with its context and the conversation so far. The question
   * and the answer join the conversation only if the assistant answers.
   */
  public String askAboutReturn(long returnId, String question) throws AssistantException {
    ReturnChat.Request request = chat.prepare(returnId, question);
    String answer = ask(request);
    chat.remember(request, answer);
    return answer;
  }

  /**
   * Checks the return for the best legal result within the facts. Each tip has a source, and the
   * tips join the conversation.
   */
  public List<Advice.Tip> adviseOnReturn(long returnId) throws AssistantException {
    ReturnChat.Request request = chat.prepare(returnId, Advice.QUESTION);
    List<Advice.Tip> tips = Advice.tips(ask(request));
    chat.remember(request.withQuestion(Advice.LABEL), Advice.text(tips));
    return tips;
  }

  private String ask(ReturnChat.Request request) throws AssistantException {
    long personId =
        household
            .personOf(request.returnId())
            .orElseThrow(() -> new AssistantException("The return does not exist."));
    DocumentAccess access = chat.openDocuments(request);
    try {
      checkOn(personId);
      return assistant.ask(request.prompt(), access);
    } finally {
      chat.closeDocuments(access);
    }
  }

  /**
   * The conversation about the return. A message that saw the return of an adult who has the
   * assistant off now stays out, so it neither shows nor reaches the next prompt.
   */
  public List<Message> conversation(long returnId) {
    return chat.conversation(returnId);
  }

  /** Sends the prompt only if the person turned the assistant on. */
  public String ask(long personId, String prompt) throws AssistantException {
    checkOn(personId);
    return assistant.ask(prompt);
  }

  private void checkOn(long personId) throws AssistantException {
    if (!household.assistantOn(personId)) {
      throw new AssistantException("The assistant is off for this person.");
    }
    if (!assistant.available()) {
      throw new AssistantException("The assistant is not available on this machine.");
    }
  }
}
