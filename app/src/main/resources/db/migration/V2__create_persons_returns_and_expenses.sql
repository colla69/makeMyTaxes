CREATE TABLE person (
  id           INTEGER PRIMARY KEY,
  household_id INTEGER NOT NULL REFERENCES household (id),
  name         TEXT    NOT NULL
);

CREATE TABLE tax_return (
  id        INTEGER PRIMARY KEY,
  person_id INTEGER NOT NULL REFERENCES person (id),
  tax_year  INTEGER NOT NULL,
  UNIQUE (person_id, tax_year)
);

CREATE TABLE expense (
  id                INTEGER PRIMARY KEY,
  tax_return_id     INTEGER NOT NULL REFERENCES tax_return (id),
  paid_on           TEXT    NOT NULL,
  amount_cents      INTEGER NOT NULL CHECK (amount_cents > 0),
  topic             TEXT    NOT NULL,
  description       TEXT    NOT NULL,
  receipt_reference TEXT    NOT NULL
);
