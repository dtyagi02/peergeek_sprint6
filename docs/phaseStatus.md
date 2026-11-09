# PeerGeek Phase Status & Roadmap

Last updated: 2026-10-20

## Summary of Phases

| Phase | Title | Status | Primary Verification |
| --- | --- | --- | --- |
| **Phase 0** | Infrastructure, Contracts & Base Monolith | **VERIFIED** | Baseline migrations, MySQL/Redis/MinIO Testcontainers, S3Client, SafeCache |
| **Phase 1** | Authentication, Profiles & Audit | **VERIFIED** | BCrypt 12, JWT HMAC-256, HttpOnly refresh cookie, anti-enumeration, audit logs |
| **Phase 2** | Course Authoring & Content Model | **VERIFIED** | Categories, courses, modules, lessons, TipTap JSON validation, ordering locks |
| **Phase 3** | File Storage & Direct S3 Uploads | **VERIFIED** | Presigned S3 PUT/GET, thumbnail processing, async worker, voter access chain |
| **Phase 4** | Readiness Engine, Gated Publishing & Preview | **VERIFIED** | Fingerprint engine, async probe worker, SSRF link checker, preview assembler, dynamic rules injection, V14_001 resource migration, actionable UI |
| **Phase 5** | Course Discovery & Public Preview | **VERIFIED** | Denormalized index, substring search, category counts, weak ETag preview |
| **Phase 6** | Enrollment & Learner Experience Foundation | **VERIFIED** | Free instant enrollment, soft withdrawal, progress calculation, Continue Learning |
| **Phase 7** | Assignments, Submissions & Assessments | **VERIFIED** | MCQ-only instructor workflow, authoritative timer, draft autosave, auto-grading |
| **Phase 8** | Reviews, Ratings & Community Content Reporting | **VERIFIED** | V8_001 reviews, V8_002 reports, ReviewCreatedEvent, ReportCreatedEvent, 7/7 backend tests, Vitest UI |
| **Phase 9** | Notifications System | **VERIFIED** | V9_001 notifications, event listeners, deadline job, header bell, dropdown, /notifications, 6/6 backend tests, 5/5 Vitest |
| **Phase 10** | Instructor Analytics Dashboard | **VERIFIED** | Read-only aggregation over existing tables, SafeCache Redis keys, 6/6 backend tests, 7/7 Vitest UI tests |
| **Phase 11** | Learner Extras (Bookmarks & Notes) | **VERIFIED** | V11_001 bookmarks, V11_002 lesson notes, private lesson notes panel, bookmarks page & toggle, 8/8 backend tests, 10/10 Vitest tests |
| **Phase 12** | Production Hardening, E2E Verification & Release | **VERIFIED** | Full 13-step core user flow E2E test, 30/30 backend integration tests, endpoint auth matrix, rate limiting, security headers, load testing, disaster recovery drill, production Docker images, CI/CD pipeline |
| **Phase 13** | Email OTP Verification & Secure Password Reset | **VERIFIED** | V13_001 email_verified & auth_otps, 6-digit BCrypt OTP, 32-byte SHA-256 resetToken, 60s cooldown, 10m TTL, max 5 attempts burn, 403 EMAIL_NOT_VERIFIED, 15/15 AuthIntegrationTest, 13/13 CoreUserFlowIntegrationTest, 12/12 Vitest auth tests |
| **Phase 14** | Admin Monitoring Dashboard & Moderation | **VERIFIED** | RBAC, User block/unblock, Course moderation, Reports review & inspection, 16/16 backend tests, 14/14 Vitest tests |

---

## Detailed Phase 7 Status: Assignments, Submissions & MCQ Assessments

### Objective
Allow instructors to create, configure, and publish MCQ-only assessments (single choice and multiple choice) for enrolled learners, with authoritative timers, draft autosaving, automatic scoring upon submission, and result display with answer breakdown. Ensure the instructor workflow is restricted strictly to MCQ questions (`MCQ_SINGLE` and `MCQ_MULTI`), while safely preserving historical records.

### Verification Checklist & Evidence

| Component / Requirement | Status | Verification Evidence |
| --- | --- | --- |
| **MCQ-Only Assignment Creation** | VERIFIED | `InstructorAssignmentService.createAssignment` enforces `kind = QUESTIONS`. Attempts to create `PROJECT` assignments return HTTP 400 `VALIDATION_FAILED`. Frontend `InstructorAssignmentsListPage.jsx` removes project kind selection and simplifies the creation modal. |
| **MCQ Question Builder** | VERIFIED | `InstructorAssignmentService.replaceQuestions` enforces `type = MCQ_SINGLE \|\| type = MCQ_MULTI`. `DESCRIPTIVE` and `CODE` types are rejected with HTTP 400 `VALIDATION_FAILED`. Frontend `AssignmentBuilderPage.jsx` provides only `+ Single Choice (MCQ)` and `+ Multi Choice (MCQ)` question buttons and restricts type dropdowns. |
| **MCQ Publishing Guards** | VERIFIED | `InstructorAssignmentService.publishAssignment` rejects `PROJECT` assignments or any assessment containing non-MCQ questions. At least one question and valid correct-answer configurations are enforced. |
| **Learner Taking & Autosave** | VERIFIED | `TakeAssignmentPage.jsx` renders radio buttons for `MCQ_SINGLE` and checkboxes for `MCQ_MULTI`. Mutex-protected debounced autosave (`PUT /api/v1/learning/assignments/{id}/submissions/draft`) securely persists learner choices without leaking correct answers. In-progress attempts resume seamlessly upon page refresh. |
| **Draft Validation & Concurrency** | VERIFIED | `LearnerSubmissionService.saveDraft` enforces strict option ID validation, single-choice cardinality, answer deduplication, and pessimistic row locking (`findWithLockByAssignmentIdAndUserId`) to prevent concurrent race conditions. |
| **Submission Resilience & Idempotency** | VERIFIED | `LearnerSubmissionService.submit` safely handles version skew from recent autosaves (`version <= submission.version`), executes scoring under write lock, and returns results idempotently on repeat submissions. Frontend flushes pending drafts before submission to prevent lost answers. |
| **Automatic Grading Engine** | VERIFIED | `LearnerSubmissionService.executeScoring` automatically grades single choice (exact match) and multi-choice (exact set match) submissions upon submission, persisting total points and transitioning status to `GRADED`. `SubmissionGradedEvent` is published on transaction commit. |
| **Result Display & Answer Secrecy** | VERIFIED | `ResultView` accurately renders total score, maximum points, selected answers, and conditional correct answer badges when `showCorrectAnswers = true`. Correct answers are never leaked prior to submission or when `showCorrectAnswers = false`. |
| **Historical Data Preservation** | VERIFIED | Database schema (`assignments`, `assignment_questions`, `submissions`, `submission_answers`, `submission_files`) is unchanged. Historical project assignments and submissions remain accessible for review, while new authoring is strictly restricted to MCQs. |
| **Backend Integration Tests** | VERIFIED | 22/22 tests passing across `AssignmentDefinitionIntegrationTest`, `SubmissionAndGradingIntegrationTest` (including `mcqEndToEndStudentFlowAndSecurity`), `SubmissionFilesIntegrationTest`, `AssignmentTimingAndExpiryIntegrationTest`, etc. |
| **Frontend Automated Tests** | VERIFIED | 21/21 files, 155/155 tests passing across entire frontend suite (16/16 in `assignments.test.jsx`). ESLint 0 errors, production build verified. |


---

## Detailed Phase 8 Status: Reviews, Ratings & Community Content Reporting

### Objective
Allow enrolled learners to submit star ratings and qualitative reviews on published courses, update existing reviews idempotently, prevent course author self-reviews, and allow learners to report broken/inappropriate course content with structured categories and instructor moderation.

### Verification Checklist & Evidence

| Component / Requirement | Status | Verification Evidence |
| --- | --- | --- |
| **Reviews Persistence (`V8_001`)** | VERIFIED | `reviews` table with TINYINT rating, FK to courses & users, unique index on `(course_id, user_id)`. |
| **Community Reports Persistence (`V8_002`)** | VERIFIED | `community_reports` table with category enum, status (`OPEN`, `RESOLVED`, `DISMISSED`), FK to courses & users. |
| **Domain Events** | VERIFIED | `ReviewCreatedEvent` and `ReportCreatedEvent` published `AFTER_COMMIT`. |
| **Backend Integration Tests** | VERIFIED | `ReviewIntegrationTest` (4 tests) and `CommunityReportIntegrationTest` (3 tests) passing 100% in MySQL Testcontainers. |
| **Frontend UI & Tests** | VERIFIED | `CourseReviewsSection.jsx`, `ReportButton.jsx`, `ReportModal.jsx` tested with 5/5 passing Vitest tests. |

---

## Detailed Phase 9 Status: Notifications System

### Objective
Provide persistent, event-driven notifications in MySQL for instructors and learners, triggered automatically on transaction commit from domain events (`SubmissionGradedEvent`, `CoursePublishedEvent`, `CourseUnpublishedEvent`, `ReportCreatedEvent`, `ReviewCreatedEvent`), plus an idempotent scheduled job reminding learners of approaching assignment deadlines. Expose paginated notification listing, unread counts, mark-as-read, and mark-all-read APIs secured to the authenticated caller, accompanied by an interactive header bell with unread badge, dropdown popover, and dedicated `/notifications` management page.

### Verification Checklist & Evidence

| Component / Requirement | Status | Verification Evidence |
| --- | --- | --- |
| **Notifications Table (`V9_001__create_notifications.sql`)** | VERIFIED | Schema with `user_id`, `type`, `title`, `message`, `resource_type`, `resource_id`, `action_url`, `is_read`, `created_at`, `read_at`, `event_key`, indexed on `(user_id, is_read, created_at)` and unique on `(user_id, event_key)`. |
| **Domain Event Listeners (`NotificationEventListener`)** | VERIFIED | Listens to `SubmissionGradedEvent`, `CoursePublishedEvent`, `CourseUnpublishedEvent`, `ReportCreatedEvent`, and `ReviewCreatedEvent` with `AFTER_COMMIT` and `fallbackExecution = true`. Deduplicated via `eventKey`. |
| **Approaching Deadline Job (`AssignmentDeadlineNotificationJob`)** | VERIFIED | Queries `AssignmentQueryService.listUpcomingDeadlines` within 24h window, retrieves learners without submissions, and creates idempotent notifications (`DEADLINE:assignmentId:userId`). Tested with repeat executions confirming 0 duplicates. |
| **Authenticated APIs (`/api/v1/notifications`)** | VERIFIED | Listing with pagination and `unreadOnly` filter, unread count endpoint (`/unread-count`), idempotent mark-as-read (`PATCH /{id}/read`), and mark-all-as-read (`POST /mark-all-read`). Security: recipient strictly resolved from `CurrentUserProvider`. Cross-user tampering rejected with 404/401. |
| **Frontend Bell & Dropdown (`NotificationBell`, `NotificationDropdown`)** | VERIFIED | Bell registered into `header.actions` slot (`order: 5`), unread badge, TanStack Query polling (30s interval, pauses when tab inactive), popover with notification items, direct action navigation, and mark-all-read action. |
| **Full Notifications Page (`/notifications`)** | VERIFIED | Filter tabs (All / Unread only), empty states, loading indicators, pagination, and direct action links. |
| **Automated Tests** | VERIFIED | Backend: 6/6 tests passing in `NotificationIntegrationTest`. Frontend: 5/5 tests passing in `notifications.test.jsx`. Overall frontend suite: 17/17 files, 109/109 tests passing. ESLint 0 errors, Prettier 100% formatted. |

---

## Detailed Phase 10 Status: Instructor Analytics Dashboard

### Objective
Provide instructors with an authoritative, read-only analytics dashboard for their owned courses without creating new tables or background data warehouses. Compute 8 essential metrics (total enrollments, active learners, completion rate, average assessment score, average rating, lesson drop-off funnel, assignment submission rate, pending reviews) via optimized SQL aggregate queries over existing schema, cached in Redis with short TTL and event-driven invalidation.

### Verification Checklist & Evidence

| Component / Requirement | Status | Verification Evidence |
| --- | --- | --- |
| **Read-Only Aggregations** | VERIFIED | `AnalyticsRepository` executes aggregate queries over `enrollments`, `course_progress`, `lesson_progress`, `assignments`, `submissions`, `reviews`, `courses`, `course_modules`, and `lessons`. Zero new tables created. |
| **Authoritative 8 Metrics** | VERIFIED | Accurate calculations handling empty/null states, division by zero guards, and distinct user counts. |
| **Course Ownership & Security** | VERIFIED | `OwnershipGuard.requireOwnerOrNotFound` strictly enforces course ownership. Tampering by other instructors yields 404 Not Found to prevent course enumeration. Anonymous calls yield 401 Unauthorized. |
| **Redis Caching & Resilience** | VERIFIED | `AnalyticsCacheService` caches with key `analytics:course:{courseId}` and 300s TTL. `SafeCache` provides graceful degradation when Redis connection fails. |
| **Event-Driven Invalidation** | VERIFIED | `AnalyticsCacheInvalidationListener` listens to 8 domain events on `AFTER_COMMIT` to evict cached analytics immediately on data modifications. |
| **Frontend Dashboard UI** | VERIFIED | `InstructorAnalyticsPage.jsx` implements 7 KPI cards, 3 visual SVG/HTML charts (Lesson Drop-Off Funnel, Score Distribution, Assignment Breakdown), loading skeletons, error state with retry, and empty course state. |
| **Slot Contribution** | VERIFIED | `analytics-course-editor-header` registered into `course-editor.header-actions` slot. Route `/instructor/courses/:courseId/analytics` registered in `routes.js`. |
| **Automated Verification** | VERIFIED | Backend: 6/6 tests passing in `InstructorAnalyticsIntegrationTest`. Frontend: 7/7 tests passing in `analytics.test.jsx`. Full frontend suite: 18/18 files, 116/116 tests passing. ESLint 0 errors, Prettier 100% formatted, Vite production build succeeded. |

---

## Detailed Phase 11 Status: Learner Extras (Bookmarks & Notes)

### Objective
Provide learners with optional personal learning enhancements: course and lesson bookmarks, and private lesson notes. Enforce strict privacy so other learners or instructors can never view or modify another learner's notes. Maintain complete phase isolation so that removing bookmarks or notes never breaks course discovery, enrollment, playback, assignments, reviews, notifications, or analytics.

### Verification Checklist & Evidence

| Component / Requirement | Status | Verification Evidence |
| --- | --- | --- |
| **Bookmarks Persistence (`V11_001`)** | VERIFIED | `bookmarks` table with `user_id`, `target_type` ('COURSE', 'LESSON'), `course_id`, `lesson_id`, stored generated `target_key`, and unique constraint `uq_bookmarks_user_target`. |
| **Lesson Notes Persistence (`V11_002`)** | VERIFIED | `lesson_notes` table with `user_id`, `course_id`, `lesson_id`, `content` (MEDIUMTEXT), timestamps, unique constraint `uq_lesson_notes_user_lesson`. |
| **Strict Privacy & Security** | VERIFIED | `CurrentUserProvider.requireCurrentUser()` resolves user strictly from JWT. Active enrollment required for notes (`ENROLLMENT_REQUIRED` 403). Notes always queried by `user_id` preventing cross-user access. |
| **Backend Integration Tests** | VERIFIED | `BookmarkIntegrationTest` (4 tests) and `LessonNoteIntegrationTest` (4 tests) passing 100% in MySQL Testcontainers. |
| **Frontend UI & Slots** | VERIFIED | `BookmarkToggle.jsx` (`course-page.enroll-area`, `player.header-actions`, `player.lesson-actions`), `BookmarksPage.jsx` (`/learning/bookmarks`), `LessonNotesPanel.jsx` (`player.side-panel`). |
| **Automated Verification** | VERIFIED | Backend: 8/8 tests passing. Frontend: 10/10 tests passing across `bookmarks.test.jsx` (6) and `notes.test.jsx` (4). Overall frontend suite: 20/20 files, 126/126 tests passing. ESLint 0 errors, Prettier 100% formatted, Vite production build succeeded. |

---

---

## Detailed Phase 12 Status: Production Hardening, End-to-End Testing & Release

### Objective
Verify that PeerGeek is secure, reliable, performant, deployable, observable, recoverable, and ready for release across all 13 phases (Phases 0–12). The complete core flow defined in Section 16 of the Feature Specification must pass end-to-end on a production-like stack, and no critical or high-severity findings remain open.

### Verification Checklist & Evidence

| Component / Requirement | Status | Verification Evidence |
| --- | --- | --- |
| **Feature Spec Section 16 Core Flow** | VERIFIED | `CoreUserFlowIntegrationTest.java`: 13 sequential steps covering registration, draft authoring, readiness run & publishing, public discovery & preview, enrollment, playback & lesson completion, bookmarks & private notes, assignment attempt & submission, instructor grading & feedback, in-app notifications, review & community report submission, authoritative instructor analytics, and second learner enrollment. **13/13 tests PASS**. |
| **Endpoint Authorization Matrix** | VERIFIED | `EndpointAuthorizationMatrixTest.java`: Verified anonymous 401 across 20+ registered endpoints, public route access (`/api/v1/categories`, `/api/v1/public/courses`, `/actuator/health/liveness`), instructor course isolation (cross-instructor 404), learner notes/notifications isolation (cross-learner 403/404). **4/4 tests PASS**. |
| **Sliding Window Rate Limiting** | VERIFIED | `RateLimitingFilter.java`: Limits auth endpoints (`/api/v1/auth/*`) and community reports (`/api/v1/courses/*/reports`) to 10 req/min with Redis sliding window and in-memory degradation fallback. Emits `Retry-After` header and uniform `ApiErrorResponse` (`429 TOO_MANY_REQUESTS`, code `RATE_LIMIT_EXCEEDED`). `RateLimitingIntegrationTest.java`: **3/3 tests PASS**. |
| **Production Security Headers** | VERIFIED | `SecurityConfig.java`: Configured strict Content Security Policy (CSP), `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: strict-origin-when-cross-origin`, and `Strict-Transport-Security` (HSTS). |
| **Database Performance Indexes** | VERIFIED | Additive migration `V12_001__performance_indexes.sql`: `idx_course_progress_course_completed` on `course_progress(course_id, completed_at)` and `idx_courses_category_status` on `courses(category_id, status)`. All 36 Flyway migrations applied, 32 application tables intact. `FoundationIntegrationTest.java`: **10/10 tests PASS**. |
| **Load Testing & Cache Benchmark** | VERIFIED | `scripts/load-test.mjs`: Benchmarked course catalog (378.9 req/s, p50: 15.8 ms), popular courses (336.8 req/s, p50: 17.2 ms), and course preview (240.1 req/s, p50: 11.5 ms) with **0.00% error rate** across 600 total requests. |
| **Disaster Recovery Backup Drill** | VERIFIED | `scripts/backup-restore-drill.ps1`: Automated, non-destructive drill in disposable MySQL 8.4 container. Created `mysqldump` artifact (11.5 KB), restored into `peergeek_drill_restore`, verified schema (4 tables) and records (users, categories, courses). Logged in `docs/backup_restore_drill.log`. **DRILL RESULT: SUCCESS**. |
| **Production Docker Images** | VERIFIED | `backend/Dockerfile` and `frontend/Dockerfile` updated with container healthchecks. Successfully built `peergeek-frontend:release` and `peergeek-backend:release` (multi-stage, non-root execution, minimal runtime). |
| **CI/CD Release Workflow** | VERIFIED | `.github/workflows/release.yml`: Automated pipeline for frontend lint/test/format/build, backend test suite, security matrix, dependency audit, and production Docker container builds. |
| **Frontend Verification** | VERIFIED | Vitest: 20/20 test files, 126/126 tests passing. ESLint: 0 errors. Prettier: 100% formatted. Vite production build: succeeded in 985ms. `npm audit`: 0 vulnerabilities. |
| **Operations Runbook** | VERIFIED | `docs/RUNBOOK.md`: Comprehensive documentation covering architecture, health probes, environment variables, migration recovery, backup/restore procedures, S3 troubleshooting, logging MDC, and zero-downtime rollback. |

---

## Detailed Phase 13 Status: Email OTP Verification & Secure Password Reset

### Objective
Implement end-to-end email OTP verification during registration (preventing login until 6-digit OTP verification) and secure 2-step password reset (email OTP exchange for single-use SHA-256 `resetToken`, session revocation, confirmation email delivery), preserving existing user access and maintaining strict anti-enumeration and brute-force defenses.

### Verification Checklist & Evidence

| Component / Requirement | Status | Verification Evidence |
| --- | --- | --- |
| **Database Migration (`V13_001`)** | VERIFIED | `V13_001__email_verification_and_otps.sql`: Added `email_verified` boolean to `users` (default `TRUE` for existing accounts) and created `auth_otps` table with indexes on `(email, purpose, created_at)` and `(reset_token_hash)`. |
| **New User Registration Flow** | VERIFIED | Unverified users receive `RegisterResponse(verificationRequired=true)`. Login before verification returns `403 FORBIDDEN` with code `EMAIL_NOT_VERIFIED`. |
| **Email Verification Endpoint** | VERIFIED | `POST /api/v1/auth/verify-email`: Validates 6-digit OTP against BCrypt hash, marks `user.email_verified = TRUE`, and consumes the OTP. |
| **Resend Verification & Cooldown** | VERIFIED | `POST /api/v1/auth/resend-verification-otp`: Generates new OTP and enforces 60-second cooldown timer. Constant-time dummy hashes protect unverified/unknown emails. |
| **2-Step Tokenized Password Reset** | VERIFIED | `POST /api/v1/auth/verify-reset-otp`: Validates 6-digit OTP and returns single-use SHA-256 `resetToken`. `POST /api/v1/auth/reset-password`: Validates token, updates password, revokes refresh token family, and sends confirmation email. Supports backward-compatible legacy OTP. |
| **Brute-Force & Attempt Protection** | VERIFIED | Max 5 failed attempts before OTP is burned. Expired/burned OTPs return consistent `VALIDATION_FAILED` errors. |
| **Zero Secret Leakage** | VERIFIED | OTPs and reset tokens are never written to server logs, URLs, `localStorage`, or `sessionStorage`. Held purely in memory. |
| **Backend Automated Tests** | VERIFIED | `AuthIntegrationTest`: 15/15 tests passing (100%). `CoreUserFlowIntegrationTest`: 13/13 tests passing (100%). Total: 28/28 tests passing. |
| **Frontend UI & Automated Tests** | VERIFIED | Dedicated `VerifyEmailPage` (`/verify-email`), cooldown timer, expiry countdown, unverified login banner with deep-link, and 2-step reset password page. Vitest: 12/12 tests passing. ESLint: 0 errors. Vite production build: succeeded. |
 
---
 
## Detailed Phase 14 Status: Basic Admin Monitoring Dashboard & Moderation

### Objective
Implement a functional, secure Admin Monitoring Dashboard accessible strictly to users with the `ADMIN` role, providing real-time visibility across registered users, course issues, courses, and platform activity logs without mock or fabricated data. Enhance the dashboard with interactive summary cards, detailed student and instructor directories, user blocking/unblocking with token revocation, course lifecycle moderation, and direct inspection of reported courses.

### Verification Checklist & Evidence

| Component / Requirement | Status | Verification Evidence |
| --- | --- | --- |
| **RBAC & Endpoint Security** | VERIFIED | All endpoints under `/api/v1/admin/**` guarded with `@PreAuthorize("hasRole('ADMIN')")`. Non-admin users receive `403 FORBIDDEN`; unauthenticated requests receive `401 UNAUTHORIZED`. |
| **Interactive KPI Summary Cards** | VERIFIED | All 6 cards (`Total Users`, `Students`, `Teachers`, `Total Courses`, `Published Courses`, `Open Issues`) are accessible buttons with hover elevation, focus rings, and direct tab navigation with pre-filtered query states. |
| **Student & Teacher Directory** | VERIFIED | Searchable user directory with role (`USER`/`ADMIN`), mode (`STUDENT`/`INSTRUCTOR` via `lastMode`), and account status (`Active`/`Blocked`). Details modal displays full metadata and registration dates. |
| **User Block/Unblock & Token Revocation** | VERIFIED | `PATCH /api/v1/admin/users/{userId}/status`: Updates `users.enabled`, revokes all active refresh tokens in `auth_refresh_tokens`, emits audit log (`USER_BLOCKED`/`USER_UNBLOCKED`). Enforces self-block and admin-block rejection with HTTP 400. |
| **Course Lifecycle Moderation** | VERIFIED | `PATCH /api/v1/admin/courses/{courseId}/status`: Allows transitioning between `PUBLISHED`, `UNPUBLISHED`, and `DRAFT`. Preserves all student enrollments, lessons, and progress. Publishes `CourseUnpublishedEvent`/`CoursePublishedEvent` to synchronize `public_course_index`. |
| **Report-Linked Course Moderation** | VERIFIED | Report Review modal features "🛡️ Inspect & Moderate Course" button, opening the moderation dialog pre-populated with report context without auto-resolving reports. |
| **Course Issues Search & Filter** | VERIFIED | `GET /api/v1/admin/reports` supporting `status=OPEN` (`PENDING` + `IN_REVIEW`), category badges, reporter details, and status updates (`IN_REVIEW`, `RESOLVED`, `DISMISSED`) with audit notes. |
| **Event Logging Integration** | VERIFIED | `AuditService.record(...)` captures `USER_BLOCKED`, `USER_UNBLOCKED`, `COURSE_UNPUBLISHED`, `COURSE_PUBLISHED`, `REPORT_CREATED`, `REPORT_STATUS_UPDATED`, and `REVIEW_CREATED`. |
| **Backend Automated Tests** | VERIFIED | `AdminDashboardIntegrationTest`: 16/16 tests passing (100%), including tests for user block/unblock, session lockout, self-block guard, course unpublishing & restoration, public catalog hiding, and report preservation. |
| **Frontend Automated Tests** | VERIFIED | `src/features/admin/admin.test.jsx`: 14/14 tests passing (100%), verifying card navigation, user details/block modal, course moderation, and report-to-course flow. |
| **Build & Lint Verification** | VERIFIED | `npm run lint`: 0 errors. `npm run build`: Production bundle succeeded. |

---

## Detailed Status: Course Readiness Engine Bug Fix & Publishing Gate Hardening

### Objective
Ensure instructors can reliably trigger **Check Course Readiness** on draft and published courses from the Course Details tab, determining publication viability. Address root causes end-to-end: eliminate uncaught exceptions, inject all Spring-registered readiness rules, perform comprehensive course structure and content validations (empty modules, blank titles, empty lessons, draft assignments), present actionable navigable issues with deep links, handle loading and error retry states, and enforce strict publishing prevention when blocking issues exist.

### Verification Checklist & Evidence

| Component / Requirement | Status | Verification Evidence |
| --- | --- | --- |
| **Resource Enum Alignment & Migration (`V14_001`)** | VERIFIED | Added `LESSON` alias into `ResourcePurpose.java`. Applied Flyway migration `V14_001__normalize_resource_purpose.sql` to normalize legacy rows in `resources` table to `'LESSON_RESOURCE'`. Eliminates `IllegalArgumentException` during JPA resource loading. |
| **Dynamic Readiness Rules Injection** | VERIFIED | `ReadinessService.java` refactored to inject `List<ReadinessRule> rules` dynamically via Spring, ensuring all rules (including `AssignmentDraftExistsReadinessRule`, `BasicReadinessRules`, `DeepReadinessRules`) are automatically executed. |
| **Course Structure & Title Validation** | VERIFIED | `BasicReadinessRules.java` verifies course modules presence (`COURSE_MODULES_REQUIRED`), module title presence (`MODULE_TITLE_REQUIRED`), module non-empty lessons (`MODULE_LESSON_REQUIRED`), lesson title presence (`LESSON_TITLE_REQUIRED`), and lesson content presence (`LESSON_CONTENT_REQUIRED`). Issues include human-readable messages referencing the exact module or lesson name, and clear fix hints. |
| **Latest Run Endpoint & Serialization** | VERIFIED | `GET /api/v1/instructor/courses/{courseId}/readiness/runs/latest` exposed via `ReadinessController` and `ReadinessService.getLatest(courseId)`. Added `@JsonProperty("code")` on `ReadinessIssue.code()` and `@JsonProperty("ready")` on `ReadinessRunView.isReady()`. |
| **Actionable Frontend Readiness Panel** | VERIFIED | `PublishReadinessPanel.jsx` updated with accessible `data-testid="check-course-readiness-btn"`, loading spinner, success banner for publication-ready courses, blocking issues alerts, and actionable cards with severity badges, scope badges, and direct navigation links (`Go to Outline ->` and `Edit Details ->`). Retry state provided on query/mutation errors. |
| **Publishing Gate Enforcement** | VERIFIED | Publishing button remains strictly disabled while blocking errors exist (`!readinessRun.ready`). Allows publishing only when readiness passes without blocking issues. |
| **Backend Integration Tests** | VERIFIED | 14/14 tests passing across `ReadinessRegressionIntegrationTest` (7 tests: ready course, missing modules, empty modules, empty lessons, multiple issues, unauthorized access, publishing enforcement), `ReadinessPublishingIntegrationTest` (4 tests), and `BasicReadinessRulesTest` (3 tests). |
| **Frontend Automated Tests** | VERIFIED | `PublishReadinessPanel.test.jsx`: 4/4 tests passing (100%). Entire frontend test suite: 21/21 test files, 157/157 tests passing. ESLint: 0 errors. Vite build: succeeded. |






