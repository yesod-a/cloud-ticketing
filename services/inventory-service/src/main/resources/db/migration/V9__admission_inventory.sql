CREATE TABLE IF NOT EXISTS admission_inventory (
  session_id CHAR(36) PRIMARY KEY,
  capacity INT NOT NULL,
  reserved_count INT NOT NULL DEFAULT 0,
  sold_count INT NOT NULL DEFAULT 0,
  next_ticket_number BIGINT NOT NULL DEFAULT 1,
  version BIGINT NOT NULL DEFAULT 0,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT chk_admission_capacity CHECK (capacity >= 0),
  CONSTRAINT chk_admission_reserved CHECK (reserved_count >= 0),
  CONSTRAINT chk_admission_sold CHECK (sold_count >= 0),
  CONSTRAINT chk_admission_next_ticket CHECK (next_ticket_number >= 1)
);

CREATE TABLE IF NOT EXISTS admission_ticket (
  id CHAR(36) PRIMARY KEY,
  session_id CHAR(36) NOT NULL,
  order_id CHAR(36) NOT NULL,
  user_id CHAR(36) NOT NULL,
  ticket_number BIGINT NOT NULL,
  state VARCHAR(20) NOT NULL,
  expires_at TIMESTAMP NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_admission_ticket_number (session_id, ticket_number),
  KEY ix_admission_ticket_order_state (order_id, state),
  CONSTRAINT chk_admission_ticket_number CHECK (ticket_number >= 1)
);
