-- Commercial plan. Server-side only (never user-editable); sent to Flagsmith as the "plan" trait so that segments
-- such as "Plus members" decide which paid capabilities are on.
ALTER TABLE app_user ADD COLUMN plan VARCHAR(20) NOT NULL DEFAULT 'free';
