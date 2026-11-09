CREATE TABLE assignment_questions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    assignment_id BIGINT NOT NULL,
    position INT NOT NULL,
    type ENUM('MCQ_SINGLE', 'MCQ_MULTI', 'DESCRIPTIVE', 'CODE') NOT NULL,
    prompt TEXT NOT NULL,
    points DECIMAL(7, 2) NOT NULL,
    options JSON NULL,
    code_language VARCHAR(30) NULL,
    CONSTRAINT fk_assignment_questions_assignment FOREIGN KEY (assignment_id) REFERENCES assignments(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_assignment_questions_position ON assignment_questions (assignment_id, position);
