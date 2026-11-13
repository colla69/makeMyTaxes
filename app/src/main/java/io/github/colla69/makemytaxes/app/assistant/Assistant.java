package io.github.colla69.makemytaxes.app.assistant;

/**
 * A language model that answers a prompt. Only {@link PersonAssistant} may call it, because it
 * checks first that the person turned the assistant on.
 */
public interface Assistant {

  /** True if the assistant can answer on this machine. The check starts no process. */
  boolean available();

  /** The answer to the prompt. The prompt leaves the machine. The assistant gets no tool. */
  String ask(String prompt) throws AssistantException;

  /**
   * The answer to the prompt. The assistant may read the documents that the access opens, and
   * each document that it reads leaves the machine too.
   */
  String ask(String prompt, DocumentAccess access) throws AssistantException;
}
