# PeerGeek — Phase 10: Instructor Analytics Architecture & Documentation

## Overview

Phase 10 provides authenticated instructors with an isolated, read-only analytics dashboard for their own courses. The architecture leverages existing domain tables (`enrollments`, `course_progress`, `lesson_progress`, `assignments`, `submissions`, `reviews`, `courses`, `course_modules`, `lessons`) without adding new database tables, materialized views, external data warehouses, or heavy telemetry stores.

Performance and consistency are achieved through:
1. Pure SQL aggregation queries via Spring Data / JdbcTemplate using existing indexed columns.
2. Short-TTL Redis caching (`analytics:course:{courseId}`) backed by `SafeCache`.
3. Event-driven cache eviction upon after-commit transactional domain events.
4. Resilient fallback to MySQL if Redis becomes unavailable.
5. Strict course-ownership authorization on the backend rejecting cross-tenant tampering with 404 Not Found to prevent course enumeration.

---

## 1. Metric Definitions & Aggregation Logic

| Metric | Business Definition | Denominator & Exclusions | Database Tables & Query Logic |
|---|---|---|---|
| **Total Enrollments** | All-time learners enrolled in this course | Includes `ACTIVE` and `WITHDRAWN` states. Unique per user. | `SELECT COUNT(*) FROM enrollments WHERE course_id = ?` |
| **Active Learners** | Learners currently enrolled with active access | Excludes withdrawn or cancelled enrollments. | `SELECT COUNT(*) FROM enrollments WHERE course_id = ? AND status = 'ACTIVE'` |
| **Completion Rate** | Percentage of active learners who finished all lessons | Denominator: `activeLearners`. Returns `0.0%` if `activeLearners == 0`. | `SELECT COUNT(cp.enrollment_id) FROM course_progress cp JOIN enrollments e ON cp.enrollment_id = e.id WHERE e.course_id = ? AND e.status = 'ACTIVE' AND cp.completed_at IS NOT NULL` |
| **Average Assessment Score** | Mean score across all finalized graded attempts | Excludes `IN_PROGRESS` and `SUBMITTED` drafts. Only `s.status = 'GRADED'` with `a.max_points > 0`. Returns `null` if 0 graded. | `SELECT AVG((s.total_points / a.max_points) * 100.0) FROM submissions s JOIN assignments a ON s.assignment_id = a.id WHERE a.course_id = ? AND s.status = 'GRADED'` |
| **Average Rating & Count** | Average star rating and total count from verified reviews | Only non-deleted reviews. Returns `null` for average and `0` count if no reviews exist. | `SELECT AVG(rating), COUNT(rating) FROM reviews WHERE course_id = ? AND deleted_at IS NULL` |
| **Lesson-Level Drop-Off** | Retention funnel through consecutive modules and lessons | Ordered by module sort order and lesson order. For each lesson: `completedCount`, `completionPercent = completedCount * 100 / activeLearners`, and `dropOffRate = (prevCompleted - completed) * 100 / prevCompleted`. | `SELECT lp.lesson_id, COUNT(DISTINCT lp.enrollment_id) FROM lesson_progress lp JOIN enrollments e ON lp.enrollment_id = e.id WHERE e.course_id = ? AND e.status = 'ACTIVE' GROUP BY lp.lesson_id` mapped against `CourseStructureQueryService`. |
| **Assignment Submission Rate** | Rate of expected submissions received for published assignments | Denominator: `publishedAssignments.size * activeLearners`. Numerator: submissions in `('SUBMITTED', 'GRADED')`. Returns `0.0%` if denominator is 0. | Aggregated per assignment and totaled across published assignments for the course. |
| **Pending Submissions** | Submissions awaiting instructor manual grading | Excludes `IN_PROGRESS` drafts and already `GRADED` attempts. | `SELECT COUNT(s.id) FROM submissions s JOIN assignments a ON s.assignment_id = a.id WHERE a.course_id = ? AND s.status = 'SUBMITTED'` |

---

## 2. Backend Architecture

### Package Structure: `com.peergeek.analytics`

- `InstructorAnalyticsController`: REST controller for `GET /api/v1/instructor/courses/{courseId}/analytics`.
- `AnalyticsService` & `AnalyticsServiceImpl`: Orchestrates course ownership verification via `OwnershipGuard`, cache retrieval, and metric aggregation.
- `AnalyticsRepository`: Optimized SQL queries using `JdbcTemplate` joining existing schema indexes.
- `AnalyticsCacheService`: Redis wrapper leveraging `SafeCache` with course-scoped keys.
- `AnalyticsCacheInvalidationListener`: `@TransactionalEventListener(phase = AFTER_COMMIT, fallbackExecution = true)` listening for changes to enrollment, progress, assignments, submissions, reviews, and course outline.
- `dto`:
  - `InstructorCourseAnalyticsDto`: Root payload.
  - `LessonDropOffDto`: Funnel progression step.
  - `AssignmentPerformanceDto`: Breakdown per assignment.
  - `ScoreBucketDto`: Tiered distribution bucket (`0-20%`, `21-40%`, `41-60%`, `61-80%`, `81-100%`).

### Security & Ownership

- The authenticated instructor ID is obtained from `CurrentUserProvider.requireCurrentUser()`.
- Course owner is looked up via `CourseStructureQueryService.getCourseOwnerId(courseId)`.
- If the course does not exist or belongs to another instructor, `ownershipGuard.requireOwnerOrNotFound(ownerId, instructorId)` throws `NotFoundException` (HTTP 404). This prevents ID enumeration attacks and maintains indistinguishability.

---

## 3. Redis Caching & Invalidation

- **Key Format**: `analytics:course:{courseId}`
- **TTL**: 300 seconds (configurable via `peergeek.analytics.cache-ttl`).
- **Resilience**: `SafeCache.getOrLoad` catches all Redis connection and command errors, logs a warning, and immediately invokes the SQL source of truth supplier. Analytics dashboard availability is never compromised by Redis downtime.
- **Event-Driven Invalidation**:
  The cache entry is immediately evicted upon any of the following domain events:
  - `LearnerEnrolledEvent`: updates enrollments count and denominators.
  - `LearnerWithdrawnEvent`: updates active learners and rates.
  - `LessonCompletedEvent`: updates lesson drop-off funnel and completion rate.
  - `SubmissionSubmittedEvent`: updates pending reviews count and assignment submission rate.
  - `SubmissionGradedEvent`: updates average assessment score, score distribution buckets, and pending count.
  - `AssignmentPublishedEvent`: updates published assignment list and submission rate denominator.
  - `ReviewCreatedEvent`: updates course average rating and review count.
  - `CourseContentChangedEvent`: updates course title and lesson outline structure.

---

## 4. Frontend Analytics Dashboard

### Location: `frontend/src/features/analytics/`

- `routes.js`: Protected route at `/instructor/courses/:courseId/analytics`.
- `slots.js`: Registered contribution `analytics-course-editor-header` into `course-editor.header-actions` slot.
- `analyticsApi.js`: Calls `GET /instructor/courses/{courseId}/analytics`.
- `InstructorAnalyticsPage.jsx`:
  - **7 KPI Cards**: Total enrollments, Active learners, Completion rate (with visual bar), Average assessment score, Average rating (with star display), Assignment submission rate, Pending submissions (with direct link to submissions queue).
  - **Lesson-Level Drop-Off Funnel**: Sequential progression bars showing learners completed and percentage drop-off from previous lesson.
  - **Score Distribution Chart**: 5-tier colored distribution bars for graded assessment performance.
  - **Assignment Breakdown Table**: Matrix of each assignment's status, max points, submission volume, submission rate, and average score.
  - **Empty, Loading & Error States**: Responsive pulsing skeletons, clean empty state for new courses, and error alerts with retry functionality.

---

## 5. Verification Summary

- **Backend**: `InstructorAnalyticsIntegrationTest` (6 tests) covering all 8 metrics, empty course defaults, IDOR protection, 401 unauthenticated rejection, Redis cache hits/eviction, and SafeCache Redis failure degradation. Passed with 0 failures in MySQL 8.4 Testcontainers.
- **Frontend**: `analytics.test.jsx` (7 tests) covering KPI rendering, charts, empty states, error retry, slot contribution, and query key isolation. Passed with 0 failures in Vitest.
- **Overall Suite**: All 18 frontend test suites (116 tests) passing green.
