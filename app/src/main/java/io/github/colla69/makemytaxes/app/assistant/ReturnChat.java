package io.github.colla69.makemytaxes.app.assistant;

import io.github.colla69.makemytaxes.app.assistant.ReturnContext.SeenReturn;
import io.github.colla69.makemytaxes.app.storage.ChatStore;
import io.github.colla69.makemytaxes.app.storage.ChatStore.Author;
import io.github.colla69.makemytaxes.app.storage.ChatStore.Exchange;
import io.github.colla69.makemytaxes.app.storage.ChatStore.Message;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** The conversation about a return: the prompt for a new question, and the stored messages. */
@Component
class ReturnChat {

  private static final String INSTRUCTIONS =
      """
      Du bist der Steuerassistent der App makeMyTaxes. Die App hilft bei der deutschen \
      Einkommensteuererklärung einer Person. Antworte auf Deutsch, kurz und klar.
      Gib Rat für das beste legale Ergebnis innerhalb der Fakten. Schlage nie vor, einen Betrag, \
      ein Datum, eine Zahl von Tagen oder eine andere Tatsache zu ändern, um ein besseres Ergebnis \
      zu erhalten. Nenne zu einer Regel die Vorschrift. Wenn du etwas nicht sicher weißt, sag es.
      Wenn du den Inhalt eines Belegs brauchst, rufe das Werkzeug read_document mit seiner \
      Beleg-Nr. auf und lies dann die genannte Datei mit Read.

      # Die Erklärungen, die du siehst
      """;

  /** A question with its prompt and the IDs of the returns that the prompt holds. */
  record Request(long returnId, String question, String prompt, Set<Long> seenReturns) {}

  private final ReturnContext context;
  private final ChatStore chat;
  private final QuestionTokens tokens;

  ReturnChat(ReturnContext context, ChatStore chat, QuestionTokens tokens) {
    this.context = context;
    this.chat = chat;
    this.tokens = tokens;
  }

  /** A one-time token for the question. It opens the documents of the returns in its prompt. */
  DocumentAccess openDocuments(Request request) {
    return tokens.open(request.seenReturns());
  }

  /** Ends the question: the token stops working and the written documents are deleted. */
  void closeDocuments(DocumentAccess access) {
    tokens.close(access);
  }

  List<String> seenReturns(long returnId) {
    return context.seenReturns(returnId).stream().map(SeenReturn::label).toList();
  }

  /**
   * The messages that the assistant may still see. A message that saw a return that the
   * assistant does not see now stays out, such as a return of an adult who turned the assistant
   * off since then.
   */
  List<Message> conversation(long returnId) {
    return conversation(returnId, seenIds(context.seenReturns(returnId)));
  }

  /** The instructions, the returns that the assistant sees, the conversation and the question. */
  Request prepare(long returnId, String question) {
    List<SeenReturn> seen = context.seenReturns(returnId);
    Set<Long> seenIds = seenIds(seen);
    StringBuilder prompt = new StringBuilder(INSTRUCTIONS).append("\n").append(context.text(seen));
    List<Message> conversation = conversation(returnId, seenIds);
    if (!conversation.isEmpty()) {
      prompt.append("# Das bisherige Gespräch\n");
      for (Message message : conversation) {
        prompt
            .append(message.author() == Author.USER ? "Nutzer: " : "Assistent: ")
            .append(message.text())
            .append("\n\n");
      }
    }
    prompt.append("# Die neue Frage\n").append(question).append("\n");
    return new Request(returnId, question, prompt.toString(), seenIds);
  }

  void remember(Request request, String answer) {
    chat.addExchange(
        request.returnId(), new Exchange(request.question(), answer, request.seenReturns()));
  }

  private List<Message> conversation(long returnId, Set<Long> seenIds) {
    return chat.messagesOf(returnId).stream()
        .filter(message -> seenIds.containsAll(message.seenReturns()))
        .toList();
  }

  private static Set<Long> seenIds(List<SeenReturn> seen) {
    return seen.stream().map(SeenReturn::returnId).collect(Collectors.toUnmodifiableSet());
  }
}
