-- The month of a payslip document. A return has at most one payslip per month.
CREATE TABLE payslip (
  document_id   INTEGER PRIMARY KEY REFERENCES document (id),
  tax_return_id INTEGER NOT NULL REFERENCES tax_return (id),
  month         INTEGER NOT NULL CHECK (month BETWEEN 1 AND 12),
  UNIQUE (tax_return_id, month)
);
