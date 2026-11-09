CREATE TABLE submissions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    assignment_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    status ENUM('IN_PROGRESS', 'SUBMITTED', 'GRADED') NOT NULL,
    started_at TIMESTAMP NOT NULL,
    deadline_at TIMESTAMP NULL,
    submitted_at TIMESTAMP NULL,
    late BOOLEAN NOT NULL DEFAULT FALSE,
    auto_submitted BOOLEAN NOT NULL DEFAULT FALSE,
    auto_graded BOOLEAN NOT NULL DEFAULT FALSE,
    text_answer TEXT NULL,
    marks DECIMAL(7, 2) NULL,
    total_points DECIMAL(7, 2) NULL,
    overall_feedback TEXT NULL,
    graded_by BIGINT NULL,
    graded_at TIMESTAMP NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_submissions_assignment FOREIGN KEY (assignment_id) REFERENCES assignments(id) ON DELETE CASCADE,
    CONSTRAINT fk_submissions_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_submissions_graded_by FOREIGN KEY (graded_by) REFERENCES users(id),
    CONSTRAINT uq_submissions_assignment_user UNIQUE (assignment_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_submissions_assignment_status_submitted ON submissions (assignment_id, status, submitted_at);
CREATE INDEX idx_submissions_user_status ON submissions (user_id, status);
CREATE INDEX idx_submissions_status_deadline ON submissions (status, deadline_at);
