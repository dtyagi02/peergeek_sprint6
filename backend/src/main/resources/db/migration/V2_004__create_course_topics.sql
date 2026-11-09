CREATE TABLE course_topics (
    id BIGINT NOT NULL AUTO_INCREMENT,
    course_id BIGINT NOT NULL,
    position INT NOT NULL,
    name VARCHAR(40) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_course_topics_course FOREIGN KEY (course_id)
        REFERENCES courses (id) ON DELETE CASCADE,
    INDEX idx_course_topics_course (course_id)
);
