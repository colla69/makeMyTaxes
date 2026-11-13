package io.github.colla69.makemytaxes.app.assistant;

import java.nio.file.Path;
import java.util.Set;

/**
 * What Claude Code may open during one question: the documents of the returns that the assistant
 * sees under the rule of {@link ReturnContext#seenReturns}, through the MCP server of the app.
 *
 * @param token the one-time token of the question
 * @param returnIds the returns whose documents the token opens
 * @param folder the folder of the question. It holds the MCP settings with the token.
 * @param mcpUrl the address of the MCP server of the app, on 127.0.0.1
 */
public record DocumentAccess(String token, Set<Long> returnIds, Path folder, String mcpUrl) {

  /** The folder where the document tool writes a document, and the only folder Claude Code reads. */
  public Path documents() {
    return folder.resolve("documents");
  }

  /** The MCP settings of Claude Code. They lie outside the folder that Claude Code may read. */
  public Path mcpConfig() {
    return folder.resolve("mcp.json");
  }
}
