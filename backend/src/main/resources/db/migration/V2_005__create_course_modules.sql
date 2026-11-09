CREATE TABLE course_modules (
    id BIGINT NOT NULL AUTO_INCREMENT,
    course_id BIGINT NOT NULL,
    title VARCHAR(120) NOT NULL,
    description VARCHAR(1000) NULL,
    position INT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_course_modules_course FOREIGN KEY (course_id)
        REFERENCES courses (id) ON DELETE CASCADE,
    INDEX idx_course_modules_course_position (course_id, position)
);
