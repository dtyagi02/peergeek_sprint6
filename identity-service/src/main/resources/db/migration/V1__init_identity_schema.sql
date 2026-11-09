-- Phase 6: Identity Service Initial Schema

CREATE TABLE IF NOT EXISTS users (
  id BIGINT NOT NULL AUTO_INCREMENT,
  email VARCHAR(254) NOT NULL,
  password_hash VARCHAR(100) NOT NULL,
  display_name VARCHAR(100) NOT NULL,
  headline VARCHAR(160) DEFAULT NULL,
  bio VARCHAR(2000) DEFAULT NULL,
  role VARCHAR(16) NOT NULL DEFAULT 'USER',
  last_mode VARCHAR(16) NOT NULL DEFAULT 'STUDENT',
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  email_verified TINYINT(1) NOT NULL DEFAULT 1,
  username VARCHAR(30) NOT NULL,
  avatar_key VARCHAR(255) DEFAULT NULL,
  avatar_url VARCHAR(500) DEFAULT NULL,
  skills TEXT,
  interests TEXT,
  teaching_expertise TEXT,
  github_url VARCHAR(255) DEFAULT NULL,
  linkedin_url VARCHAR(255) DEFAULT NULL,
  portfolio_url VARCHAR(255) DEFAULT NULL,
  website_url VARCHAR(255) DEFAULT NULL,
  show_education TINYINT(1) NOT NULL DEFAULT 1,
  show_social_links TINYINT(1) NOT NULL DEFAULT 1,
  show_learning_history TINYINT(1) NOT NULL DEFAULT 1,
  PRIMARY KEY (id),
  UNIQUE KEY uk_users_email (email),
  UNIQUE KEY uk_users_username (username),
  CONSTRAINT chk_users_last_mode CHECK (last_mode IN ('STUDENT', 'INSTRUCTOR')),
  CONSTRAINT chk_users_role CHECK (role IN ('USER', 'ADMIN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS refresh_tokens (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  token_hash CHAR(64) NOT NULL,
  family_id CHAR(36) NOT NULL,
  expires_at TIMESTAMP(6) NOT NULL,
  revoked_at TIMESTAMP(6) NULL DEFAULT NULL,
  replaced_by BIGINT DEFAULT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  user_agent VARCHAR(512) DEFAULT NULL,
  ip VARCHAR(45) DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_refresh_tokens_hash (token_hash),
  KEY fk_refresh_tokens_replaced_by (replaced_by),
  KEY idx_refresh_tokens_user_family (user_id, family_id),
  KEY idx_refresh_tokens_expiry (expires_at),
  CONSTRAINT fk_refresh_tokens_replaced_by FOREIGN KEY (replaced_by) REFERENCES refresh_tokens (id),
  CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS auth_otps (
  id BIGINT NOT NULL AUTO_INCREMENT,
  email VARCHAR(254) NOT NULL,
  user_id BIGINT DEFAULT NULL,
  purpose VARCHAR(32) NOT NULL,
  otp_hash VARCHAR(100) NOT NULL,
  expires_at TIMESTAMP(6) NOT NULL,
  attempts INT NOT NULL DEFAULT 0,
  consumed_at TIMESTAMP(6) NULL DEFAULT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  reset_token_hash VARCHAR(100) DEFAULT NULL,
  reset_token_expires_at TIMESTAMP(6) NULL DEFAULT NULL,
  verified_at TIMESTAMP(6) NULL DEFAULT NULL,
  PRIMARY KEY (id),
  KEY fk_auth_otps_user (user_id),
  KEY idx_auth_otps_email_purpose (email, purpose, created_at),
  KEY idx_auth_otps_reset_token (reset_token_hash),
  CONSTRAINT fk_auth_otps_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS password_reset_otps (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  otp_hash VARCHAR(100) NOT NULL,
  expires_at TIMESTAMP(6) NOT NULL,
  attempts INT NOT NULL DEFAULT 0,
  consumed_at TIMESTAMP(6) NULL DEFAULT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_password_reset_otps_user_created (user_id, created_at),
  CONSTRAINT fk_password_reset_otps_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS user_educations (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  institution VARCHAR(200) NOT NULL,
  degree VARCHAR(100) DEFAULT NULL,
  field_of_study VARCHAR(100) DEFAULT NULL,
  start_year INT DEFAULT NULL,
  end_year INT DEFAULT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_user_educations_user (user_id),
  CONSTRAINT fk_user_educations_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS user_projects (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  title VARCHAR(150) NOT NULL,
  description TEXT,
  technologies VARCHAR(500) DEFAULT NULL,
  project_url VARCHAR(500) DEFAULT NULL,
  repo_url VARCHAR(500) DEFAULT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_user_projects_user (user_id),
  CONSTRAINT fk_user_projects_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS outbox_events (
  id BIGINT NOT NULL AUTO_INCREMENT,
  event_id VARCHAR(64) NOT NULL,
  event_type VARCHAR(128) NOT NULL,
  topic VARCHAR(128) NOT NULL,
  payload JSON NOT NULL,
  published TINYINT(1) NOT NULL DEFAULT 0,
  retry_count INT NOT NULL DEFAULT 0,
  created_at DATETIME(6) NOT NULL,
  published_at DATETIME(6) NULL,
  error_message TEXT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_outbox_event_id (event_id),
  KEY idx_outbox_published (published, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

