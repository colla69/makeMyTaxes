-- The part of a medical cost that an insurer paid back. Zero for an old expense.
ALTER TABLE expense ADD COLUMN refunded_cents INTEGER NOT NULL DEFAULT 0;
