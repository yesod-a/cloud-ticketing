ALTER TABLE order_outbox ADD COLUMN next_attempt_at TIMESTAMP NULL;
ALTER TABLE order_outbox ADD INDEX idx_order_outbox_retry (published_at, next_attempt_at, claimed_until);
