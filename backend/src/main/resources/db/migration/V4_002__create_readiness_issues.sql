CREATE TABLE readiness_issues (
    id BIGINT NOT NULL AUTO_INCREMENT,
    run_id BIGINT NOT NULL,
    rule_code VARCHAR(80) NOT NULL,
    severity ENUM('BLOCKING_ERROR', 'WARNING') NOT NULL,
    scope ENUM('COURSE', 'MODULE', 'LESSON', 'RESOURCE') NOT NULL,
    module_id BIGINT NULL,
    lesson_id BIGINT NULL,
    resource_id BIGINT NULL,
    module_title VARCHAR(150) NULL,
    lesson_title VARCHAR(150) NULL,
    resource_title VARCHAR(255) NULL,
    message VARCHAR(1000) NOT NULL,
    fix_hint VARCHAR(1000) NULL,
    details JSON NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_readiness_issues_run FOREIGN KEY (run_id)
        REFERENCES readiness_runs (id) ON DELETE CASCADE,
    INDEX idx_readiness_issues_run_severity (run_id, severity, id)
);
