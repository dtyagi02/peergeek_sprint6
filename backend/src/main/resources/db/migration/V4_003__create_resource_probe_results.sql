CREATE TABLE resource_probe_results (
    id BIGINT NOT NULL AUTO_INCREMENT,
    resource_id BIGINT NOT NULL,
    checksum_sha256 CHAR(64) NOT NULL,
    kind ENUM('VIDEO', 'PDF', 'OOXML', 'IMAGE') NOT NULL,
    ok BOOLEAN NOT NULL,
    error_code VARCHAR(80) NULL,
    duration_ms BIGINT NULL,
    width INT NULL,
    height INT NULL,
    info JSON NULL,
    probed_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_resource_probe_checksum UNIQUE (resource_id, checksum_sha256),
    INDEX idx_resource_probe_resource (resource_id)
);
