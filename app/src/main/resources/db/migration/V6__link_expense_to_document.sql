-- Empty for an old expense. An old expense keeps its receipt_reference text.
ALTER TABLE expense ADD COLUMN document_id INTEGER REFERENCES document (id);
