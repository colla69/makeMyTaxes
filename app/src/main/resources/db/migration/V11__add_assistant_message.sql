-- The conversation with the assistant about one return, in the order of the messages.
-- seen_returns holds the IDs of the returns that the assistant saw for the message, comma-separated.
-- A message that saw a return that the assistant may no longer see stays out of later prompts.
CREATE TABLE assistant_message (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  tax_return_id INTEGER NOT NULL REFERENCES tax_return(id),
  author TEXT NOT NULL CHECK (author IN ('USER', 'ASSISTANT')),
  text TEXT NOT NULL,
  seen_returns TEXT NOT NULL
);

CREATE INDEX assistant_message_return ON assistant_message(tax_return_id, id);
