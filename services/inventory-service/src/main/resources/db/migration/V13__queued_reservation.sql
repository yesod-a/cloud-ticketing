CREATE TABLE IF NOT EXISTS inventory_reservation (
  reservation_id CHAR(36) PRIMARY KEY,
  order_id CHAR(36) NULL,
  session_id CHAR(36) NOT NULL,
  user_id CHAR(36) NOT NULL,
  mode VARCHAR(24) NOT NULL,
  quantity INT NOT NULL,
  seat_ids TEXT NOT NULL,
  ticket_numbers TEXT NOT NULL,
  status VARCHAR(32) NOT NULL,
  expires_at TIMESTAMP NOT NULL,
  attempts INT NOT NULL DEFAULT 0,
  next_attempt_at TIMESTAMP NULL,
  last_error VARCHAR(512) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uq_inventory_reservation_order (order_id),
  KEY ix_inventory_reservation_status (status, next_attempt_at),
  KEY ix_inventory_reservation_expiry (expires_at, status)
);

CREATE TABLE IF NOT EXISTS inventory_reconciliation_diff (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  reservation_id CHAR(36) NULL,
  session_id CHAR(36) NOT NULL,
  diff_type VARCHAR(64) NOT NULL,
  before_state TEXT NOT NULL,
  after_state TEXT NULL,
  repair_status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
  reason VARCHAR(512) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  repaired_at TIMESTAMP NULL,
  KEY ix_reconciliation_status (repair_status, created_at),
  KEY ix_reconciliation_reservation (reservation_id)
);
