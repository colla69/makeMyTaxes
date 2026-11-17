package io.github.colla69.makemytaxes.app.web;

import io.github.colla69.makemytaxes.app.storage.DocumentStore.NewDocument;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The uploads that the app could not recognize. The app stores such a file only after the user
 * names its kind, so it holds the file in memory until then. A restart forgets the files. Only
 * the newest few stay, so that files the user leaves do not fill the memory.
 */
@Component
class PendingUploads {

  private static final int KEPT = 5;

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

  synchronized void remove(String token) {
    byToken.remove(token);
  }
}
