CREATE TABLE IF NOT EXISTS activity_audit_log (
  id CHAR(36) PRIMARY KEY,
  actor_user_id CHAR(36) NULL,
  action VARCHAR(128) NOT NULL,
  resource_type VARCHAR(64) NOT NULL,
  resource_id CHAR(36) NULL,
  before_json TEXT NULL,
  after_json TEXT NULL,
  trace_id VARCHAR(128) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
