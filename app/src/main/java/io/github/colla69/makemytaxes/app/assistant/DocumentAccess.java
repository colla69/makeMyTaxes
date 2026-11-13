package io.github.colla69.makemytaxes.app.assistant;

import io.github.colla69.makemytaxes.core.TaxYear;
import java.nio.file.Path;
import java.util.Set;

/**
 * What Claude Code may open during one question: the documents of the returns that the assistant
 * sees under the rule of {@link ReturnContext#seenReturns}, through the MCP server of the app.
 *
 * @param token the one-time token of the question
 * @param question the returns whose documents the token opens, and their tax year
 * @param folder the folder of the question. It holds the MCP settings with the token.
 * @param mcpUrl the address of the MCP server of the app, on 127.0.0.1
 */
public record DocumentAccess(String token, Question question, Path folder, String mcpUrl) {

  /**
   * The returns that one question sees. They all belong to one tax year, so the law tools read the
   * law of that year.
   *
   * @param openReturnId the return whose panel asks the question. Proposals of the assistant go
   *     to this return.
   * @param taxYear the year of the returns, or null if the question sees no return
   */
  public record Question(long openReturnId, Set<Long> returnIds, TaxYear taxYear) {

    public Question {
      returnIds = Set.copyOf(returnIds);
    }
  }

  public Set<Long> returnIds() {
    return question.returnIds();
  }

  public long openReturnId() {
    return question.openReturnId();
  }

  public TaxYear taxYear() {
    return question.taxYear();
  }

  /** The folder where the document tool writes a document, and the only folder Claude Code reads. */
  public Path documents() {
    return folder.resolve("documents");
  }

  /** The MCP settings of Claude Code. They lie outside the folder that Claude Code may read. */
  public Path mcpConfig() {
    return folder.resolve("mcp.json");
  }
}
