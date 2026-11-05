CREATE TABLE form_answer (
  tax_return_id INTEGER NOT NULL REFERENCES tax_return (id),
  form_id       TEXT    NOT NULL,
  field_id      TEXT    NOT NULL,
  value         TEXT    NOT NULL,
  PRIMARY KEY (tax_return_id, form_id, field_id)
);

UPDATE expense SET topic = 'WORK_OTHER' WHERE topic = 'WORK';
