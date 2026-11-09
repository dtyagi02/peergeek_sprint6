CREATE TABLE course_public_stats (
    course_id BIGINT NOT NULL,
    enrollment_count INT NULL,
    completion_count INT NULL,
    rating_average DECIMAL(3, 2) NULL,
    rating_count INT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (course_id)
);
