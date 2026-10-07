ALTER TABLE activity_session ADD COLUMN sale_start_at TIMESTAMP NULL AFTER sale_mode;
CREATE INDEX ix_activity_session_sale_start ON activity_session(status, sale_start_at);
