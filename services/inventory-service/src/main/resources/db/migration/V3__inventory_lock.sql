CREATE TABLE IF NOT EXISTS inventory_lock (
  id CHAR(36) PRIMARY KEY,
  order_id CHAR(36) NOT NULL,
  session_id CHAR(36) NOT NULL,
  seat_id CHAR(36) NOT NULL,
  active TINYINT NOT NULL DEFAULT 1,
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  expires_at TIMESTAMP NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_inventory_active_lock (session_id, seat_id, active),
  KEY idx_inventory_lock_order (order_id, active),
  KEY idx_inventory_lock_expiry (expires_at, active)
);
