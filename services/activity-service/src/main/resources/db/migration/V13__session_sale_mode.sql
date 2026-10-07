ALTER TABLE activity_session ADD COLUMN sale_mode VARCHAR(16) NOT NULL DEFAULT 'DIRECT';
ALTER TABLE activity_session ADD CONSTRAINT chk_activity_session_sale_mode CHECK (sale_mode IN ('DIRECT','QUEUED'));
