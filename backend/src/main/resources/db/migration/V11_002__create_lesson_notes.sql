CREATE TABLE lesson_notes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,
    lesson_id BIGINT NOT NULL,
    content MEDIUMTEXT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_lesson_notes_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_lesson_notes_course FOREIGN KEY (course_id) REFERENCES courses (id),
    CONSTRAINT fk_lesson_notes_lesson FOREIGN KEY (lesson_id) REFERENCES lessons (id),
    CONSTRAINT uq_lesson_notes_user_lesson UNIQUE (user_id, lesson_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_lesson_notes_user_course ON lesson_notes (user_id, course_id);
CREATE INDEX idx_lesson_notes_lesson ON lesson_notes (lesson_id);
