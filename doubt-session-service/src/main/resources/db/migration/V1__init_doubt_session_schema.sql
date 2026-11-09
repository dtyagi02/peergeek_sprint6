-- Phase 6: Doubt Session Service Initial Schema

CREATE TABLE IF NOT EXISTS course_doubt_session_settings (
  id BIGINT NOT NULL AUTO_INCREMENT,
  course_id BIGINT NOT NULL,
  instructor_id BIGINT NOT NULL,
  enabled TINYINT(1) NOT NULL DEFAULT 0,
  available TINYINT(1) NOT NULL DEFAULT 0,
  hourly_rate_pgc INT NOT NULL DEFAULT 600,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uq_session_settings_course (course_id),
  KEY idx_session_settings_instructor (instructor_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS doubt_sessions (
  id BIGINT NOT NULL AUTO_INCREMENT,
  session_uid VARCHAR(36) NOT NULL,
  course_id BIGINT NOT NULL,
  instructor_id BIGINT NOT NULL,
  student_id BIGINT NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'REQUESTED',
  hourly_rate_pgc INT NOT NULL,
  max_duration_minutes INT NOT NULL,
  reserved_amount DECIMAL(19,2) NOT NULL,
  final_charge DECIMAL(19,2) DEFAULT NULL,
  refunded_amount DECIMAL(19,2) DEFAULT NULL,
  billable_seconds INT NOT NULL DEFAULT 0,
  requested_at TIMESTAMP(6) NOT NULL,
  expires_at TIMESTAMP(6) NOT NULL,
  accepted_at TIMESTAMP(6) NULL DEFAULT NULL,
  connect_deadline_at TIMESTAMP(6) NULL DEFAULT NULL,
  started_at TIMESTAMP(6) NULL DEFAULT NULL,
  ended_at TIMESTAMP(6) NULL DEFAULT NULL,
  livekit_room_name VARCHAR(128) NOT NULL,
  student_connected TINYINT(1) NOT NULL DEFAULT 0,
  instructor_connected TINYINT(1) NOT NULL DEFAULT 0,
  last_both_connected_at TIMESTAMP(6) NULL DEFAULT NULL,
  last_disconnected_at TIMESTAMP(6) NULL DEFAULT NULL,
  reconnect_deadline_at TIMESTAMP(6) NULL DEFAULT NULL,
  end_reason VARCHAR(64) DEFAULT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uq_doubt_sessions_uid (session_uid),
  KEY idx_doubt_sessions_instructor_status (instructor_id, status),
  KEY idx_doubt_sessions_student_status (student_id, status),
  KEY idx_doubt_sessions_course (course_id),
  KEY idx_doubt_sessions_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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

