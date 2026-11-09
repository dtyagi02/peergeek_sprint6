CREATE TABLE lesson_contents (
    lesson_id BIGINT NOT NULL,
    content_format VARCHAR(32) NOT NULL,
    content_document JSON NOT NULL,
    word_count INT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (lesson_id),
    CONSTRAINT fk_lesson_contents_lesson FOREIGN KEY (lesson_id)
        REFERENCES lessons (id) ON DELETE CASCADE
);
