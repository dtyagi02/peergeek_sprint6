ALTER TABLE resources
    ADD COLUMN processing_attempts INT NOT NULL DEFAULT 0 AFTER status;
