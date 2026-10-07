CREATE TABLE IF NOT EXISTS user_session_purchase_reservation (
  order_id CHAR(36) PRIMARY KEY,
  user_id CHAR(36) NOT NULL,
  session_id CHAR(36) NOT NULL,
  quantity INT NOT NULL,
  state VARCHAR(20) NOT NULL DEFAULT 'HELD',
  expires_at TIMESTAMP NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY ix_purchase_reservation_expiry (state, expires_at),
  KEY ix_purchase_reservation_user_session (user_id, session_id, state)
);
