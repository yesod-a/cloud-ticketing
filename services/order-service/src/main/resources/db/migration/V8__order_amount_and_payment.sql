ALTER TABLE ticket_order ADD COLUMN amount_minor INT NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS payment (
  id CHAR(36) PRIMARY KEY,
  order_id CHAR(36) NOT NULL,
  user_id CHAR(36) NOT NULL,
  method VARCHAR(20) NOT NULL,
  amount_minor INT NOT NULL DEFAULT 0,
  currency VARCHAR(10) NOT NULL DEFAULT 'CNY',
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  qr_token VARCHAR(128) NOT NULL,
  provider_transaction_id VARCHAR(128) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  paid_at TIMESTAMP NULL,
  UNIQUE KEY uq_payment_order (order_id)
);
