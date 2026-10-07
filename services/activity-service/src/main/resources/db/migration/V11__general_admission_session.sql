ALTER TABLE activity_session
  ADD COLUMN layout_mode VARCHAR(30) NOT NULL DEFAULT 'GRID',
  ADD COLUMN capacity INT NOT NULL DEFAULT 0,
  ADD COLUMN purchase_limit INT NOT NULL DEFAULT 0;

ALTER TABLE activity_session
  ADD CONSTRAINT chk_activity_session_capacity CHECK (capacity >= 0),
  ADD CONSTRAINT chk_activity_session_purchase_limit CHECK (purchase_limit >= 0);
