CREATE TABLE submission_answers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    submission_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    answer_text TEXT NULL,
    selected_option_ids JSON NULL,
    auto_points DECIMAL(7, 2) NULL,
    points DECIMAL(7, 2) NULL,
    feedback TEXT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_submission_answers_submission FOREIGN KEY (submission_id) REFERENCES submissions(id) ON DELETE CASCADE,
    CONSTRAINT uq_submission_answers_submission_question UNIQUE (submission_id, question_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
