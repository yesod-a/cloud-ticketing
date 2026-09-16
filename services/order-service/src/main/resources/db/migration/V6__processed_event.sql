CREATE TABLE processed_event (
  event_id CHAR(36) NOT NULL,
  consumer_name VARCHAR(100) NOT NULL,
  event_type VARCHAR(100) NOT NULL,
  aggregate_id CHAR(36) NULL,
  payload_hash CHAR(64) NULL,
  trace_id VARCHAR(128) NULL,
  processed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (event_id, consumer_name),
  INDEX idx_processed_event_consumer_time (consumer_name, processed_at)
);
