-- An answer loses its document when the user deletes the document anyway. The answer keeps its
-- value, but it has no evidence: document_id is empty and own_statement is 0. The app shows
-- "Beleg fehlt" for such an answer, so the user can add new evidence. SQLite cannot change a
-- CHECK, so the table is built again with the same columns and rows.
CREATE TABLE form_answer_new (
  tax_return_id    INTEGER NOT NULL REFERENCES tax_return (id),
  form_id          TEXT    NOT NULL,
  field_id         TEXT    NOT NULL,
  value            TEXT    NOT NULL,
  document_id      INTEGER REFERENCES document (id),
  own_statement    INTEGER NOT NULL DEFAULT 1
    CHECK (own_statement IN (0, 1) AND NOT (own_statement = 1 AND document_id IS NOT NULL)),
  confirmed_source INTEGER NOT NULL DEFAULT 0 CHECK (confirmed_source IN (0, 1)),
  PRIMARY KEY (tax_return_id, form_id, field_id)
);

INSERT INTO form_answer_new
  (tax_return_id, form_id, field_id, value, document_id, own_statement, confirmed_source)
SELECT tax_return_id, form_id, field_id, value, document_id, own_statement, confirmed_source
FROM form_answer;

DROP TABLE form_answer;

ALTER TABLE form_answer_new RENAME TO form_answer;
