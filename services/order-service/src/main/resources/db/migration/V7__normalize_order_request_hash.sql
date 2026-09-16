UPDATE ticket_order
SET request_hash = SHA2(CONCAT(user_id, '|', session_id, '|', REPLACE(seat_ids, ' ', '')), 256);
