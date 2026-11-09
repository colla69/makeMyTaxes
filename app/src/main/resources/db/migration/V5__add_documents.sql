-- The bytes are in their own table, so a list of documents never loads them.
CREATE TABLE document (
  id            INTEGER PRIMARY KEY,
  tax_return_id INTEGER NOT NULL REFERENCES tax_return (id),
  kind          TEXT    NOT NULL,
  file_name     TEXT    NOT NULL,
  media_type    TEXT    NOT NULL,
  size_bytes    INTEGER NOT NULL CHECK (size_bytes > 0),
  sha256        TEXT    NOT NULL,
  uploaded_on   TEXT    NOT NULL
);

CREATE TABLE document_content (
  document_id INTEGER PRIMARY KEY REFERENCES document (id),
  bytes       BLOB    NOT NULL
);
