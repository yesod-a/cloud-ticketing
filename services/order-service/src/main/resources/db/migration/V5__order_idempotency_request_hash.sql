ALTER TABLE ticket_order ADD COLUMN request_hash CHAR(64) NULL;
UPDATE ticket_order SET request_hash = SHA2(CONCAT(user_id, '|', session_id, '|', REPLACE(seat_ids, ' ', '')), 256) WHERE request_hash IS NULL;
ALTER TABLE ticket_order MODIFY COLUMN request_hash CHAR(64) NOT NULL;
CREATE INDEX idx_ticket_order_request_hash ON ticket_order(request_hash);
