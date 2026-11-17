package io.github.colla69.makemytaxes.app.web;

import io.github.colla69.makemytaxes.app.storage.DocumentStore.NewDocument;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The uploads that the app could not recognize. The app stores such a file only after the user
 * names its kind, so it holds the file in memory until then. A restart forgets the files. Only
 * the newest 20 stay, enough for the unknown files of an upload of several files, so that files
 * the user leaves do not fill the memory.
 */
@Component
class PendingUploads {

  private static final int KEPT = 20;

  record Pending(long returnId, NewDocument document) {}

  private final Map<String, Pending> byToken =
      new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Pending> eldest) {
          return size() > KEPT;
        }
      };

  synchronized String put(long returnId, NewDocument document) {
    String token = UUID.randomUUID().toString();
    byToken.put(token, new Pending(returnId, document));
    return token;
  }

  /** The file of the token, if it belongs to the return. */
  synchronized Optional<NewDocument> find(long returnId, String token) {
    return Optional.ofNullable(byToken.get(token))
        .filter(pending -> pending.returnId() == returnId)
        .map(Pending::document);
  }

  /** A file that waits for its kind, as the list on the page shows it. */
  record Waiting(String token, String fileName) {}

  /** The files of the return that wait for their kind, the oldest first. */
  synchronized List<Waiting> of(long returnId) {
    return byToken.entrySet().stream()
        .filter(entry -> entry.getValue().returnId() == returnId)
        .map(entry -> new Waiting(entry.getKey(), entry.getValue().document().fileName()))
        .toList();
  }

  synchronized void remove(String token) {
    byToken.remove(token);
  }
}
