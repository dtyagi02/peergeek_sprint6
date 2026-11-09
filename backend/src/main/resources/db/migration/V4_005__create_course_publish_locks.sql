CREATE TABLE course_publish_locks (
    course_id BIGINT NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (course_id),
    CONSTRAINT fk_course_publish_locks_course FOREIGN KEY (course_id)
        REFERENCES courses (id) ON DELETE CASCADE
);
