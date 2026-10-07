-- Image metadata must survive activity deletion long enough for object cleanup retries.
SET @fk_name = (
  SELECT CONSTRAINT_NAME
  FROM information_schema.REFERENTIAL_CONSTRAINTS
  WHERE CONSTRAINT_SCHEMA = DATABASE()
    AND TABLE_NAME = 'activity_image'
    AND REFERENCED_TABLE_NAME = 'activity'
  LIMIT 1
);
SET @drop_fk = IF(@fk_name IS NULL, 'SELECT 1',
  CONCAT('ALTER TABLE activity_image DROP FOREIGN KEY `', @fk_name, '`'));
PREPARE drop_fk_stmt FROM @drop_fk;
EXECUTE drop_fk_stmt;
DEALLOCATE PREPARE drop_fk_stmt;
