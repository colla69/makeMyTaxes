package io.github.colla69.makemytaxes.app.web;

import io.github.colla69.makemytaxes.app.assistant.AssistantException;
import io.github.colla69.makemytaxes.app.assistant.PersonAssistant;
import io.github.colla69.makemytaxes.app.storage.ChatStore.Message;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

/**
 * The chat panel beside the tabs of a return. The nav template asks it for the panel, and htmx
 * sends each question here.
 */
@Controller
public class AssistantController {

  /** The panel of a return: the returns that the assistant sees, and the conversation. */
  public record ChatPanel(List<String> seenReturns, List<Message> messages) {}

  private final PersonAssistant assistant;

  AssistantController(PersonAssistant assistant) {
    this.assistant = assistant;
  }

  /** The panel, or null while the person of the return has the assistant off. */
  public ChatPanel panel(long returnId) {
    if (!assistant.onForReturn(returnId)) {
      return null;
    }
    return new ChatPanel(assistant.seenReturns(returnId), assistant.conversation(returnId));
  }

  @PostMapping("/returns/{returnId}/assistant")
  String ask(@PathVariable long returnId, @RequestParam String question, Model model) {
    if (!assistant.onForReturn(returnId)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
    String text = question.strip();
    if (text.isEmpty()) {
      model.addAttribute("error", "Bitte eine Frage eingeben.");
    } else {
      try {
        assistant.askAboutReturn(returnId, text);
      } catch (AssistantException e) {
        model.addAttribute("error", "Der Assistent hat nicht geantwortet: " + e.getMessage());
        model.addAttribute("question", text);
      } catch (RuntimeException e) {
        // htmx shows no error page, so the panel must say it. The message can hold any text, so
        // the panel does not show it.
        model.addAttribute("error", "Der Assistent hat nicht geantwortet.");
        model.addAttribute("question", text);
      }
    }
    model.addAttribute("returnId", returnId);
    model.addAttribute("messages", assistant.conversation(returnId));
    return "assistant-chat :: conversation";
  }
}
