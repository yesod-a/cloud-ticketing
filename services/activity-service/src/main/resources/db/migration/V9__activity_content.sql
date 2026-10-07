ALTER TABLE activity ADD COLUMN description TEXT NULL AFTER organizer;
UPDATE activity SET description = '' WHERE description IS NULL;
ALTER TABLE activity MODIFY COLUMN description TEXT NOT NULL;
