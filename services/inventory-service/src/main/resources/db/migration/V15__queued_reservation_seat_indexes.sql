ALTER TABLE inventory_reservation
  MODIFY seat_ids TEXT NULL,
  MODIFY ticket_numbers TEXT NULL,
  ADD COLUMN seat_indexes TEXT NULL AFTER quantity;
