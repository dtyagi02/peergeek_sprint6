CREATE TABLE course_progress (
    enrollment_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,
    total_lessons INT NOT NULL DEFAULT 0,
    completed_lessons INT NOT NULL DEFAULT 0,
    percent TINYINT NOT NULL DEFAULT 0,
    completed_at TIMESTAMP(6) NULL,
    stale BOOLEAN NOT NULL DEFAULT FALSE,
    computed_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (enrollment_id),
    CONSTRAINT fk_course_progress_enrollment FOREIGN KEY (enrollment_id) REFERENCES enrollments (id) ON DELETE CASCADE,
    INDEX idx_course_progress_course_stale (course_id, stale)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
