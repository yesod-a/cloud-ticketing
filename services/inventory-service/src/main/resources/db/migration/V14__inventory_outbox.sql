CREATE TABLE IF NOT EXISTS inventory_outbox (
  event_id CHAR(36) PRIMARY KEY,
  topic VARCHAR(128) NOT NULL,
  event_key VARCHAR(128) NOT NULL,
  payload JSON NOT NULL,
  published_at TIMESTAMP NULL,
  attempts INT NOT NULL DEFAULT 0,
  next_attempt_at TIMESTAMP NULL,
  last_error VARCHAR(512) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY ix_inventory_outbox_pending (published_at, next_attempt_at, created_at)
);
