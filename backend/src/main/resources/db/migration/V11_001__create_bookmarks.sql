CREATE TABLE bookmarks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    target_type ENUM('COURSE', 'LESSON') NOT NULL,
    course_id BIGINT NOT NULL,
    lesson_id BIGINT NULL,
    target_key VARCHAR(64) GENERATED ALWAYS AS (
        CASE
            WHEN target_type = 'COURSE' THEN CONCAT('C:', course_id)
            ELSE CONCAT('L:', lesson_id)
        END
    ) STORED,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_bookmarks_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_bookmarks_course FOREIGN KEY (course_id) REFERENCES courses (id),
    CONSTRAINT chk_bookmarks_target CHECK (
        (target_type = 'COURSE' AND lesson_id IS NULL) OR
        (target_type = 'LESSON' AND lesson_id IS NOT NULL)
    ),
    CONSTRAINT uq_bookmarks_user_target UNIQUE (user_id, target_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_bookmarks_user_created ON bookmarks (user_id, created_at);
CREATE INDEX idx_bookmarks_course ON bookmarks (course_id);
CREATE INDEX idx_bookmarks_lesson ON bookmarks (lesson_id);
