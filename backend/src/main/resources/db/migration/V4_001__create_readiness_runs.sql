CREATE TABLE readiness_runs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    course_id BIGINT NOT NULL,
    requested_by BIGINT NOT NULL,
    status ENUM('RUNNING', 'COMPLETED', 'FAILED') NOT NULL,
    fingerprint CHAR(64) NOT NULL,
    blocking_count INT NOT NULL DEFAULT 0,
    warning_count INT NOT NULL DEFAULT 0,
    failure_reason VARCHAR(1000) NULL,
    started_at TIMESTAMP(6) NOT NULL,
    finished_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_readiness_runs_course FOREIGN KEY (course_id)
        REFERENCES courses (id) ON DELETE CASCADE,
    CONSTRAINT fk_readiness_runs_requested_by FOREIGN KEY (requested_by)
        REFERENCES users (id),
    INDEX idx_readiness_runs_course_created (course_id, created_at)
);
