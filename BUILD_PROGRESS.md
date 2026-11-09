# PeerGeek — Build & Implementation Progress

## Phase 8: Course Discovery, Search, Filtering, Sorting & Enrollment Foundation

### 1. Mandatory Pre-Implementation Audit Summary
- **Backend Architecture & Readiness Verification**:
  - `com.peergeek.discovery` already maintains the denormalized read model `public_course_index` populated via transaction-commit events (`CoursePublishedEvent`, `CourseUnpublishedEvent`, etc.) and reconciled periodically via `DiscoveryReconciliationService`.
  - Free-text substring search, category slug filtering, multi-difficulty filtering, sort options (`popularity,desc`, `publishedAt,desc`, `rating,desc`, `title,asc`, `relevance`), and pagination (capped at offset 1000) are fully implemented in `JdbcCourseSearchRepository`.
  - Public course preview endpoints (`GET /api/v1/public/courses/{courseId}`) enforce strict 404 indistinguishability for unpublished, draft, and deleted courses and compute weak ETags for HTTP 304 caching.
  - Public thumbnail endpoint (`GET /api/v1/public/courses/{courseId}/thumbnail`) enforces course publication, ready status, and returns `302 Found` to an inline presigned S3 URL.
  - Enrollment foundation (`com.peergeek.enrollment`) provides free instant enrollment (`POST /api/v1/learning/courses/{courseId}/enroll`), status check (`GET /api/v1/learning/courses/{courseId}/enrollment`), soft withdrawal (`DELETE /api/v1/learning/courses/{courseId}/enrollment`), and owner self-enrollment rejection (`409 CANNOT_ENROLL_OWN_COURSE`).

- **Frontend Discovery & Course Detail Audit**:
  - `DiscoverPage.jsx` implements URL-synchronized search with 300ms debounce, category dropdown, difficulty checkboxes, sorting dropdown, and pagination.
  - `CoursePreviewPage.jsx` implements the course detail preview with title, description, instructor bio, outline accordion, and dynamic slot `course-page.enroll-area`.
  - `EnrollArea.jsx` renders appropriate contextual UI based on authentication and enrollment state:
    - Anonymous: "Log in to enroll" linking to `/login?returnTo=...`
    - Enrolled: Progress bar, "Continue learning" button, withdrawal dialog
    - Not enrolled: "Enroll in Course" button with mutation
    - Owner: Course instructor banner with link to instructor dashboard

### 2. Gaps & Vulnerabilities Resolved During Phase 8
1. **Frontend Runtime Error on HTML Interception**:
   - `client.js` `readResponse` was returning raw HTML text strings on non-JSON 200 responses. Added explicit check rejecting `text/html` responses with `ApiError` (`UNEXPECTED_HTML_RESPONSE`).
   - `vite.config.js` lacked development proxy for `/api/v1`. Added `server.proxy` targeting `http://localhost:8080` with `changeOrigin: true`.
2. **Defensive Data Handling**:
   - `PopularCoursesSection.jsx`: Added `!Array.isArray(courses) || courses.length === 0` check before `.slice()` and `.map()`.
   - `DiscoverPage.jsx`: Added `Array.isArray(coursesQuery.data?.content)` fallback to ensure empty state renders gracefully on malformed payloads.
   - `CoursePreviewPage.jsx`: Added `Array.isArray()` defensive checks for `course.topics`, `course.outline`, and `mod.lessons`.
3. **Redis Health Verification**:
   - Started `peergeek20-redis-1` container, verifying `/actuator/health` components (`db`, `redis`, `objectStorage`, `ping`, `readinessState`, `livenessState`) all report `UP`.

### 3. Files Modified
- `frontend/vite.config.js`: Added proxy configuration for `/api/v1` to `http://localhost:8080`.
- `frontend/src/shared/api/client.js`: Throws `ApiError` when unexpected `text/html` is returned by API endpoints.
- `frontend/src/features/discovery/PopularCoursesSection.jsx`: Defensive array verification.
- `frontend/src/features/discovery/DiscoverPage.jsx`: Defensive array check for search results `content`.
- `frontend/src/features/discovery/CoursePreviewPage.jsx`: Defensive array checks for `topics`, `outline`, and `lessons`.
- `frontend/src/shared/api/client.test.js`: Added test for HTML response rejection.
- `frontend/src/features/discovery/discovery.test.jsx`: Added tests for non-array responses across `PopularCoursesSection`, `DiscoverPage`, and `CoursePreviewPage`.
- `docs/phaseStatus.md`: Documented status and Phase 9 boundary.
- `BUILD_PROGRESS.md`: Recorded build, audit, and verification logs.

### 4. Verification & Automated Test Results
- **Frontend Tests (`npm test`)**:
  - **14 passed test suites, 96 passed tests (0 failures).**
  - Includes tests for `discovery.test.jsx` (20 tests), `learn.test.jsx` (18 tests), `assignments.test.jsx` (14 tests), `auth.test.jsx` (8 tests), `components.test.jsx` (7 tests), `courseAuthoring.test.jsx` (5 tests), `client.test.js` (4 tests), `LessonDocument.test.jsx` (4 tests).
- **Frontend Code Quality**:
  - `npm run lint`: 0 errors.
  - `npm run format:check`: All files use Prettier code style.
  - `npm run build`: Production build succeeded in 692ms.
- **Backend Tests (`mvnw test`)**:
  - `DiscoveryRedisDownIntegrationTest` & `DiscoveryThumbnailIntegrationTest`: 5 tests passed, 0 failures, 0 skipped.
  - `SafeCacheTest`: 1 test passed, 0 failures.
- **API Runtime Verification**:
  - `curl http://localhost:5173/api/v1/public/courses/popular`: HTTP 200 OK (`[]`).
  - `curl -X POST http://localhost:5173/api/v1/auth/refresh`: HTTP 401 Unauthorized (proper Spring Boot response routed via proxy).
  - `curl http://localhost:8080/actuator/health`: HTTP 200 OK, all components UP.

### 5. Boundary from Phase 8 to Phase 9
Phase 8 delivered **Course Discovery, Search, Filtering, Sorting & Enrollment Foundation**, establishing the discovery read models, UI components, and API proxy foundations.

---

## Phase 9: Student Enrollment Lifecycle and Protected Course Access

### 1. Mandatory Pre-Implementation Audit Summary
- **Backend Architecture & Seam Verification**:
  - `com.peergeek.enrollment` contains the complete enterprise enrollment and learner domain model:
    - Tables: `enrollments` (`V6_001`), `lesson_progress` (`V6_002`), `course_progress` (`V6_003`).
    - Composite uniqueness constraint: `uq_enrollments_user_course (user_id, course_id)`.
    - Lifecycle: Free instant enrollment (`POST /api/v1/learning/courses/{courseId}/enroll`) via atomic SQL upsert (`ON DUPLICATE KEY UPDATE`) to handle concurrent requests safely without Hibernate session corruption.
    - Soft withdrawal (`DELETE /api/v1/learning/courses/{courseId}/enrollment`): Transitions status to `WITHDRAWN`, timestamps `withdrawn_at`. Re-enrolling reactivates the enrollment and preserves all completed lesson records and progress math.
    - Course owner self-enrollment block: Rejects owners enrolling in their own course with HTTP 409 `CANNOT_ENROLL_OWN_COURSE`.
    - Authorization: Enforces security context via `CurrentUserProvider.requireCurrentUser()`. Caller cannot pass student ID to impersonate another user.
    - Protected course access: Non-enrolled users attempting to access course outlines or lessons of published courses receive HTTP 403 `ENROLLMENT_REQUIRED`. Non-enrolled requests to draft, unpublished, deleted, or nonexistent courses receive HTTP 404 `NOT_FOUND` to guarantee 404 indistinguishability.
    - Protected resource access: `EnrolledLearnerVoter` (`ResourceAccessVoter` with request-scoped memoization) grants `VIEW` and `DOWNLOAD` permissions to active learners for `LESSON_RESOURCE` ready attachments. Presigned S3 URLs are short-lived; no S3 credentials or object keys are leaked.
    - Continue Learning: Deterministic 4-step resolution (`RESUME` -> `NEXT` -> `FIRST` -> `COMPLETED`) via `ProgressService.getContinueLesson`.

- **Frontend Learning Experience Audit**:
  - `EnrollArea.jsx` renders dynamic state based on authentication and enrollment:
    - Anonymous: "Log in to enroll" linking to `/login?returnTo=...`
    - Loading: Spinner while checking enrollment status.
    - Enrolled: Enrolled badge, progress bar, "Continue learning" button, "Withdraw from course" confirmation dialog.
    - Not enrolled: "Enroll in Course" button with mutation and error feedback.
    - Owner: Course instructor banner with link to instructor management dashboard.
  - `MyLearningPage.jsx` (`/learning`): Displays enrolled course cards, filter tabs (All, In Progress, Completed), search query, pagination, and unpublish indicator badges.
  - `CoursePlayerPage.jsx` (`/learning/courses/:courseId/lessons/:lessonId`): Renders course outline sidebar, TipTap ProseMirror lesson content, attached lesson resources (PDF iframe, video player, image viewer, document download, external links), mark complete toggle button with aria-live announcements, and slot extensions (`player.lesson-actions`, `player.side-panel`).
  - `CourseRedirectPage.jsx` (`/learning/courses/:courseId`): Authoritatively redirects to the student's continue lesson or course outline.

### 2. Gaps & Hardening Applied During Phase 9
1. **Defensive Non-Array Payload Safeguards**:
   - `MyLearningPage.jsx`: Added `Array.isArray(data?.content) ? data.content : []` to prevent `courses.map is not a function` runtime crashes if backend returns non-array or null content.
   - `CourseRedirectPage.jsx`: Added `Array.isArray(outlineQuery.data.modules)` and `Array.isArray(m?.lessons)` checks before `flatMap` and `find`.
   - `CoursePlayerPage.jsx`: Added `Array.isArray(outline?.modules)` and `Array.isArray(module.lessons)` checks around outline module/lesson iteration.
2. **Automated Defensive Test Coverage**:
   - Added 3 test cases in `frontend/src/features/learn/learn.test.jsx`:
     - `MyLearningPage handles null or non-array content safely`
     - `CoursePlayerPage handles null or non-array modules without crashing`
     - `CourseRedirectPage handles malformed/non-array modules in outline without crashing`

### 3. Files Modified
- `frontend/src/features/learn/MyLearningPage.jsx`: Defensive array check for enrolled course cards.
- `frontend/src/features/learn/CourseRedirectPage.jsx`: Defensive array checks for outline modules and lessons.
- `frontend/src/features/learn/CoursePlayerPage.jsx`: Defensive array checks for modules and lessons list.
- `frontend/src/features/learn/learn.test.jsx`: Added 3 tests verifying graceful handling of malformed payloads (bringing suite to 21 tests).
- `docs/phaseStatus.md`: Updated Phase 9 status to VERIFIED and added detailed verification checklist and Phase 10 boundary.
- `BUILD_PROGRESS.md`: Recorded Phase 9 audit, hardening, and verification logs.

### 4. Verification & Automated Test Results
- **Backend Integration Tests (`mvnw test`)**:
  - `EnrollmentIntegrationTest` (6 tests): verified successful enrollment, 404 indistinguishability for draft/unpublished/deleted courses, 409 `CANNOT_ENROLL_OWN_COURSE`, idempotent repeat enrollments, withdrawal and progress-preserving reactivation, concurrent double-enrollment race condition safety.
  - `LearnerAccessMatrixIntegrationTest` (4 tests): verified 401 Unauthorized for anonymous calls, 403 `ENROLLMENT_REQUIRED` for non-enrolled users on published courses, 404 for unpublished courses, 200 for enrolled learners across all 6 learner endpoints.
  - `LearnerResourceAccessIntegrationTest` (4 tests): verified short-lived presigned URL generation, document attachment disposition, video/image inline disposition, 404 for non-enrolled or deleted resources.
  - `EnrolledLearnerVoterTest` (6 tests): verified voter grants VIEW/DOWNLOAD, abstains on instructor routes and non-ready resources, and performs request-scoped memoization.
  - `ContinueLearningIntegrationTest` (5 tests): verified RESUME, NEXT, FIRST, and COMPLETED resolution order.
  - `ProgressEngineIntegrationTest` (12 tests): verified floor math, deleted lesson pruning, and lazy recomputation on stale flags.
  - `StatsAndEventsIntegrationTest` (2 tests): verified after-commit event publishing and stats recording.
  - **Total: 39 tests passed, 0 failures, 0 skipped.**
- **Frontend Test Suite (`npm test`)**:
  - **14 passed test files, 99 passed tests (0 failures).**
  - Includes 21 tests in `learn.test.jsx` covering anonymous login redirect, enrollment mutation, progress bar, continue learning, withdrawal confirmation dialog, MyLearningPage tabs/search/cards, CoursePlayerPage outline/lesson/resources/completion, error states (403/404), and defensive payload handling.
- **Frontend Code Standards**:
  - `npm run lint`: 0 errors.
  - `npm run format:check`: All files use Prettier code style.
  - `npm run build`: Production bundle compiled cleanly in 687ms.
- **API Runtime Verification**:
  - `curl http://localhost:5173/api/v1/learning/courses`: HTTP 401 Unauthorized (`code: "UNAUTHORIZED"`).
  - `curl -X POST http://localhost:5173/api/v1/learning/courses/1/enroll`: HTTP 401 Unauthorized.
  - `curl http://localhost:5173/api/v1/learning/courses/1`: HTTP 401 Unauthorized.
  - `curl http://localhost:5173/api/v1/learning/resources/1/url`: HTTP 401 Unauthorized.
  - `curl http://localhost:8080/actuator/health`: HTTP 200 OK, all components UP.

---

## Phase 8: Reviews, Ratings & Community Content Reporting

### 1. Implementation Summary
- **Database Migrations**:
  - `V8_001__create_course_reviews.sql`: Created `reviews` table with TINYINT rating, FK to courses & users, unique index on `(course_id, user_id)`.
  - `V8_002__create_community_reports.sql`: Created `community_reports` table with category enum, status (`OPEN`, `RESOLVED`, `DISMISSED`), FK to courses & users.
- **Domain Events**:
  - `ReviewCreatedEvent`: Emitted after-commit upon review creation.
  - `ReportCreatedEvent`: Emitted after-commit upon report creation.
- **Backend Architecture**:
  - `com.peergeek.review`: `ReviewEntity`, `ReviewDto`, `ReviewService`, `ReviewController`, `PublicReviewController`. Blocks course authors from reviewing their own course (`CannotReviewOwnCourseException`), enforces enrolled status (`ReviewNotEnrolledException`), updates course aggregate ratings via `CourseStatsRecorder`.
  - `com.peergeek.report`: `ReportEntity`, `ReportDto`, `ReportService`, `ReportController`, `InstructorReportController`. Provides submission for learners and moderation status updates for course owners/admins.
- **Frontend Architecture**:
  - `frontend/src/features/reviews/`: `reviewsApi.js`, `CourseReviewsSection.jsx` integrated into `CoursePreviewPage`.
  - `frontend/src/features/reports/`: `reportsApi.js`, `ReportButton.jsx`, `ReportModal.jsx` integrated into `player.lesson-actions` slot.
- **Verification Evidence**:
  - Backend: `ReviewIntegrationTest` (4 tests) and `CommunityReportIntegrationTest` (3 tests) passing 100% in MySQL Testcontainers.
  - Frontend: `reviews.test.jsx` (3 tests) and `reports.test.jsx` (2 tests) passing in Vitest.

---

## Phase 9: Notifications System

### 1. Implementation Summary
- **Database Migration**:
  - `V9_001__create_notifications.sql`: Created `notifications` table with `user_id`, `type`, `title`, `message`, `resource_type`, `resource_id`, `action_url`, `is_read`, `created_at`, `read_at`, `event_key`.
  - Indexing: `idx_notifications_user_created (user_id, created_at)` and `idx_notifications_user_read (user_id, is_read, created_at)`.
  - Deduplication: Unique constraint `uq_notifications_user_event (user_id, event_key)`.
- **Domain Event Listeners**:
  - `NotificationEventListener`: Annotated with `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)`.
  - Handled events:
    1. `SubmissionGradedEvent` -> Notifies student with grade score and direct assignment link. Deduplicated via `SUBMISSION_GRADED:{submissionId}`.
    2. `CoursePublishedEvent` -> Notifies instructor that course is live. Deduplicated via `COURSE_PUBLISHED:{courseId}:{publishedAt}`.
    3. `CourseUnpublishedEvent` -> Notifies instructor that course was unpublished. Deduplicated via `COURSE_UNPUBLISHED:{courseId}:{unpublishedAt}`.
    4. `ReportCreatedEvent` -> Notifies course owner of community report. Deduplicated via `REPORT_CREATED:{reportId}`.
    5. `ReviewCreatedEvent` -> Notifies instructor of learner review and star rating. Deduplicated via `REVIEW_CREATED:{reviewId}`.
- **Approaching Deadline Scheduled Job**:
  - `AssignmentDeadlineNotificationJob`: Queries `AssignmentQueryService.listUpcomingDeadlines` within a 24-hour window, retrieves enrolled learners without submissions via `listLearnerIdsWithoutSubmission`, and dispatches idempotent notifications with `eventKey = "DEADLINE:{assignmentId}:{learnerId}"`. Subsequent executions produce 0 duplicate notifications.
- **REST Endpoints (`/api/v1/notifications`)**:
  - `GET /api/v1/notifications`: Paginated listing with `unreadOnly` filter.
  - `GET /api/v1/notifications/unread-count`: Returns unread count for badge.
  - `PATCH /api/v1/notifications/{id}/read`: Idempotent read status update.
  - `POST /api/v1/notifications/mark-all-read`: Marks all unread notifications for caller as read.
  - Security: User resolved strictly from `CurrentUserProvider.requireCurrentUser()`. Insecure direct object reference (IDOR) attacks blocked with 404/401.
- **Frontend Architecture**:
  - `frontend/src/features/notifications/`:
    - `notificationsApi.js`: API client methods.
    - `NotificationBell.jsx`: Header bell registered in `header.actions` slot (`order: 5`), unread badge, TanStack Query polling (30s interval, pauses when tab inactive).
    - `NotificationDropdown.jsx`: Popover with recent notifications, type icons, direct action links, and mark all read button.
    - `NotificationsPage.jsx`: Full notifications management page at `/notifications` with filter tabs (All / Unread only), empty states, pagination, and direct action buttons.
    - `slots.js` and `routes.js`: Registered via plugin architecture.
- **Verification & Test Results**:
  - Backend: `NotificationIntegrationTest` (6 tests passing in MySQL Testcontainers).
  - Frontend: `notifications.test.jsx` (5 tests passing in Vitest).
  - Overall Frontend Suite: 17 test files, 109 tests passing (0 failures).
  - ESLint: 0 errors; Prettier: 100% formatted; Production build: compiled in 11.2s.

---

---

## Phase 10: Instructor Analytics Dashboard

### 1. Implementation Summary
- **No Database Migrations / Schema Changes**:
  - As mandated by the architecture blueprint, Phase 10 strictly reuses existing tables (`enrollments`, `course_progress`, `lesson_progress`, `assignments`, `submissions`, `reviews`, `courses`, `course_modules`, `lessons`). Zero new tables created.
- **Backend Architecture (`com.peergeek.analytics`)**:
  - `InstructorAnalyticsController`: Read-only endpoint `GET /api/v1/instructor/courses/{courseId}/analytics`.
  - `AnalyticsService` & `AnalyticsServiceImpl`: Verifies ownership via `OwnershipGuard.requireOwnerOrNotFound`, computes 8 core metrics, and interfaces with `AnalyticsCacheService`.
  - `AnalyticsRepository`: Performs aggregate queries using `JdbcTemplate` on MySQL 8 indexes:
    1. Total enrollments: `countTotalEnrollments`
    2. Active learners: `countActiveLearners` (`status = 'ACTIVE'`)
    3. Completion rate: `countCompletedLearners` over `activeLearners`
    4. Average assessment score: `getAverageAssessmentScore` (`total_points / max_points * 100`) across `GRADED` submissions
    5. Average rating: `getRatingSummary` (`AVG(rating)` and `COUNT(rating)` on non-deleted reviews)
    6. Lesson drop-off: `getLessonCompletionCounts` merged with `CourseStructureQueryService` to compute sequential drop-off rates
    7. Assignment submission rate: `getAssignmentStats` calculating submitted count vs expected count for published assignments
    8. Pending submissions: `countPendingSubmissions` (`status = 'SUBMITTED'`)
  - `AnalyticsCacheService`: Course-scoped caching with key `analytics:course:{courseId}` and configurable 300s TTL via `SafeCache`.
  - `AnalyticsCacheInvalidationListener`: Invalidation listener listening to `LearnerEnrolledEvent`, `LearnerWithdrawnEvent`, `LessonCompletedEvent`, `SubmissionSubmittedEvent`, `SubmissionGradedEvent`, `AssignmentPublishedEvent`, `ReviewCreatedEvent`, and `CourseContentChangedEvent`.
  - Security: Authenticated instructor ID strictly extracted from `CurrentUserProvider`. Manipulation of course IDs across instructors yields 404 Not Found to prevent enumeration.
- **Frontend Architecture (`frontend/src/features/analytics/`)**:
  - `analyticsApi.js`: API client function `getCourseAnalytics(courseId)`.
  - `InstructorAnalyticsPage.jsx`:
    - 7 KPI cards: Total Enrollments, Active Learners, Completion Rate with progress bar, Average Assessment Score, Average Rating with star display, Assignment Submission Rate, Pending Submissions with direct review queue navigation.
    - 3 SVG/HTML visual charts: Lesson-Level Drop-Off Funnel, Assessment Score Distribution (5 buckets: 0-20%, 21-40%, 41-60%, 61-80%, 81-100%), and Assignment Performance Breakdown table.
    - UI states: Loading skeletons, error state with retry, empty state for new courses.
  - `AnalyticsSlotComponents.jsx` & `slots.js`: Contributes `CourseEditorAnalyticsHeaderLink` to `course-editor.header-actions` slot.
  - `routes.js`: Protected route `/instructor/courses/:courseId/analytics`.
- **Verification & Test Results**:
  - Backend: `InstructorAnalyticsIntegrationTest` (6/6 tests passing in MySQL 8.4 Testcontainers).
  - Regression: `FoundationIntegrationTest` (10/10 tests passing), `ReviewIntegrationTest` (4/4 tests passing), `NotificationIntegrationTest` (6/6 tests passing).
  - Frontend: `analytics.test.jsx` (7/7 tests passing in Vitest).
  - Full Frontend Regression Suite: 18/18 test files, 116/116 tests passing (0 failures).
  - ESLint: 0 errors; Prettier: 100% formatted; Production build: Vite compiled successfully in 1.61s.

---

## Phase 11: Learner Extras (Bookmarks & Notes) — Implementation Complete

### Summary
Phase 11 delivers personal learning enhancements to learners in PeerGeek:
1. **Course and Lesson Bookmarks**: Allows learners to bookmark any published course or enrolled lesson, toggle bookmark status, and manage their saved bookmarks from a dedicated `/learning/bookmarks` page.
2. **Private Lesson Notes**: Allows enrolled learners to write, update, and delete private rich-text notes for lessons inside the course player's side-panel slot.
3. **Strict Privacy**: A learner's notes are completely isolated and confidential. Other learners, instructors, and anonymous callers are forbidden from reading or altering them.
4. **Phase Isolation**: Disabling or removing bookmarks and notes modules has zero effect on discovery, enrollment, playback, assignments, reviews, notifications, or analytics.

### Implementation Details
- **Database Migrations**:
  - `V11_001__create_bookmarks.sql`: `bookmarks` table with `user_id`, `target_type` (`COURSE`, `LESSON`), `course_id`, `lesson_id`, stored generated `target_key` (`C:<id>` or `L:<id>`), and unique constraint `uq_bookmarks_user_target (user_id, target_key)`.
  - `V11_002__create_lesson_notes.sql`: `lesson_notes` table with `user_id`, `course_id`, `lesson_id`, `content` (`MEDIUMTEXT`), timestamps, and unique constraint `uq_lesson_notes_user_lesson (user_id, lesson_id)`.
- **Backend Architecture (`com.peergeek.bookmark` & `com.peergeek.note`)**:
  - `BookmarkController` & `BookmarkService`:
    - `POST/DELETE/GET` on `/api/v1/courses/{courseId}/bookmarks`
    - `POST/DELETE/GET` on `/api/v1/courses/{courseId}/lessons/{lessonId}/bookmarks`
    - `GET /api/v1/bookmarks[?type=COURSE|LESSON]` & `DELETE /api/v1/bookmarks/{id}`
    - Idempotent toggle and creation, soft-delete safe.
  - `LessonNoteController` & `LessonNoteService`:
    - `GET/PUT/POST/DELETE` on `/api/v1/courses/{courseId}/lessons/{lessonId}/notes`
    - Authorization: verifies active enrollment via `enrollmentRepository.existsByUserIdAndCourseIdAndStatus(userId, courseId, ACTIVE)`; unenrolled access returns `403 FORBIDDEN` with code `ENROLLMENT_REQUIRED`.
    - Privacy: User is derived solely from `CurrentUserProvider.requireCurrentUser()`. All operations filter by `userId`, completely preventing cross-user access or tampering.
    - Content validation: Limit 50,000 characters. Content is never logged in logs or audit trails.
- **Frontend Architecture (`frontend/src/features/bookmarks/` & `frontend/src/features/notes/`)**:
  - `bookmarksApi.js` & `notesApi.js`: Centralized API clients using `apiRequest`.
  - `BookmarkToggle.jsx`: Accessible toggle with optimistic TanStack Query cache updates.
  - `BookmarksPage.jsx`: Filterable listing ("All", "Courses", "Lessons") with loading skeletons, empty state, and remove action.
  - `BookmarkSlotComponents.jsx` & `slots.js`: Contributes `CourseBookmarkSlot` to `course-page.enroll-area` (order 15) and `player.header-actions` (order 15), and `LessonBookmarkSlot` to `player.lesson-actions` (order 15).
  - `navigation.js`: Registers "Bookmarks" in authenticated student navigation.
  - `routes.js`: Protected route `/learning/bookmarks`.
  - `LessonNotesPanel.jsx`: Responsive note editor with key-based isolation per lesson, unsaved edits tracking, word/character counter, delete confirmation, and privacy banner. Contributed to `player.side-panel` slot (order 10).
- **Verification & Test Results**:
  - Backend: `BookmarkIntegrationTest` (4/4 tests passing), `LessonNoteIntegrationTest` (4/4 tests passing), `FoundationIntegrationTest` (10/10 tests passing).
  - Frontend: `bookmarks.test.jsx` (6/6 tests passing), `notes.test.jsx` (4/4 tests passing).
  - Full Frontend Suite: 20/20 test files, 126/126 tests passing (0 failures).
  - ESLint: 0 errors; Prettier: 100% formatted; Vite build: compiled successfully.

---

---

## Phase 12: Production Hardening, End-to-End Testing & Release (COMPLETE)

### Objectives
Execute the final release-readiness phase of the 13-phase roadmap strictly according to the official `PeerGeek – Phased Production Plan`, Feature Specification Section 13 (Security) and Section 16 (Core Flow), and Technical Architecture Blueprint Section 11. Verify that PeerGeek is secure, reliable, performant, deployable, observable, recoverable, and ready for release.

### Implementation Details
- **Feature Specification Section 16 Core Flow Verification (`CoreUserFlowIntegrationTest.java`)**:
  - Implemented 13 sequential integration test steps exercising the full learner and instructor lifecycle:
    1. User Registration & Login (Instructor, Learner 1, Learner 2)
    2. Course Authoring (Draft course, modules, lessons, TipTap rich text, project assignment, assignment publishing)
    3. Readiness Validation & Publishing (Fingerprint run, asynchronous check completion, course publishing)
    4. Course Discovery & Preview (Catalog search, category filtering, public course preview)
    5. Course Enrollment (Instant enrollment, active enrollment status, enrolled courses listing)
    6. Playback & Lesson Completion (Continue lesson, lesson content fetch, lesson completion via PUT `/completion`, 100% course progress)
    7. Learner Extras (Course bookmark toggle, bookmarks listing, private lesson note creation, strict privacy rejection for non-enrolled learner)
    8. Assignment Attempt & Submission (Start attempt with optimistic concurrency versioning, draft autosave, assignment submission)
    9. Instructor Review & Grading (Submissions queue inspection, submission detail retrieval, grading with marks and feedback, learner submission inspection)
    10. In-App Notifications (Notification event consumption, unread count check, mark notification as read)
    11. Reviews & Community Reports (Course review submission with star rating, community issue report submission, instructor report moderation)
    12. Instructor Analytics (Authoritative metrics verification: 100% completion rate, 95.0 average score, 5.0 average rating, drop-off and assignment stats)
    13. Second Learner Enrollment (Second user enrollment and analytics update to 2 active learners)
  - **Result**: 13/13 tests PASS.

- **Security Hardening & Endpoint Authorization Matrix (`EndpointAuthorizationMatrixTest.java`)**:
  - Validated anonymous rejection (401 UNAUTHORIZED) across 20+ protected endpoints.
  - Validated public route allowlist (`/api/v1/categories`, `/api/v1/public/courses`, `/actuator/health/liveness`, `/actuator/info`).
  - Validated cross-instructor isolation: accessing another instructor's course returns `404 NOT_FOUND` to prevent resource enumeration.
  - Validated cross-learner isolation: accessing another learner's private notes returns `403 FORBIDDEN`; notifications are strictly bound to JWT principal.
  - **Result**: 4/4 tests PASS.

- **Sliding-Window Rate Limiting (`RateLimitingFilter.java`)**:
  - Applied to `/api/v1/auth/*` (10 req/min) and `/api/v1/courses/*/reports` (10 req/min).
  - Uses Redis sliding-window with atomic script / fallback in-memory degradation if Redis is temporarily unreachable.
  - Emits `Retry-After: 60` and standard `ApiErrorResponse` (`429 TOO_MANY_REQUESTS`, code `RATE_LIMIT_EXCEEDED`).
  - Tested in `RateLimitingIntegrationTest.java`: allowed requests, rate-limit rejection, and recovery window verified. (3/3 tests PASS).

- **Production Security Headers (`SecurityConfig.java`)**:
  - Content Security Policy (CSP): `default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: https: blob:; media-src 'self' https: blob:; connect-src 'self' https:; frame-ancestors 'none'; object-src 'none'; base-uri 'self';`
  - Frame Options: `DENY`
  - Content Type Options: `nosniff`
  - Referrer Policy: `strict-origin-when-cross-origin`
  - Strict Transport Security (HSTS): max-age 31536000 with subdomains.

- **Performance & Database Indexes (`V12_001__performance_indexes.sql`)**:
  - Additive migration adding `idx_course_progress_course_completed` on `course_progress(course_id, completed_at)` for analytics queries.
  - Additive migration adding `idx_courses_category_status` on `courses(category_id, status)` for discovery queries.
  - Total Flyway migrations: 36 applied without defect; 32 application tables verified intact.

- **Load Testing & Cache Benchmark (`scripts/load-test.mjs`)**:
  - Repeatable load test against discovery catalog, popular courses, and player preview.
  - Concurrency: 10 workers, 600 total requests.
  - Throughput:
    - Discovery catalog: 378.93 req/sec (p50: 15.79 ms, p95: 65.14 ms)
    - Popular courses: 336.76 req/sec (p50: 17.23 ms, p95: 89.77 ms)
    - Course preview: 240.10 req/sec (p50: 11.48 ms)
  - Error rate: **0.00%**. Cache hits validated via HTTP headers and response times.

- **Disaster Recovery Backup & Restore Drill (`scripts/backup-restore-drill.ps1`)**:
  - Automated non-destructive drill in ephemeral MySQL 8.4 container.
  - Created consistent `mysqldump` backup artifact (11.5 KB).
  - Restored into isolated database `peergeek_drill_restore`.
  - Verified 4 tables, categories, users, and course records intact without data loss or corruption.
  - Output logged in `docs/backup_restore_drill.log`.

- **Production Docker Containers & CI/CD**:
  - Updated `backend/Dockerfile` and `frontend/Dockerfile` with container healthchecks.
  - Successfully built production images `peergeek-frontend:release` and `peergeek-backend:release`.
  - Created `.github/workflows/release.yml` with comprehensive build, test, audit, and packaging pipeline.

- **Documentation & Operations**:
  - Authored comprehensive `docs/RUNBOOK.md` detailing architecture, health probes, disaster recovery, secret management, and zero-downtime rollback.
  - Updated `README.md` with full 13-phase capabilities and operator verification commands.
  - Updated `docs/phaseStatus.md` with complete Phase 12 checklist and verification evidence.

---

## Phase 14: Basic Admin Monitoring Dashboard

- **Backend Architecture & Security**:
  - Implemented `AdminDashboardController` under `/api/v1/admin/**` protected strictly with `@PreAuthorize("hasRole('ADMIN')")`.
  - Non-admin callers receive `403 FORBIDDEN`; unauthenticated callers receive `401 UNAUTHORIZED`.
  - Implemented `AdminDashboardService` providing:
    - `GET /api/v1/admin/dashboard/summary`: Live counts for total users, students, teachers, total courses, published courses, open reports (`PENDING` or `IN_REVIEW`), and total activity records.
    - `GET /api/v1/admin/dashboard/activities`: Chronological activity feed sorted newest first, paginated with batch lookups to eliminate N+1 queries.
    - `GET /api/v1/admin/reports`: Filterable by status and searchable by course title, with reporter details and resolution notes.
    - `PATCH /api/v1/admin/reports/{reportId}` & `PUT /api/v1/admin/reports/{reportId}/status`: Allows administrators to update issue report status (`IN_REVIEW`, `RESOLVED`, `DISMISSED`) with resolution notes.
    - `GET /api/v1/admin/users`: Searchable and filterable by role (`USER`, `ADMIN`), with account and verification status.
    - `GET /api/v1/admin/courses`: Filterable by publication state (`PUBLISHED`, `DRAFT`, `UNPUBLISHED`), with instructor profile and active enrollment counts.
  - Added repository methods with batch joins: `UserRepository.findAdminUsers`, `CourseRepository.findAdminCourses`, `EnrollmentRepository.countActiveByCourseIds`, `ReportRepository.findAdminReports`, and `AuditLogRepository.findAllByOrderByCreatedAtDesc`.
  - Integrated `AuditService` in `ReportService` and `ReviewService` ensuring `REPORT_CREATED`, `REPORT_STATUS_UPDATED`, and `REVIEW_CREATED` events are permanently captured in live audit records alongside existing auth, course, and enrollment events.

- **Frontend React UI**:
  - Created `AdminDashboardPage` under `/admin`, wrapped with `AdminRoute` that restricts access to users with `role: "ADMIN"`.
  - Header navigation dynamically displays "Admin" link only when the authenticated user possesses the `ADMIN` role.
  - Features 6 real-time summary cards, including warning badges on open issue reports.
  - Provides 4 dedicated monitoring sections:
    1. **Recent Activities**: Chronological feed with event icons, user tags, timestamps, and details.
    2. **Course Issues**: Filter by status, search by course, and full-detail resolution modal.
    3. **User Monitoring**: Role filters, search by name/email, verification badges, and account status.
    4. **Course Overview**: Status filters, search by title, instructor metadata, and active learner counts.

- **Testing & Verification**:
  - Backend integration test suite `AdminDashboardIntegrationTest`: 7/7 tests passing (100%).
  - Frontend test suite `src/features/admin/admin.test.jsx`: 10/10 tests passing (100%).
  - ESLint: 0 errors across frontend codebase.
  - Production build: `vite build` completed successfully.

---

## Bug Fix: Course Issue Reports Not Appearing in Admin Dashboard

### Root Cause Analysis
1. **Stale Backend Server Process**:
   - The Spring Boot server process on port 8080 had been executing continuously for ~12 hours before `AdminDashboardController` was created. Because the server was not restarted, HTTP requests to `/api/v1/admin/reports` and `/api/v1/admin/dashboard/summary` returned `404 Not Found`, causing the Admin Dashboard UI to fail with loading errors.
2. **Persistence Verification**:
   - Inspected `community_reports` database table. Submissions via `POST /api/v1/courses/{courseId}/reports` were successfully persisted (`id=3, course_id=11, reporter_user_id=1, category='BROKEN_RESOURCE', details='testing', status='PENDING'`). No duplicate tables or persistence defects existed.
3. **Repository Query Optimization**:
   - Enhanced `ReportRepository.findAdminReports` query so that the search filter seamlessly matches against both the course title and the report's text details, handling null parameters cleanly in Hibernate 6.
4. **Cache Synchronization**:
   - In `ReportModal.jsx`, added React Query query client cache invalidation for `["admin", "reports"]`, `["admin", "summary"]`, and `["admin", "activities"]` upon successful report submission.
5. **Diagnostic Logging**:
   - Added structured, minimal SLF4J logging in `ReportService` (logging report creation attempts, success with report ID, and failure warnings) and `AdminDashboardService` (logging query filters, retrieved result counts, and admin moderation actions). Zero credentials or tokens are logged.

### Regression Testing & Live End-to-End Verification
- **Automated Tests**:
  - Added `studentReportAppearsInAdminDashboard` and `invalidReportSubmissionsFail` to `AdminDashboardIntegrationTest.java`.
  - All 9/9 tests pass (100%).
  - Ran full frontend test suite: 21 test files, 149/149 tests pass (100%).
- **Live Server Verification**:
  - Restarted Spring Boot application on port 8080.
  - Authenticated as `admin@peergeek.local` and queried `GET /api/v1/admin/reports`.
  - Confirmed the user's report `id=3` (`courseId=11`, `category='BROKEN_RESOURCE'`, `details='testing'`, `status='PENDING'`) is returned with correct course and reporter information.
  - Tested live submission of a new report (ID 4) and verified it immediately appears in the admin dashboard and can be filtered and moderated to `RESOLVED`.

---

## Phase 14 Enhancement: Interactive Admin Dashboard Cards & User/Course Moderation

### 1. Requirements & Architectural Scope
- **Interactive Summary KPI Cards**:
  - `Total Users`: Navigates to Users tab with all filters reset.
  - `Students`: Navigates to Users tab pre-filtered with `mode=STUDENT`.
  - `Teachers`: Navigates to Users tab pre-filtered with `mode=INSTRUCTOR`.
  - `Total Courses`: Navigates to Courses tab with status `ALL`.
  - `Published Courses`: Navigates to Courses tab pre-filtered with status `PUBLISHED`.
  - `Open Issues`: Navigates to Reports tab pre-filtered with status `OPEN` (`PENDING` or `IN_REVIEW`).
  - Implemented as accessible, focusable `<button type="button">` elements with keyboard navigation (`Tab` + `Enter`/`Space`), focus rings (`focus:ring-2`, `focus:ring-offset-2`), and hover elevation (`hover:-translate-y-0.5`, `hover:shadow-md`).
- **Student & Instructor Account Directory**:
  - Distinct filters for account role (`USER`, `ADMIN`), user mode (`STUDENT` vs `INSTRUCTOR` via `lastMode`), and account status (`ALL`, `Active`, `Blocked`).
  - Live query search on name or email.
  - Comprehensive User Details Modal displaying user ID, display name, email, role, active mode, email verification status, account status, and registration timestamp.
  - Self-block prevention: Logged-in admin account cannot be blocked.
  - Admin-block protection: Other administrator accounts cannot be blocked from this view.
- **User Block/Unblock & Active Session Revocation**:
  - Dedicated Block/Unblock confirmation dialog explaining immediate session termination and requiring moderation justification (`reason`).
  - Backend `PATCH /api/v1/admin/users/{userId}/status`: Updates `users.enabled`, revokes all active refresh tokens (`refreshTokenRepository.revokeAllByUserId(userId)`), and records `USER_BLOCKED` or `USER_UNBLOCKED` in `audit_logs`.
  - Security constraints: Self-block and admin-block attempts return HTTP 400 `CANNOT_BLOCK_SELF` / `CANNOT_BLOCK_ADMIN`. Non-admin callers receive HTTP 403 `FORBIDDEN`.
- **Course Details & Moderation**:
  - Displays Course ID, title, instructor metadata, category, publication status, active learner enrollment count, and open issues count.
  - Course Moderation Modal allows switching between `PUBLISHED`, `UNPUBLISHED`, and `DRAFT`.
  - Unpublishing or suspending a course safely preserves all learner enrollments, lessons, reviews, and progress math in the database.
  - Event-driven index synchronization: Emits `CourseUnpublishedEvent` or `CoursePublishedEvent` on transaction commit, updating `public_course_index` so public search and `/api/v1/public/courses/{id}` reflect status immediately.
  - Records `COURSE_UNPUBLISHED` or `COURSE_PUBLISHED` in `audit_logs` with admin user ID and moderation reason.
- **Course Issues Linked to Course Moderation**:
  - Added "🛡️ Inspect & Moderate Course" button inside the Report Review modal.
  - Dynamically loads course metadata via `GET /api/v1/admin/courses/{courseId}` and pre-populates course moderation modal with report context banner ("📋 Moderating in response to Report #...").
  - Explicit separation of concerns: Moderating the course does not automatically close or resolve the community report.

### 2. Files Modified
- `backend/src/main/java/com/peergeek/user/UserEntity.java`: Added `setEnabled(boolean)`.
- `backend/src/main/java/com/peergeek/user/UserRepository.java`: Added `mode` (`lastMode`), `enabled`, and `query` parameters to `findAdminUsers`.
- `backend/src/main/java/com/peergeek/course/CourseRepository.java`: Added `@Modifying(clearAutomatically = true)` to `transitionStatus` to clear Hibernate cache.
- `backend/src/main/java/com/peergeek/report/ReportRepository.java`: Added `countOpenReportsByCourseIds` and updated `findAdminReports` to support collection filtering for `OPEN` (`PENDING` + `IN_REVIEW`).
- `backend/src/main/java/com/peergeek/admin/AdminDashboardService.java`: Implemented `updateUserStatus`, `getCourseById`, `updateCourseStatus`, and updated `getReports` for `status=OPEN`.
- `backend/src/main/java/com/peergeek/admin/AdminDashboardController.java`: Added `GET /api/v1/admin/courses/{courseId}`, `PATCH /api/v1/admin/courses/{courseId}/status`, `PATCH /api/v1/admin/users/{userId}/status`, and updated query parameters for users and reports.
- `backend/src/test/java/com/peergeek/admin/AdminDashboardIntegrationTest.java`: Added 7 comprehensive regression tests covering user blocking, session lockout, unblocking, self-block protection, non-admin rejection, course unpublishing and restoration, discovery hiding verification, and report preservation.
- `frontend/src/features/admin/adminApi.js`: Added `updateAdminUserStatus`, `getAdminCourseById`, `updateAdminCourseStatus`, and updated `getAdminUsers` with `mode` and `enabled`.
- `frontend/src/features/admin/AdminDashboardPage.jsx`: Implemented interactive summary buttons, user filters, user details modal, user block dialog, course moderation modal, and report inspection flow.
- `frontend/src/features/admin/admin.test.jsx`: Added comprehensive test coverage for card navigation, user details and blocking, course moderation, and report-to-course flow.
- `docs/phaseStatus.md`: Updated Phase 14 documentation and verification matrix.
- `BUILD_PROGRESS.md`: Documented implementation details, architecture, and verification results.

### 3. Verification & Automated Test Results
- **Backend Tests (`mvnw test -Dtest=AdminDashboardIntegrationTest`)**:
  - **16/16 tests PASS (100%)**.
  - All lifecycle transitions, authorization constraints, token revocations, discovery index events, and audit logging verified.
- **Frontend Code Quality & Tests**:
  - `src/features/admin/admin.test.jsx`: 14/14 tests passing (100%).
  - `npm run lint`: 0 errors.

---

## Assignment Workflow Update: Restriction to MCQ-Only Assessments

### 1. Objective & Architecture
- **Audit Findings**:
  - Validated that the existing architecture already supported end-to-end multiple-choice questions (single choice and multiple choice) with automated grading, timer countdowns, autosaving drafts, submission tracking, and immediate result display.
  - To align with current platform requirements, the instructor creation and publishing workflows were restricted exclusively to **MCQ-only assessments**.
- **Instructor Flow Restrictions**:
  - In `InstructorAssignmentsListPage.jsx`: Removed `PROJECT` kind selection options. The creation dialog now defaults and restricts assignment creation strictly to `kind = QUESTIONS`.
  - In `AssignmentBuilderPage.jsx`: Removed `+ Descriptive` and `+ Code` question buttons. Kept `+ Single Choice (MCQ)` and `+ Multi Choice (MCQ)`. Restricted the question type dropdown to `MCQ_SINGLE` and `MCQ_MULTI`.
  - In `InstructorAssignmentService.java`:
    - `createAssignment`: Validates `request.kind() == AssignmentKind.QUESTIONS`. Rejects attempts to create `PROJECT` assignments with `400 VALIDATION_FAILED`.
    - `replaceQuestions`: Validates every question has `type == MCQ_SINGLE || type == MCQ_MULTI`. Rejects `DESCRIPTIVE` or `CODE` questions with `400 VALIDATION_FAILED`.
    - `publishAssignment`: Rejects publishing non-MCQ questions or `PROJECT` assignments.
- **Backward Compatibility & Historical Records**:
  - Historical student submissions, grades, and existing project assignments remain intact in the database schema (`assignments`, `assignment_questions`, `submissions`, `submission_answers`, `submission_files`).
  - Student taking experience (`TakeAssignmentPage.jsx`), autosave, scoring engine (`LearnerSubmissionService.executeScoring`), and results display are preserved with zero regression.

### 2. Verification & Automated Test Results
- **Backend Tests (`mvnw test "-Dtest=Assignment*Test,Submission*Test"`)**:
  - **22/22 tests PASS (100%)**:
    - `AssignmentAccessMatrixIntegrationTest`: 2 tests passed.
    - `AssignmentDefinitionIntegrationTest`: 6 tests passed.
    - `AssignmentLeakGuardTest`: 2 tests passed.
    - `AssignmentQueryCountGuardIntegrationTest`: 2 tests passed.
    - `AssignmentReadinessRuleAndQueryServiceTest`: 2 tests passed.
    - `AssignmentTimingAndExpiryIntegrationTest`: 2 tests passed.
    - `SubmissionAndGradingIntegrationTest`: 4 tests passed (including `mcqEndToEndStudentFlowAndSecurity`).
    - `SubmissionFilesIntegrationTest`: 2 tests passed.
- **Frontend Tests (`npm test`)**:
  - **21/21 test suites passed, 155/155 tests passed (100%)**.

---

## Student MCQ Assignment Attempts: End-to-End Reliability Hardening

### 1. Root Causes & Architectural Defect Analysis
1. **Attempt State Resolution Failure on Page Reload**:
   - `LearnerAssignmentDetailDto` serialized attempt summaries in the nested `attempt: { submissionId, status, ... }` object, while `TakeAssignmentPage.jsx` checked flat properties `assignment.hasAttempt` and `assignment.attemptStatus`.
   - On page refreshes or returning to in-progress/submitted assignments, `TakeAssignmentPage.jsx` defaulted to `BeforeStartView`. Clicking "Start" threw HTTP 409 `ALREADY_SUBMITTED`, preventing students from resuming or accessing their results.
2. **Result View Mismatch & Incomplete Rendering**:
   - `ResultView` in `TakeAssignmentPage.jsx` expected top-level `assignmentTitle` and `kind`, and looked for an obsolete `sub.answers` array and `opt.correct` property, whereas backend DTOs returned `q.pointsAwarded`, `q.maxPoints`, `q.selectedOptionIds`, and `opt.isCorrect`.
   - This caused results to display blank titles, `0 / undefined pts`, and failed to show the learner's selected answers or the correct answer badges.
3. **Autosave and Submit Race Condition**:
   - When a student typed or selected an answer and immediately submitted, or when an autosave was in flight, the frontend submission call sent an outdated version number, causing HTTP 409 `DRAFT_VERSION_CONFLICT`.
4. **Backend Option Validation Gap in Draft Saves**:
   - `LearnerSubmissionService.saveDraft` did not validate `selectedOptionIds` against the question's allowed options, nor did it enforce single-option cardinality on `MCQ_SINGLE` questions.
5. **Concurrency & Idempotency Vulnerabilities**:
   - `saveDraft` and `submit` used non-locking repository methods (`findByAssignmentIdAndUserId`), creating race conditions under concurrent requests.

### 2. Implemented Fixes & Architecture Hardening
- **Backend Enhancements**:
  - `LearnerAssignmentDetailDto.java`: Added `@JsonProperty("hasAttempt")` and `@JsonProperty("attemptStatus")` getters to ensure complete compatibility across frontend consumers.
  - `LearnerSubmissionDetailDto.java`: Added `assignmentTitle` and `kind` to the root record; added `@JsonProperty("id")` and `@JsonProperty("points")` to `LearnerSubmissionQuestionDto`; and added `@JsonProperty("correct")` to `SubmissionQuestionOptionDto`.
  - `LearnerSubmissionService.java`:
    - Updated `saveDraft` and `submit` to use `submissionRepository.findWithLockByAssignmentIdAndUserId` for pessimistic write locking.
    - In `saveDraft`: Enforced strict option validation (rejecting foreign/tampered IDs with HTTP 400), rejected multiple selections for `MCQ_SINGLE`, and deduplicated answer entries by `questionId` to prevent unique constraint conflicts.
    - In `submit`: Allowed `request.version() <= submission.getVersion()` to gracefully accept submissions following recent autosaves, and returned existing graded submissions idempotently.
    - In `executeScoring`: Filtered selected option IDs against valid question option sets to prevent score manipulation.
- **Frontend Enhancements ([TakeAssignmentPage.jsx](file:///c:/Users/DevanshTyagi/OneDrive%20-%20Academian%20India%20Private%20Limited/Desktop/PeerGeek2.0/frontend/src/features/assignments/TakeAssignmentPage.jsx))**:
  - Handled both nested `assignment.attempt?.status` and top-level `assignment.attemptStatus`/`hasAttempt`.
  - Created mutex-protected `executeAutosave` using `inFlightPromiseRef` and `versionRef` to prevent concurrent overlapping saves.
  - In `handleFinalSubmit`: Awaits pending/in-flight autosaves before submitting to ensure zero lost answers, catches 409 `ALREADY_SUBMITTED` idempotently, and disables submit buttons with clear progress and error states.
  - In `ResultView`: Mapped question points, learner selections, and correct answer badges accurately, checking both camelCase and legacy property variants.

### 3. Verification & Automated Test Results
- **Backend Tests (`mvnw test "-Dtest=Assignment*Test,Submission*Test"`)**:
  - **22/22 tests PASS (100%)**:
    - `SubmissionAndGradingIntegrationTest`: 4 tests passed, including `mcqEndToEndStudentFlowAndSecurity` verifying unenrolled access denial, start attempt idempotency, secrecy of correct answers before submission, draft option validation, draft persistence and recovery, auto-grading, submit idempotency, post-submit draft protection, and result display with conditional answer reveal.
- **Frontend Tests (`npm test`)**:
  - **21/21 test files passed, 155/155 tests passed (100%)**.
- **Frontend Lint & Build**:
  - `npm run lint`: 0 errors.
  - `npm run build`: Production bundle succeeded in 4.78s.

---

## Course Readiness Engine: End-to-End Bug Fix & Publishing Gate Hardening

### 1. Root Causes & Architectural Defect Analysis
1. **Enum Mismatch on Legacy Resource Data**:
   - The database table `resources` contained legacy rows with `purpose = 'LESSON'`.
   - The backend Java enum `com.peergeek.file.ResourcePurpose` only declared `LESSON_RESOURCE` and `COURSE_THUMBNAIL`.
   - When the instructor clicked **Check Course Readiness**, `ReadinessService` called `LessonResourceQueryServiceImpl.getCourseResourceState()`, which triggered a Hibernate JPA query over `resources`. Hibernate threw:
     `java.lang.IllegalArgumentException: No enum constant com.peergeek.file.ResourcePurpose.LESSON`
     resulting in an unhandled 500 `INTERNAL_ERROR` and terminating the readiness check run abruptly.
2. **Hardcoded Rule Discovery & Incomplete Content Validation**:
   - `ReadinessService.java` instantiated `BasicReadinessRules` and `DeepReadinessRules` manually rather than injecting Spring's `List<ReadinessRule>` beans. This silently omitted rules like `AssignmentDraftExistsReadinessRule`.
   - `BasicReadinessRules.java` validated module empty states and lesson bodies, but lacked title presence checks (`MODULE_TITLE_REQUIRED`, `LESSON_TITLE_REQUIRED`), and generated generic messages without identifying the specific module or lesson by title.
   - Missing `GET /api/v1/instructor/courses/{courseId}/readiness/runs/latest` endpoint, forcing frontend consumers to infer latest runs or fail after page navigation.
   - Serialization mismatch: `ReadinessIssue` record lacked `@JsonProperty("code")` for its accessor `code()`, and `ReadinessRunView` lacked `@JsonProperty("ready")` for `isReady()`.
3. **Frontend Actionability & UX Gaps**:
   - `PublishReadinessPanel.jsx` button was missing clear loading indicators, explicit retry state on failures, and actionable navigable links to resolve flagged issues.
   - Blocking issues were rendered without severity badges, module/lesson contextual tags, or direct deep-links (`Go to Outline ->` / `Edit Details ->`).

### 2. Implemented Fixes & Architecture Hardening
- **Database & Enums**:
   - Added backward-compatible `LESSON` alias into `com.peergeek.file.ResourcePurpose.java`.
   - Created Flyway migration `V14_001__normalize_resource_purpose.sql` updating legacy `'LESSON'` rows to `'LESSON_RESOURCE'`.
- **Backend Readiness Engine**:
   - Updated `ReadinessService.java` to inject `List<ReadinessRule> rules`, ensuring all Spring-managed readiness rules are dynamically executed.
   - Added `getLatest(long courseId)` in `ReadinessService` and `GET /runs/latest` in `ReadinessController`.
   - Enhanced `BasicReadinessRules.java` with module/lesson title validation and human-readable contextual messages (e.g. `Module 'Module Name' has no lessons.`, `Lesson 'Lesson Name' has no required authored content.`).
   - Added `@JsonProperty("code")` on `ReadinessIssue.code()` and `@JsonProperty("ready")` on `ReadinessRunView.isReady()`.
- **Frontend Polish ([PublishReadinessPanel.jsx](file:///c:/Users/DevanshTyagi/OneDrive%20-%20Academian%20India%20Private%20Limited/Desktop/PeerGeek2.0/frontend/src/features/instructor-publish/PublishReadinessPanel.jsx))**:
   - Added `data-testid="check-course-readiness-btn"` with accessible loading spinner and progress status text.
   - Added success banner for publication-ready courses and blocking issue count alerts.
   - Implemented actionable issue cards with severity badges (`BLOCKING_ERROR`, `WARNING`), contextual scope tags, message, fix hints, and direct deep links to course outline and details.
   - Added retry button for failed readiness runs and query error boundaries.
   - Added `getLatestReadinessRun` in `publishApi.js`.

### 3. Verification & Automated Test Results
- **Backend Tests (`mvnw test "-Dtest=ReadinessPublishingIntegrationTest,ReadinessRegressionIntegrationTest,BasicReadinessRulesTest"`)**:
  - **14/14 tests PASS (100%)**:
    - `ReadinessRegressionIntegrationTest` (7 tests): Verified ready courses, missing modules, empty modules, empty lessons, multiple issues, unauthorized access, and publishing enforcement.
    - `ReadinessPublishingIntegrationTest` (4 tests): Verified lifecycle state gates and publish validation.
    - `BasicReadinessRulesTest` (3 tests): Verified unit-level rule evaluations and title checks.
- **Frontend Tests (`npm test`)**:
  - **21/21 test suites passed, 157/157 tests passed (100%)**.
  - `PublishReadinessPanel.test.jsx` (4 tests) passing 100%.
- **Frontend Lint & Build**:
  - `npm run lint`: 0 errors.
  - `npm run build`: Production bundle succeeded in 1.72s.
- **Live API & Service Verification**:
  - Executed live `POST /api/v1/instructor/courses/1/readiness/runs` and `GET /runs/latest` against running Spring Boot instance on port 8080: verified successful completion, 0 exceptions, and accurate detection of empty modules with actionable navigation links.
