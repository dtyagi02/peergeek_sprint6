ALTER TABLE certificates
    ADD COLUMN revoked_by BIGINT NULL AFTER revocation_reason,
    ADD COLUMN failure_reason VARCHAR(1000) NULL AFTER revoked_by,
    ADD COLUMN retry_count INT NOT NULL DEFAULT 0 AFTER failure_reason;

