CREATE TABLE IF NOT EXISTS inventory_audit_log (
  id CHAR(36) PRIMARY KEY,
  actor_user_id CHAR(36) NULL,
  action VARCHAR(64) NOT NULL,
  resource_id CHAR(36) NOT NULL,
  before_status VARCHAR(20) NULL,
  after_status VARCHAR(20) NOT NULL,
  reason VARCHAR(500) NOT NULL,
  trace_id VARCHAR(128) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
