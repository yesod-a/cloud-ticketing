ALTER TABLE ticket_order ADD COLUMN expire_at TIMESTAMP NULL;
UPDATE ticket_order SET expire_at = TIMESTAMPADD(MINUTE, 15, created_at) WHERE expire_at IS NULL;
CREATE INDEX ix_ticket_order_expiry ON ticket_order(status, expire_at, id);

CREATE TABLE IF NOT EXISTS order_timeout_outbox (
  id CHAR(36) PRIMARY KEY,
  order_id CHAR(36) NOT NULL,
  expire_at TIMESTAMP NOT NULL,
  published_at TIMESTAMP NULL,
  attempts INT NOT NULL DEFAULT 0,
  last_error TEXT NULL,
  next_attempt_at TIMESTAMP NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_order_timeout_outbox_order (order_id),
  KEY ix_order_timeout_outbox_pending (published_at, next_attempt_at, created_at)
);
