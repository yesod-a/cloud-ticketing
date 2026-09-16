CREATE TABLE IF NOT EXISTS order_outbox (
  event_id CHAR(36) PRIMARY KEY,
  event_type VARCHAR(100) NOT NULL,
  aggregate_id CHAR(36) NOT NULL,
  payload JSON NOT NULL,
  trace_id VARCHAR(128) NULL,
  occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  published_at TIMESTAMP NULL,
  attempts INT NOT NULL DEFAULT 0,
  last_error TEXT NULL,
  INDEX idx_order_outbox_pending (published_at, occurred_at)
);
