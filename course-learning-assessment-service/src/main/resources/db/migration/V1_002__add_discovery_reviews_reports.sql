CREATE TABLE IF NOT EXISTS reviews (
  id BIGINT NOT NULL AUTO_INCREMENT,
  course_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  rating TINYINT NOT NULL,
  review_text TEXT,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  deleted_at TIMESTAMP(6) NULL DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_reviews_course_user (course_id, user_id),
  KEY idx_reviews_course (course_id, deleted_at, created_at),
  KEY idx_reviews_user (user_id),
  CONSTRAINT fk_reviews_course FOREIGN KEY (course_id) REFERENCES courses (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS community_reports (
  id BIGINT NOT NULL AUTO_INCREMENT,
  course_id BIGINT NOT NULL,
  reporter_user_id BIGINT NOT NULL,
  category VARCHAR(64) NOT NULL,
  details TEXT NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  resolution_notes TEXT,
  resolved_by BIGINT DEFAULT NULL,
  resolved_at TIMESTAMP(6) NULL DEFAULT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  KEY idx_reports_course_status (course_id, status),
  KEY idx_reports_reporter (reporter_user_id),
  KEY idx_reports_status (status),
  CONSTRAINT fk_reports_course FOREIGN KEY (course_id) REFERENCES courses (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS public_course_index (
  course_id BIGINT NOT NULL,
  title VARCHAR(120) NOT NULL,
  short_description VARCHAR(300) DEFAULT NULL,
  search_text TEXT NOT NULL,
  category_id BIGINT DEFAULT NULL,
  category_name VARCHAR(80) DEFAULT NULL,
  category_slug VARCHAR(100) DEFAULT NULL,
  difficulty VARCHAR(16) DEFAULT NULL,
  topics_text VARCHAR(1000) DEFAULT NULL,
  instructor_id BIGINT NOT NULL,
  instructor_name VARCHAR(120) DEFAULT NULL,
  module_count INT NOT NULL DEFAULT 0,
  lesson_count INT NOT NULL DEFAULT 0,
  thumbnail_resource_id BIGINT DEFAULT NULL,
  published_at TIMESTAMP(6) NOT NULL,
  source_fingerprint CHAR(64) NOT NULL,
  indexed_at TIMESTAMP(6) NOT NULL,
  PRIMARY KEY (course_id),
  KEY idx_public_course_cat_published (category_id, published_at),
  KEY idx_public_course_difficulty (difficulty),
  KEY idx_public_course_published_at (published_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS course_public_stats (
  course_id BIGINT NOT NULL,
  enrollment_count INT DEFAULT NULL,
  completion_count INT DEFAULT NULL,
  rating_average DECIMAL(3,2) DEFAULT NULL,
  rating_count INT DEFAULT NULL,
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (course_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS audit_logs (
  id BIGINT NOT NULL AUTO_INCREMENT,
  actor_user_id BIGINT DEFAULT NULL,
  action VARCHAR(80) NOT NULL,
  entity_type VARCHAR(80) NOT NULL,
  entity_id VARCHAR(128) DEFAULT NULL,
  details JSON NOT NULL,
  ip VARCHAR(45) DEFAULT NULL,
  user_agent VARCHAR(512) DEFAULT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  KEY idx_audit_logs_actor_created (actor_user_id, created_at),
  KEY idx_audit_logs_entity (entity_type, entity_id),
  KEY idx_audit_logs_action_created (action, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
