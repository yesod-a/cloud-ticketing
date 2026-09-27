ALTER TABLE inventory_seat ADD COLUMN seat_index INT NULL;

UPDATE inventory_seat s
JOIN (
  SELECT id,
         ROW_NUMBER() OVER (
           PARTITION BY session_id
           ORDER BY area_label, position_y, position_x, row_label, seat_number, id
         ) - 1 AS calculated_index
  FROM inventory_seat
) ordered ON ordered.id = s.id
SET s.seat_index = ordered.calculated_index
WHERE s.seat_index IS NULL;

ALTER TABLE inventory_seat MODIFY COLUMN seat_index INT NOT NULL;
ALTER TABLE inventory_seat ADD UNIQUE KEY uq_inventory_seat_index(session_id, seat_index);
