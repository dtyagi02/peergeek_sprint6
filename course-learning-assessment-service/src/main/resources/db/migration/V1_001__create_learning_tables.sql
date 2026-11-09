-- V1_001__create_learning_tables.sql
-- Autonomous Course, Learning & Assessment Microservice Schema

-- 1. Read-Model User Profiles
CREATE TABLE IF NOT EXISTS learning_user_profiles (
    id BIGINT NOT NULL,
    email VARCHAR(255) NOT NULL,
    username VARCHAR(100),
    display_name VARCHAR(150),
    avatar_url VARCHAR(500),
    role VARCHAR(50) NOT NULL DEFAULT 'USER',
    mode VARCHAR(50) NOT NULL DEFAULT 'STUDENT',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_lup_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. Course Categories
CREATE TABLE IF NOT EXISTS categories (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(255) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    active TINYINT(1) NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_categories_name (name),
    UNIQUE KEY uq_categories_slug (slug)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. Courses
CREATE TABLE IF NOT EXISTS courses (
    id BIGINT NOT NULL AUTO_INCREMENT,
    owner_id BIGINT NOT NULL,
    title VARCHAR(500) NOT NULL,
    description TEXT,
    category_id BIGINT,
    difficulty VARCHAR(32),
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    thumbnail_resource_id BIGINT,
    published_at TIMESTAMP NULL,
    content_updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_courses_owner (owner_id),
    KEY idx_courses_status (status),
    KEY idx_courses_category (category_id),
    CONSTRAINT fk_courses_category FOREIGN KEY (category_id) REFERENCES categories (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. Course Topics
CREATE TABLE IF NOT EXISTS course_topics (
    id BIGINT NOT NULL AUTO_INCREMENT,
    course_id BIGINT NOT NULL,
    position INT NOT NULL DEFAULT 0,
    name VARCHAR(255) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_course_topics_course (course_id),
    CONSTRAINT fk_course_topics_course FOREIGN KEY (course_id) REFERENCES courses (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. Course Modules
CREATE TABLE IF NOT EXISTS course_modules (
    id BIGINT NOT NULL AUTO_INCREMENT,
    course_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    description VARCHAR(1000),
    position INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_course_modules_course (course_id),
    CONSTRAINT fk_course_modules_course FOREIGN KEY (course_id) REFERENCES courses (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6. Lessons
CREATE TABLE IF NOT EXISTS lessons (
    id BIGINT NOT NULL AUTO_INCREMENT,
    module_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    summary VARCHAR(1000),
    position INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_lessons_module (module_id),
    CONSTRAINT fk_lessons_module FOREIGN KEY (module_id) REFERENCES course_modules (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 7. Lesson Contents
CREATE TABLE IF NOT EXISTS lesson_contents (
    lesson_id BIGINT NOT NULL,
    content_format VARCHAR(64) NOT NULL DEFAULT 'DOCUMENT_JSON',
    content_document JSON NOT NULL,
    word_count INT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (lesson_id),
    CONSTRAINT fk_lesson_contents_lesson FOREIGN KEY (lesson_id) REFERENCES lessons (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 8. Resources
CREATE TABLE IF NOT EXISTS resources (
    id BIGINT NOT NULL AUTO_INCREMENT,
    course_id BIGINT NOT NULL,
    lesson_id BIGINT,
    uploaded_by BIGINT NOT NULL,
    purpose VARCHAR(64) NOT NULL,
    type VARCHAR(64) NOT NULL,
    title VARCHAR(255) NOT NULL,
    original_filename VARCHAR(255),
    object_key VARCHAR(500),
    mime_type VARCHAR(128),
    size_bytes BIGINT,
    checksum_sha256 VARCHAR(64),
    status VARCHAR(32) NOT NULL DEFAULT 'READY',
    processing_attempts INT NOT NULL DEFAULT 0,
    failure_reason VARCHAR(1000),
    external_url VARCHAR(2048),
    position INT NOT NULL DEFAULT 0,
    deleted_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_resources_object_key (object_key),
    KEY idx_resources_course (course_id),
    KEY idx_resources_lesson (lesson_id),
    KEY idx_resources_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 9. Lesson Expected Resources
CREATE TABLE IF NOT EXISTS lesson_expected_resources (
    id BIGINT NOT NULL AUTO_INCREMENT,
    lesson_id BIGINT NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    min_count INT NOT NULL DEFAULT 1,
    label VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_expected_res_lesson (lesson_id),
    CONSTRAINT fk_expected_res_lesson FOREIGN KEY (lesson_id) REFERENCES lessons (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 10. Readiness Runs & Issues
CREATE TABLE IF NOT EXISTS readiness_runs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    course_id BIGINT NOT NULL,
    requested_by BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    fingerprint VARCHAR(64) NOT NULL,
    blocking_count INT NOT NULL DEFAULT 0,
    warning_count INT NOT NULL DEFAULT 0,
    failure_reason VARCHAR(500),
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_readiness_runs_course (course_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS readiness_issues (
    id BIGINT NOT NULL AUTO_INCREMENT,
    run_id BIGINT NOT NULL,
    rule_code VARCHAR(64) NOT NULL,
    severity VARCHAR(32) NOT NULL,
    scope VARCHAR(32) NOT NULL,
    module_id BIGINT,
    lesson_id BIGINT,
    resource_id BIGINT,
    module_title VARCHAR(255),
    lesson_title VARCHAR(255),
    resource_title VARCHAR(255),
    message VARCHAR(1000) NOT NULL,
    fix_hint VARCHAR(1000),
    details JSON,
    PRIMARY KEY (id),
    KEY idx_readiness_issues_run (run_id),
    CONSTRAINT fk_readiness_issues_run FOREIGN KEY (run_id) REFERENCES readiness_runs (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 11. Enrollments
CREATE TABLE IF NOT EXISTS enrollments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    enrolled_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    withdrawn_at TIMESTAMP NULL,
    last_lesson_id BIGINT,
    last_accessed_at TIMESTAMP NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_enrollments_user_course (user_id, course_id),
    KEY idx_enrollments_user (user_id),
    KEY idx_enrollments_course (course_id),
    CONSTRAINT fk_enrollments_course FOREIGN KEY (course_id) REFERENCES courses (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 12. Lesson Progress
CREATE TABLE IF NOT EXISTS lesson_progress (
    id BIGINT NOT NULL AUTO_INCREMENT,
    enrollment_id BIGINT NOT NULL,
    lesson_id BIGINT NOT NULL,
    completed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_lesson_progress_enrollment_lesson (enrollment_id, lesson_id),
    KEY idx_lesson_progress_enrollment (enrollment_id),
    KEY idx_lesson_progress_lesson (lesson_id),
    CONSTRAINT fk_lesson_progress_enrollment FOREIGN KEY (enrollment_id) REFERENCES enrollments (id) ON DELETE CASCADE,
    CONSTRAINT fk_lesson_progress_lesson FOREIGN KEY (lesson_id) REFERENCES lessons (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 13. Course Progress
CREATE TABLE IF NOT EXISTS course_progress (
    enrollment_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,
    total_lessons INT NOT NULL DEFAULT 0,
    completed_lessons INT NOT NULL DEFAULT 0,
    percent TINYINT NOT NULL DEFAULT 0,
    completed_at TIMESTAMP NULL,
    stale TINYINT(1) NOT NULL DEFAULT 0,
    computed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (enrollment_id),
    KEY idx_course_progress_course (course_id),
    CONSTRAINT fk_course_progress_enrollment FOREIGN KEY (enrollment_id) REFERENCES enrollments (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 14. Assignments
CREATE TABLE IF NOT EXISTS assignments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    course_id BIGINT NOT NULL,
    module_id BIGINT,
    lesson_id BIGINT,
    kind VARCHAR(32) NOT NULL DEFAULT 'MCQ',
    title VARCHAR(255) NOT NULL,
    instructions TEXT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    max_points DECIMAL(6,2) NOT NULL DEFAULT 100.00,
    opens_at TIMESTAMP NULL,
    due_at TIMESTAMP NULL,
    time_limit_minutes INT,
    allow_late TINYINT(1) NOT NULL DEFAULT 0,
    allow_text TINYINT(1) NOT NULL DEFAULT 0,
    allow_files TINYINT(1) NOT NULL DEFAULT 0,
    show_correct_answers TINYINT(1) NOT NULL DEFAULT 0,
    attempt_count INT NOT NULL DEFAULT 0,
    published_at TIMESTAMP NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_assignments_course (course_id),
    KEY idx_assignments_status (status),
    CONSTRAINT fk_assignments_course FOREIGN KEY (course_id) REFERENCES courses (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 15. Assignment Questions
CREATE TABLE IF NOT EXISTS assignment_questions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    assignment_id BIGINT NOT NULL,
    position INT NOT NULL DEFAULT 0,
    type VARCHAR(32) NOT NULL DEFAULT 'MCQ',
    prompt TEXT NOT NULL,
    points DECIMAL(6,2) NOT NULL DEFAULT 1.00,
    options JSON,
    code_language VARCHAR(64),
    PRIMARY KEY (id),
    KEY idx_assignment_questions_assignment (assignment_id),
    CONSTRAINT fk_questions_assignment FOREIGN KEY (assignment_id) REFERENCES assignments (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 16. Submissions
CREATE TABLE IF NOT EXISTS submissions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    assignment_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deadline_at TIMESTAMP NULL,
    submitted_at TIMESTAMP NULL,
    late TINYINT(1) NOT NULL DEFAULT 0,
    auto_submitted TINYINT(1) NOT NULL DEFAULT 0,
    auto_graded TINYINT(1) NOT NULL DEFAULT 0,
    text_answer TEXT,
    marks DECIMAL(6,2),
    total_points DECIMAL(6,2),
    overall_feedback TEXT,
    graded_by BIGINT,
    graded_at TIMESTAMP NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_submissions_assignment_user (assignment_id, user_id),
    KEY idx_submissions_assignment (assignment_id),
    KEY idx_submissions_user (user_id),
    CONSTRAINT fk_submissions_assignment FOREIGN KEY (assignment_id) REFERENCES assignments (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 17. Submission Answers
CREATE TABLE IF NOT EXISTS submission_answers (
    id BIGINT NOT NULL AUTO_INCREMENT,
    submission_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    answer_text TEXT,
    selected_option_ids JSON,
    auto_points DECIMAL(6,2),
    points DECIMAL(6,2),
    feedback TEXT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_submission_answers_submission (submission_id),
    CONSTRAINT fk_answers_submission FOREIGN KEY (submission_id) REFERENCES submissions (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 18. Submission Files
CREATE TABLE IF NOT EXISTS submission_files (
    id BIGINT NOT NULL AUTO_INCREMENT,
    submission_id BIGINT NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    object_key VARCHAR(500) NOT NULL,
    mime_type VARCHAR(128) NOT NULL,
    size_bytes BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'READY',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_submission_files_submission (submission_id),
    CONSTRAINT fk_files_submission FOREIGN KEY (submission_id) REFERENCES submissions (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 19. Certificates
CREATE TABLE IF NOT EXISTS certificates (
    id BIGINT NOT NULL AUTO_INCREMENT,
    certificate_number VARCHAR(64) NOT NULL,
    verification_code VARCHAR(128) NOT NULL,
    user_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,
    enrollment_id BIGINT NOT NULL,
    student_name VARCHAR(255) NOT NULL,
    course_title VARCHAR(500) NOT NULL,
    instructor_name VARCHAR(255) NOT NULL,
    issued_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    file_key VARCHAR(500),
    status VARCHAR(32) NOT NULL DEFAULT 'ISSUED',
    revoked_at TIMESTAMP NULL,
    revocation_reason VARCHAR(500),
    revoked_by BIGINT,
    failure_reason VARCHAR(1000),
    retry_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_certificates_number (certificate_number),
    UNIQUE KEY uq_certificates_verification (verification_code),
    UNIQUE KEY uq_certificates_enrollment (enrollment_id),
    KEY idx_certificates_user (user_id),
    KEY idx_certificates_course (course_id),
    CONSTRAINT fk_certificates_course FOREIGN KEY (course_id) REFERENCES courses (id) ON DELETE CASCADE,
    CONSTRAINT fk_certificates_enrollment FOREIGN KEY (enrollment_id) REFERENCES enrollments (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 20. Bookmarks
CREATE TABLE IF NOT EXISTS bookmarks (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    target_type VARCHAR(32) NOT NULL DEFAULT 'COURSE',
    course_id BIGINT NOT NULL,
    lesson_id BIGINT,
    target_key VARCHAR(128) GENERATED ALWAYS AS (CONCAT(target_type, ':', course_id, IF(lesson_id IS NOT NULL, CONCAT(':', lesson_id), ''))) STORED,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_bookmarks_user (user_id),
    KEY idx_bookmarks_course (course_id),
    KEY idx_bookmarks_target_key (user_id, target_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 21. Lesson Notes
CREATE TABLE IF NOT EXISTS lesson_notes (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,
    lesson_id BIGINT NOT NULL,
    content MEDIUMTEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_notes_user_lesson (user_id, lesson_id),
    KEY idx_notes_user_course (user_id, course_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 22. Transactional Outbox Events
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
