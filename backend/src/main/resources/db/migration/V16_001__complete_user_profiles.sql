ALTER TABLE users
    ADD COLUMN avatar_key VARCHAR(255) NULL,
    ADD COLUMN avatar_url VARCHAR(500) NULL,
    ADD COLUMN skills TEXT NULL,
    ADD COLUMN interests TEXT NULL,
    ADD COLUMN teaching_expertise TEXT NULL,
    ADD COLUMN github_url VARCHAR(255) NULL,
    ADD COLUMN linkedin_url VARCHAR(255) NULL,
    ADD COLUMN portfolio_url VARCHAR(255) NULL,
    ADD COLUMN website_url VARCHAR(255) NULL,
    ADD COLUMN show_education BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN show_social_links BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN show_learning_history BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE user_educations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    institution VARCHAR(200) NOT NULL,
    degree VARCHAR(100) NULL,
    field_of_study VARCHAR(100) NULL,
    start_year INT NULL,
    end_year INT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_user_educations_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_user_educations_user ON user_educations (user_id);

CREATE TABLE user_projects (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(150) NOT NULL,
    description TEXT NULL,
    technologies VARCHAR(500) NULL,
    project_url VARCHAR(500) NULL,
    repo_url VARCHAR(500) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_user_projects_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_user_projects_user ON user_projects (user_id);
