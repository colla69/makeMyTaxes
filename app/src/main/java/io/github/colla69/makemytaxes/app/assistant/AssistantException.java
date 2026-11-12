package io.github.colla69.makemytaxes.app.assistant;

/** The assistant gave no answer. The message holds no data of the prompt or of the answer. */
public class AssistantException extends Exception {

  public AssistantException(String message) {
    super(message);
  }

  public AssistantException(String message, Throwable cause) {
    super(message, cause);
  }
}
