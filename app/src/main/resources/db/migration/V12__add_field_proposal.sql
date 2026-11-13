-- A value that the assistant proposes for a field of a return, with its source. It waits here until
-- the user confirms or rejects it. Only a confirmed value enters form_answer.
-- source is the text that the user sees, such as "Lohnsteuerbescheinigung, Zeile 3". document_id is
-- the document of the return that the value comes from, if any.
CREATE TABLE field_proposal (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  tax_return_id INTEGER NOT NULL REFERENCES tax_return(id),
  form_id TEXT NOT NULL,
  field_id TEXT NOT NULL,
  value TEXT NOT NULL,
  source TEXT NOT NULL,
  document_id INTEGER REFERENCES document(id)
);

CREATE INDEX field_proposal_return ON field_proposal(tax_return_id, id);

-- 1 if the user confirmed the source of the answer, such as from a proposal of the assistant. The
-- answer keeps this source while its value stays the same, also when the interview changes.
ALTER TABLE form_answer ADD COLUMN confirmed_source INTEGER NOT NULL DEFAULT 0
  CHECK (confirmed_source IN (0, 1));
