package io.github.colla69.makemytaxes.app.assistant;

import java.util.ArrayList;
import java.util.List;

/** An assistant for tests. It starts no process and sends nothing off the machine. */
public class FakeAssistant implements Assistant {

  private final List<String> prompts = new ArrayList<>();
  private volatile boolean available = true;
  private volatile String answer = "Made-up answer";

  @Override
  public boolean available() {
    return available;
  }

  @Override
  public synchronized String ask(String prompt) throws AssistantException {
    if (!available) {
      throw new AssistantException("The fake assistant is not available.");
    }
    prompts.add(prompt);
    return answer;
  }

  public void setAvailable(boolean available) {
    this.available = available;
  }

  public void setAnswer(String answer) {
    this.answer = answer;
  }

  public synchronized List<String> prompts() {
    return List.copyOf(prompts);
  }
}
