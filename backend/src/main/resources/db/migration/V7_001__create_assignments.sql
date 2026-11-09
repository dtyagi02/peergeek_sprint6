CREATE TABLE assignments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    course_id BIGINT NOT NULL,
    module_id BIGINT NULL,
    lesson_id BIGINT NULL,
    kind ENUM('PROJECT', 'QUESTIONS') NOT NULL,
    title VARCHAR(255) NOT NULL,
    instructions TEXT NOT NULL,
    status ENUM('DRAFT', 'PUBLISHED', 'UNPUBLISHED') NOT NULL,
    max_points DECIMAL(7, 2) NOT NULL,
    opens_at TIMESTAMP NULL,
    due_at TIMESTAMP NULL,
    time_limit_minutes INT NULL,
    allow_late BOOLEAN NOT NULL DEFAULT FALSE,
    allow_text BOOLEAN NOT NULL DEFAULT FALSE,
    allow_files BOOLEAN NOT NULL DEFAULT FALSE,
    show_correct_answers BOOLEAN NOT NULL DEFAULT FALSE,
    attempt_count INT NOT NULL DEFAULT 0,
    published_at TIMESTAMP NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_assignments_course FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE,
    CONSTRAINT fk_assignments_user FOREIGN KEY (created_by) REFERENCES users(id),
    CONSTRAINT chk_assignments_module_lesson CHECK (module_id IS NULL OR lesson_id IS NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_assignments_course_status ON assignments (course_id, status);
CREATE INDEX idx_assignments_due_at ON assignments (due_at);
CREATE INDEX idx_assignments_lesson_id ON assignments (lesson_id);
CREATE INDEX idx_assignments_module_id ON assignments (module_id);
