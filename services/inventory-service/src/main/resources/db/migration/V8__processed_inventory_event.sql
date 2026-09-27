CREATE TABLE processed_inventory_event (
  event_id CHAR(36) PRIMARY KEY,
  event_type VARCHAR(100) NOT NULL,
  aggregate_id CHAR(36) NOT NULL,
  trace_id VARCHAR(128) NULL,
  processed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_processed_inventory_event_time (processed_at)
);
