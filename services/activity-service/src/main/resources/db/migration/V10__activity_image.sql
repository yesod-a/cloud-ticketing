CREATE TABLE activity_image (
  id CHAR(36) PRIMARY KEY,
  activity_id CHAR(36) NOT NULL,
  object_key VARCHAR(500) NOT NULL,
  original_name VARCHAR(255) NOT NULL,
  content_type VARCHAR(100) NOT NULL,
  size_bytes BIGINT NOT NULL,
  image_type VARCHAR(20) NOT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_activity_image_activity FOREIGN KEY (activity_id) REFERENCES activity(id),
  CONSTRAINT uq_activity_image_object UNIQUE (object_key),
  INDEX ix_activity_image_activity (activity_id, status, image_type, sort_order)
);
