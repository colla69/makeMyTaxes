package io.github.colla69.makemytaxes.app.web;

import io.github.colla69.makemytaxes.app.assistant.AssistantException;
import io.github.colla69.makemytaxes.app.assistant.PersonAssistant;
import io.github.colla69.makemytaxes.app.storage.ChatStore.Message;
import io.github.colla69.makemytaxes.app.web.ProposalReview.ProposalView;
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

  /**
   * The panel of a return: the returns that the assistant sees, the conversation, and the values
   * that the assistant proposes and that wait for the user.
   */
  public record ChatPanel(
      List<String> seenReturns, List<Message> messages, List<ProposalView> proposals) {}

  private final PersonAssistant assistant;
  private final ProposalReview proposals;

  AssistantController(PersonAssistant assistant, ProposalReview proposals) {
    this.assistant = assistant;
    this.proposals = proposals;
  }

  /** The panel, or null while the person of the return has the assistant off. */
  public ChatPanel panel(long returnId) {
    if (!assistant.onForReturn(returnId)) {
      return null;
    }
    return new ChatPanel(
        assistant.seenReturns(returnId),
        assistant.conversation(returnId),
        proposals.pending(returnId));
  }

  @PostMapping("/returns/{returnId}/assistant")
  String ask(@PathVariable long returnId, @RequestParam String question, Model model) {
    checkOn(returnId);
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
    return conversation(returnId, model);
  }

  /** True if the review page offers the check by the assistant. */
  public boolean adviceOn(long returnId) {
    return assistant.onForReturn(returnId);
  }

  /**
   * The assistant checks the return for the best legal result. The tips join the conversation. The
   * chat panel shows the conversation, and the review page shows the tips.
   */
  @PostMapping("/returns/{returnId}/assistant/advice")
  String advise(
      @PathVariable long returnId,
      @RequestParam(defaultValue = "chat") String view,
      Model model) {
    checkOn(returnId);
    try {
      model.addAttribute("tips", assistant.adviseOnReturn(returnId));
    } catch (AssistantException e) {
      model.addAttribute("error", "Der Assistent hat nicht geantwortet: " + e.getMessage());
    } catch (RuntimeException e) {
      model.addAttribute("error", "Der Assistent hat nicht geantwortet.");
    }
    if (view.equals("review")) {
      return "assistant-chat :: advice";
    }
    return conversation(returnId, model);
  }

  /** The user takes the proposed value into the return, with its source. */
  @PostMapping("/returns/{returnId}/assistant/proposals/{proposalId}/confirm")
  String confirm(@PathVariable long returnId, @PathVariable long proposalId, Model model) {
    checkOn(returnId);
    proposals.confirm(returnId, proposalId);
    return conversation(returnId, model);
  }

  /** The user drops the proposed value. The return does not change. */
  @PostMapping("/returns/{returnId}/assistant/proposals/{proposalId}/reject")
  String reject(@PathVariable long returnId, @PathVariable long proposalId, Model model) {
    checkOn(returnId);
    proposals.reject(returnId, proposalId);
    return conversation(returnId, model);
  }

  @PostMapping("/returns/{returnId}/assistant/proposals/confirm")
  String confirmAll(@PathVariable long returnId, Model model) {
    checkOn(returnId);
    proposals.confirmAll(returnId);
    return conversation(returnId, model);
  }

  @PostMapping("/returns/{returnId}/assistant/proposals/reject")
  String rejectAll(@PathVariable long returnId, Model model) {
    checkOn(returnId);
    proposals.rejectAll(returnId);
    return conversation(returnId, model);
  }

  private void checkOn(long returnId) {
    if (!assistant.onForReturn(returnId)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
  }

  private String conversation(long returnId, Model model) {
    model.addAttribute("returnId", returnId);
    model.addAttribute("messages", assistant.conversation(returnId));
    model.addAttribute("proposals", proposals.pending(returnId));
    return "assistant-chat :: conversation";
  }
}
