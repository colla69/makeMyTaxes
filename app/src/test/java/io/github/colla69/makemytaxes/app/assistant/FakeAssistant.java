package io.github.colla69.makemytaxes.app.assistant;

import java.time.Duration;
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

  private volatile Duration delay = Duration.ZERO;

  @Override
  public synchronized String ask(String prompt) throws AssistantException {
    if (!available) {
      throw new AssistantException("The fake assistant is not available.");
    }
    prompts.add(prompt);
    try {
      Thread.sleep(delay);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssistantException("The fake assistant stopped.", e);
    }
    return answer;
  }

  /** How long the fake works before it answers, so a test can see the panel while it works. */
  public void setDelay(Duration delay) {
    this.delay = delay;
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
