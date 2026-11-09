CREATE TABLE enrollments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,
    status ENUM('ACTIVE', 'WITHDRAWN') NOT NULL DEFAULT 'ACTIVE',
    enrolled_at TIMESTAMP(6) NOT NULL,
    withdrawn_at TIMESTAMP(6) NULL,
    last_lesson_id BIGINT NULL,
    last_accessed_at TIMESTAMP(6) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_enrollments_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_enrollments_course FOREIGN KEY (course_id) REFERENCES courses (id),
    CONSTRAINT uq_enrollments_user_course UNIQUE (user_id, course_id),
    INDEX idx_enrollments_user_status_updated (user_id, status, updated_at),
    INDEX idx_enrollments_course_status (course_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
