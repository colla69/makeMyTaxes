-- Each answer comes from a document or from the user's own statement. An old answer was typed
-- with no document, so it counts as an own statement.
ALTER TABLE form_answer ADD COLUMN document_id INTEGER REFERENCES document (id);
ALTER TABLE form_answer ADD COLUMN own_statement INTEGER NOT NULL DEFAULT 1
  CHECK (own_statement IN (0, 1) AND (document_id IS NULL) = (own_statement = 1));
