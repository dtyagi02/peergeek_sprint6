


-- Phase 12: Production Hardening Performance Indexes
-- Targeted composite indexes derived from query-plan analysis across analytics, discovery, and learner workflows.

-- 1. Optimizes instructor analytics completion rate queries
CREATE INDEX idx_course_progress_course_completed
    ON course_progress (course_id, completed_at);

-- 2. Optimizes category discovery and catalog filtering for published courses
CREATE INDEX idx_courses_category_status
    ON courses (category_id, status);
