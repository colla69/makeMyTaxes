-- Says where the amount comes from when no receipt exists by nature. Empty for an old expense.
ALTER TABLE expense ADD COLUMN stated_basis TEXT NOT NULL DEFAULT '';
