SET @quantity_exists = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'ticket_order' AND column_name = 'quantity'
);
SET @add_quantity = IF(@quantity_exists = 0,
  'ALTER TABLE ticket_order ADD COLUMN quantity INT NOT NULL DEFAULT 0',
  'SELECT 1');
PREPARE add_quantity_stmt FROM @add_quantity;
EXECUTE add_quantity_stmt;
DEALLOCATE PREPARE add_quantity_stmt;

SET @ticket_numbers_exists = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'ticket_order' AND column_name = 'ticket_numbers'
);
SET @add_ticket_numbers = IF(@ticket_numbers_exists = 0,
  'ALTER TABLE ticket_order ADD COLUMN ticket_numbers TEXT NULL',
  'SELECT 1');
PREPARE add_ticket_numbers_stmt FROM @add_ticket_numbers;
EXECUTE add_ticket_numbers_stmt;
DEALLOCATE PREPARE add_ticket_numbers_stmt;

UPDATE ticket_order SET ticket_numbers = '' WHERE ticket_numbers IS NULL;
ALTER TABLE ticket_order MODIFY COLUMN ticket_numbers TEXT NOT NULL;
CREATE TABLE IF NOT EXISTS user_session_purchase (
  user_id CHAR(36) NOT NULL,
  session_id CHAR(36) NOT NULL,
  active_quantity INT NOT NULL DEFAULT 0,
  reserved_quantity INT NOT NULL DEFAULT 0,
  reservation_expires_at TIMESTAMP NULL,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id, session_id)
);

INSERT INTO user_session_purchase (user_id, session_id, active_quantity, reserved_quantity)
SELECT user_id, session_id,
       SUM(CASE WHEN status IN ('PENDING', 'PAID') THEN quantity ELSE 0 END),
       0
FROM ticket_order
WHERE quantity > 0
GROUP BY user_id, session_id
ON DUPLICATE KEY UPDATE active_quantity = VALUES(active_quantity), reserved_quantity = 0,
  reservation_expires_at = NULL;
