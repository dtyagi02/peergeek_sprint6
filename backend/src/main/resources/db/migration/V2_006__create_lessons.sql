CREATE TABLE lessons (
    id BIGINT NOT NULL AUTO_INCREMENT,
    module_id BIGINT NOT NULL,
    title VARCHAR(150) NOT NULL,
    summary VARCHAR(500) NULL,
    position INT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_lessons_module FOREIGN KEY (module_id)
        REFERENCES course_modules (id) ON DELETE CASCADE,
    INDEX idx_lessons_module_position (module_id, position)
);
