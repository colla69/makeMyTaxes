CREATE TABLE person_detail (
  person_id INTEGER NOT NULL REFERENCES person (id),
  field_id  TEXT    NOT NULL,
  value     TEXT    NOT NULL,
  PRIMARY KEY (person_id, field_id)
);
