-- Normalize legacy LESSON purpose values in resources table to LESSON_RESOURCE
UPDATE resources SET purpose = 'LESSON_RESOURCE' WHERE purpose = 'LESSON';
