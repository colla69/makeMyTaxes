package io.github.colla69.makemytaxes.app.storage;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** The conversation with the assistant about one return. */
@Repository
public class ChatStore {

  public enum Author {
    USER,
    ASSISTANT
  }

  /** A message, with the IDs of the returns that the assistant saw for it. */
  public record Message(Author author, String text, Set<Long> seenReturns) {}

  /** A question and its answer, with the IDs of the returns that the assistant saw for them. */
  public record Exchange(String question, String answer, Set<Long> seenReturns) {}

  private final JdbcClient jdbc;

  ChatStore(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /** The messages of the return, the oldest first. */
  public List<Message> messagesOf(long returnId) {
    return jdbc.sql(
            "select author, text, seen_returns from assistant_message"
                + " where tax_return_id = ? order by id")
        .param(returnId)
        .query(
            (row, rowNumber) ->
                new Message(
                    Author.valueOf(row.getString("author")),
                    row.getString("text"),
                    parseIds(row.getString("seen_returns"))))
        .list();
  }

  /** Adds a question and its answer together, so the conversation never holds half an exchange. */
  @Transactional
  public void addExchange(long returnId, Exchange exchange) {
    add(returnId, new Message(Author.USER, exchange.question(), exchange.seenReturns()));
    add(returnId, new Message(Author.ASSISTANT, exchange.answer(), exchange.seenReturns()));
  }

  private void add(long returnId, Message message) {
    String seen =
        message.seenReturns().stream().sorted().map(String::valueOf).collect(Collectors.joining(","));
    jdbc.sql(
            "insert into assistant_message(tax_return_id, author, text, seen_returns)"
                + " values (?, ?, ?, ?)")
        .params(returnId, message.author().name(), message.text(), seen)
        .update();
  }

  private static Set<Long> parseIds(String ids) {
    return Arrays.stream(ids.split(","))
        .filter(id -> !id.isBlank())
        .map(Long::valueOf)
        .collect(Collectors.toUnmodifiableSet());
  }
}
