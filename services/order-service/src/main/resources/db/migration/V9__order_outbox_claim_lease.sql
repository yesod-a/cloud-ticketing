ALTER TABLE order_outbox ADD COLUMN claim_token VARCHAR(64) NULL;
ALTER TABLE order_outbox ADD COLUMN claimed_until TIMESTAMP NULL;
ALTER TABLE order_outbox ADD INDEX idx_order_outbox_claim (published_at, claimed_until, occurred_at);
