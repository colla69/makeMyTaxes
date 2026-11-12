-- Each person turns the assistant on for their own returns. It is off until the person turns it on.
ALTER TABLE person ADD COLUMN assistant_on INTEGER NOT NULL DEFAULT 0;
