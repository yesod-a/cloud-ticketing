ALTER TABLE venue ADD COLUMN capacity INT NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS venue_seat (
  id CHAR(36) PRIMARY KEY,
  venue_id CHAR(36) NOT NULL,
  row_label VARCHAR(20) NOT NULL,
  seat_number INT NOT NULL,
  position_x DECIMAL(10,2) NULL,
  position_y DECIMAL(10,2) NULL,
  position VARCHAR(100) NOT NULL DEFAULT '',
  status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
  UNIQUE KEY uq_venue_seat (venue_id, row_label, seat_number),
  FOREIGN KEY (venue_id) REFERENCES venue(id)
);
