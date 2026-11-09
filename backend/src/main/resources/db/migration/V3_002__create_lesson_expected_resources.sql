CREATE TABLE lesson_expected_resources (
    id BIGINT NOT NULL AUTO_INCREMENT,
    lesson_id BIGINT NOT NULL,
    resource_type VARCHAR(16) NOT NULL,
    min_count INT NOT NULL,
    label VARCHAR(80) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_expected_resources_lesson_type (lesson_id, resource_type)
);
