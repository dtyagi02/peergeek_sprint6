CREATE TABLE link_check_results (
    id BIGINT NOT NULL AUTO_INCREMENT,
    resource_id BIGINT NOT NULL,
    url_sha256 CHAR(64) NOT NULL,
    outcome VARCHAR(32) NOT NULL,
    http_status INT NULL,
    checked_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_link_check_resource UNIQUE (resource_id),
    INDEX idx_link_check_url_checked (url_sha256, checked_at)
);
