# PeerGeek frozen contracts

This document defines the Phase 0 interfaces that later phases must preserve.

## API and serialization

- Application endpoints live under `/api/v1`. Operational Actuator and OpenAPI
  endpoints are infrastructure endpoints and are not application API routes.
- IDs are `BIGINT` auto-increment values and serialize as JSON numbers.
- Timestamps use UTC and ISO-8601 strings. The JVM, Hibernate JDBC layer, and
  MySQL session use UTC.
- Every API error has this shape (including an empty `fieldErrors` array when
  there are no field-specific errors):

  ```json
  {
    "timestamp": "2026-01-01T12:00:00Z",
    "status": 400,
    "error": "Bad Request",
    "code": "VALIDATION_FAILED",
    "message": "Request validation failed",
    "path": "/api/v1/example",
    "fieldErrors": [{ "field": "name", "message": "must not be blank" }]
  }
  ```

- `code` is a stable machine-readable identifier. Reserved codes include
  `VALIDATION_FAILED`, `NOT_FOUND`, `FORBIDDEN`, `CONFLICT`, `INTERNAL_ERROR`,
  `MALFORMED_JSON`, `TYPE_MISMATCH`, and `METHOD_NOT_ALLOWED`.
- Unexpected errors are logged with the `X-Request-Id` correlation value. Their
  response never exposes stack traces or internal exception messages.
- Every response carries `X-Request-Id`. A safe, supplied ID is echoed;
  otherwise the backend generates a UUID. The value is also stored in logging
  context for the request.

## Pagination

- Requests use `page` (zero-based, default `0`), `size` (default `20`, maximum
  `100`), and optional `sort=<field>,<dir>` where `dir` is `asc` or `desc`.
- The common `Pagination` helper applies the default and size cap.
- Responses use:

  ```json
  {
    "content": [],
    "page": 0,
    "size": 20,
    "totalElements": 0,
    "totalPages": 0
  }
  ```

## Persistence, events, and packages

- MySQL is the source of truth. Flyway owns the schema; Hibernate
  `ddl-auto=validate` is not allowed to create or update schema.
- Migration filenames use `V<phase>_<seq>__description.sql`; Phase 0 contains
  only `V0_001__baseline.sql`, which creates no application table. Phase 1 adds
  `V1_001` through `V1_004` for users, refresh tokens, password-reset OTPs, and
  audit logs. Phase 2 adds `V2_001` through `V2_007` for categories, courses,
  topics, modules, lessons, and lesson content. Phase 3 adds `V3_001` through
  `V3_003` for resources, expected lesson resources, and processing attempts.
  Phase 4 adds `V4_001` through
  `V4_005` for readiness runs/issues, content probe and link-check results, and
  per-course publish locks. Probe and link-check resource IDs intentionally
  have no foreign keys. Phase 15 adds `V15_001__add_username_to_users.sql` to
  introduce mandatory, case-insensitively unique usernames with backfill for
  existing users (`user_{id}`) and database constraint `uk_users_username`.
  Applied migrations are immutable.
- `BaseEntity` is an abstract `@MappedSuperclass` for future entities. It
  defines a `Long` identity and audited `createdAt`/`updatedAt` `Instant`s.
- Future Spring application events extend `DomainEvent`, which supplies
  `eventId` and `occurredAt`.
- Package ownership under `com.peergeek`: `config` owns wiring/settings;
  `exception` owns transport errors; `common` owns cross-domain primitives;
  `security`, `auth`, `user`, `course`, `enrollment`, `module`, `lesson`,
  `assignment`, `submission`, `review`, `file`, `notification`, and `audit`
  own only their respective domain responsibilities. Phase 1 implements
  `security`, `auth`, `user`, and `audit`; Phase 2 implements course authoring
  in `course`, `module`, and `lesson`; Phase 3 implements resources in `file`
  and storage/processing configuration in `config`. Later phases must keep
  other domains out of these packages.

## Authentication, users, and audit

- Public application endpoints are listed centrally in
  `com.peergeek.security.PublicEndpoints`. All other API endpoints require a
  valid bearer access token, except explicit test-only routes enabled under the
  `test` profile.
- Public production endpoints are `POST /api/v1/auth/register`, `login`,
  `refresh`, `forgot-password`, and `reset-password`, plus
  `GET /api/v1/users/{id}/public`, `GET /api/v1/users/by-username/{username}`, and `GET /api/v1/categories`. `POST
  /api/v1/auth/logout`, all `/me` endpoints, and every instructor authoring
  endpoint require authentication.
- JWT access tokens last 15 minutes by default. Refresh credentials are opaque,
  rotate on use, and are stored only as SHA-256 hashes in MySQL. Reusing a
  revoked token invalidates its full family. The raw refresh value exists only
  in the HttpOnly cookie, never JSON.
- The `PEERGEEK_REFRESH` cookie is scoped to `/api/v1/auth`, uses `SameSite=Lax`,
  and is `Secure` according to typed auth configuration (required in `prod`).
  Credentialed CORS allows only configured origins. CSRF is disabled because
  API authorization uses bearer headers; refresh-cookie requests are protected
  by the Lax same-site cookie policy and restrictive credentialed CORS.
- Roles are `USER` and `ADMIN`. Every normal registration is `USER`. `STUDENT`
  and `INSTRUCTOR` are persisted UI modes only and do not grant permissions.
- `CurrentUserProvider` in `com.peergeek.security` is the application-facing
  identity abstraction. Services use `OwnershipGuard`: `requireOwner` returns a
  forbidden response; `requireOwnerOrNotFound` hides resource existence.
- `AuditService.record(AuditEvent)` writes an append-only row. Audit details
  must not contain credentials or token data. Security audit writes fail
  closed: a security-sensitive operation does not report success if its audit
  row cannot be committed. Phase 1 introduces no read-style audit events.
- Phase 1 adds `users`, `refresh_tokens`, `password_reset_otps`, and
  `audit_logs`. Phase 2 adds `categories`, `courses`, `course_topics`,
  `course_modules`, `lessons`, and `lesson_contents`. Phase 15 adds mandatory
  `username` column to `users` with unique constraint `uk_users_username`.
  Public profile JSON includes `id`, `username`, `displayName`, `headline`, and `bio`.
- Phase 16 adds Complete User Profiles (`V16_001__complete_user_profiles.sql`):
  - Added columns to `users`: `avatar_key`, `avatar_url`, `skills`, `interests`, `teaching_expertise`, `github_url`, `linkedin_url`, `portfolio_url`, `website_url`, `show_education`, `show_social_links`, `show_learning_history`.
  - Added tables: `user_educations` and `user_projects` with FKs to `users(id)` and indexed foreign keys.
  - Endpoints: `GET /api/v1/users/me/profile`, `PUT /api/v1/users/me/profile`, `GET /api/v1/users/{id}/profile`, `GET /api/v1/users/by-username/{username}/profile`, `POST /api/v1/users/me/avatar`, `DELETE /api/v1/users/me/avatar`, `GET /api/v1/users/{id}/avatar`, education and project CRUD (`/api/v1/users/me/education`, `/api/v1/users/me/projects`), and `GET /api/v1/instructor/courses/{courseId}/students/{studentId}`.
  - Viewer Scenarios:
    1. Student views Student: Permitted general profile; education, social links, and completed achievements filtered by user's privacy flags; private email, grades, enrollments hidden.
    2. Instructor views Enrolled Student: Course-specific performance summary (lessons completed, progress percent, enrollment status/dates, assignment submissions with marks and status); strictly denies access to non-owned courses (403) or unenrolled students (404).
    3. Instructor views Instructor: Teaching expertise, published courses, teaching statistics (published count, enrolled learners, avg rating, review count), public projects.
    4. Instructor views Student not in their course: Permitted general profile only; private educational and course records forbidden.
- Course status storage has `DRAFT`, `PUBLISHED`, and `UNPUBLISHED` values.
  Phase 2 creates drafts only and exposes no status-transition endpoint.
  `thumbnail_resource_id` intentionally has no foreign key; Phase 3 owns
  resource records and their attachment semantics.

## Files and resources

- Phase 3 owns resource rows, upload policy, object storage, instructor resource
  management, and the existing course thumbnail reference. Files are uploaded
  directly to S3-compatible storage with short-lived signed `PUT` URLs; the
  browser sends the exact signed headers and confirms the resource with the
  backend after the object upload succeeds.
- Resource rows progress through `PENDING`, `UPLOADED`, `PROCESSING`, then
  `READY` or `FAILED`. Processing streams the stored object to validate its
  declared size and content signature and compute SHA-256. Lesson documents
  include PDF, OOXML, UTF-8 text/CSV, images, video, and external HTTP(S) links.
  Unsupported formats and mismatched content fail closed.
- Resource access uses ordered voters with deny precedence and default deny.
  UI mode does not grant access. Lesson resources, expected-resource
  declarations, course thumbnails, downloads, and management actions enforce
  course ownership in the backend. Deleted resources are hidden immediately.
- Lesson resource ordering is zero-based and gap-free. Reorder requests must
  contain the exact current resource ID set. Expected resource types are unique
  per lesson; minimum counts are between 1 and 20.
- Upload sizes, allowed extensions/MIME types, processing concurrency, URL
  expiry, abandoned upload age, retry age/count, and deleted-object grace are
  controlled by `PEERGEEK_RESOURCES_*` environment variables. Scheduled
  reconciliation expires abandoned uploads, retries stuck processors up to
  the configured attempt count, and removes objects for resources past their
  deletion grace period. Values and API shapes are documented in
  [docs/RESOURCES.md](./RESOURCES.md).
- Resource and thumbnail changes publish domain events only after the
  transaction commits. Security-sensitive ownership checks happen before
  presigning or storage operations.

## Course authoring and downstream structure contract

- `GET /api/v1/categories` is the only Phase 2 public endpoint and returns
  active categories only. All course, module, lesson, outline, and content
  endpoints require authentication, irrespective of UI mode.
- Authoring ownership is checked in services. A non-owner receives
  `404 NOT_FOUND`; deleted courses and their structures are invisible.
- A draft can be soft-deleted. Structure and content edits do not depend on
  status. Positions are zero-based and gap-free; reorder requests must contain
  the exact current ID set.
- `CourseContentChangedEvent` extends `DomainEvent` and is published after
  commit for each structure or lesson-content write. A rolled-back transaction
  publishes no event.
- Later phases must use the read-only `CourseStructureQueryService` in
  `com.peergeek.course` for course structure, lesson content, and owner
  snapshots. Its methods perform **no authorization checks**; each caller must
  apply its own policy (such as owner checks in Phase 4 or enrollment checks in
  Phase 6). Downstream phases must not import `ModuleRepository`,
  `LessonRepository`, or module/lesson entities.
- The query service also provides `getLessonLocation(lessonId)`, returning an
  optional `{ lessonId, moduleId, courseId, ownerId, courseDeleted }` snapshot,
  and `findExistingLessonIds(lessonIds)`, returning only IDs that still have a
  lesson row. These lookups do not authorize callers and use a fixed query
  count, independent of the number of supplied IDs.
- `getCourseMetadata(courseId)` returns an optional immutable snapshot of
  ownership, status/deletion state, version, title, description, category
  identity/activity, difficulty, topic names, and thumbnail resource ID. It
  performs no authorization; callers apply their own policy.
- `CourseLearnerViewAssembler.assemble(courseId)` composes course metadata,
  module/lesson content, and ready resource metadata through the read-only query
  seams. It performs **no authorization**, does not require a published status,
  and does not check enrollment. It does not issue resource URLs. Any controller
  or use case exposing this view must enforce publication and the caller's
  access/enrollment policy before returning it.
- `CourseThumbnailAccessor` in `com.peergeek.course` reads and writes only the
  existing `courses.thumbnail_resource_id` column. The getter returns an
  `Optional<Long>`; setting a resource ID does not add a foreign key or change
  course timestamps/version.
- `CourseStatusAccessor` reads a status snapshot and performs conditional
  status transitions only for non-deleted courses whose current status is in
  the caller-supplied allowed set. A successful transition increments the
  course version; only a transition to `PUBLISHED` updates `published_at`.
- Phase 4 readiness and publication routes are owner-only. Readiness starts
  asynchronously and returns an immutable report with stable rule codes,
  blocking errors, warnings, safe fix hints, and a deterministic fingerprint.
  Publish checks the latest completed report against current course metadata,
  structure, lesson document versions/content, expected resource state, and
  resource checksums, and rejects missing, stale, expired, or blocking reports.
- `POST /api/v1/instructor/courses/{courseId}/publish` transitions an owned
  `DRAFT` or `UNPUBLISHED` course to `PUBLISHED`. `POST
  /api/v1/instructor/courses/{courseId}/unpublish` transitions a published
  course to `UNPUBLISHED`. Both write an audit row transactionally; lifecycle
  events are emitted only after commit. A per-course database lock serializes
  these transitions.
- `GET /api/v1/instructor/courses/{courseId}/preview` is owner-only and returns
  a read-only instructor preview assembled through the course and resource
  query seams. It contains category name, module descriptions, lesson
  summaries/content, and ready resources without exposing object keys or
  internal presigned URLs. The frontend preview route uses the shared pure
  React lesson renderer.
- Safe link checks restrict schemes and ports, resolve and validate all
  addresses, pin each connection to the checked DNS address, revalidate each
  manual redirect, verify TLS hostnames, and bound time, redirects, concurrency,
  and response headers. Private and special-use addresses are denied unless
  explicitly enabled for a trusted environment; application startup fails if
  `READINESS_LINKS_ALLOW_PRIVATE_HOSTS=true` under the `prod` profile. Detailed
  rules and typed environment variables are documented in
  [READINESS.md](./READINESS.md).
- The video probe consumes a short-lived internal GET URL supplied by
  `InternalResourceUrlProvider`; the URL is passed as a distinct
  `ProcessBuilder` argument and is never fetched through a public API. ffprobe
  has a 30-second timeout, bounded stdout/stderr capture, an HTTP(S)-only
  protocol allowlist, and defensive JSON validation. `VIDEO_INVALID` means the
  media is malformed, has no video stream, or has no positive duration;
  `VIDEO_PROBE_UNAVAILABLE` means the probe executable could not run, timed
  out, or could not retrieve the object through its internal presigned URL.
  Only completed valid/invalid results are cached by resource ID and
  SHA-256 in `resource_probe_results`; unavailable outcomes are retried rather
  than cached. The resource download service supplies the internal URL only
  after checking that the object is ready and not deleted.
- Lesson documents use ProseMirror JSON format `tiptap-json-v1`. Only the
  documented text-node and mark whitelist is supported. Images, files, embeds,
  and arbitrary attributes are excluded.

## S3-compatible storage configuration

- `S3Config` binds `STORAGE_ENDPOINT`, `STORAGE_REGION`,
  `STORAGE_ACCESS_KEY`, `STORAGE_SECRET_KEY`, `STORAGE_BUCKET`, and
  `STORAGE_PATH_STYLE` to the `peergeek.storage.*` settings. Startup bucket
  verification additionally uses `STORAGE_CREATE_BUCKET_IF_MISSING`.
- Local Compose MinIO uses endpoint `http://minio:9000`, path-style access,
  static credentials, and `STORAGE_CREATE_BUCKET_IF_MISSING=true`.
- Real AWS S3 uses an empty `STORAGE_ENDPOINT`, `STORAGE_PATH_STYLE=false`, the
  bucket's `STORAGE_REGION`, and `STORAGE_CREATE_BUCKET_IF_MISSING=false`.
  Static `STORAGE_ACCESS_KEY` and `STORAGE_SECRET_KEY` must be set together;
  when both are empty, the AWS SDK default credentials provider chain is used.
  Creating a bucket at startup is rejected in the `prod` profile.
- Startup sends `HeadBucket` and fails with a clear message that directs the
  operator to verify bucket, region, endpoint, credentials/provider, and
  permissions. Application diagnostics do not include credential values.
- For direct browser access from `http://localhost:5173`, configure this bucket
  CORS JSON:

  ```json
  [
    {
      "AllowedOrigins": ["http://localhost:5173"],
      "AllowedMethods": ["PUT", "GET", "HEAD"],
      "AllowedHeaders": ["Content-Type", "Content-Length"],
      "ExposeHeaders": ["ETag"]
    }
  ]
  ```

## Frontend extension registries

- Feature `routes.js`, `navigation.js`, and `slots.js` modules export default
  arrays and are automatically discovered by the app registries.
- Routes declare `path`, `element`, and `visibility` (`public` or `protected`).
  Protected routes pass through `ProtectedRoute`, which now enforces the Phase 1
  session state.
- Navigation items have `id`, `label`, `route`, `order`, and `visibility`
  (`always`, `anonymous`, or `authenticated`); optional `placement` selects
  `header` (default) or `sidebar`, and optional `mode` filters a UI-mode-only
  entry. Lower `order` sorts first.
- Slot contributions have `id`, `name`, `order`, and a React `element`.
  Contributions render by ascending order. An empty slot renders no DOM.
- Frozen slot names:
  - `course-page.enroll-area` (receives `{ courseId }` prop)
  - `player.side-panel` (receives `{ courseId, lessonId }` props)
  - `player.lesson-actions` (Phase 6: receives `{ courseId, lessonId }` props)
  - `header.actions`
  - `lesson-editor.side-panel`
  - `course-details.thumbnail`
  - `course-editor.header-actions`
  - `home.sections` (Phase 5: homepage section extension)
- Additions should use feature registration modules rather than changing the
  shell or central registry implementation.

## Phase 5: Discovery, public preview, cache, and stats

### 1. Migrations and Tables
- Migration prefix range: `V5_xxx`
- Tables owned by `discovery`:
  - `public_course_index`: Denormalized read-model index of currently published courses (PK `course_id`, `utf8mb4_0900_ai_ci` collation, indexes on `(category_id, published_at)`, `difficulty`, `published_at`, `title(50)`).
  - `course_public_stats`: Public metrics store (PK `course_id`, `enrollment_count`, `completion_count`, `rating_average`, `rating_count`, `updated_at`). No foreign keys to other tables.

### 2. Explicit Public Endpoints (GET only)
Only the following exact path patterns are marked public in `SecurityConfig`:
- `GET /api/v1/public/courses`
- `GET /api/v1/public/courses/popular`
- `GET /api/v1/public/courses/{courseId:[0-9]+}`
- `GET /api/v1/public/courses/{courseId:[0-9]+}/thumbnail`
- `GET /api/v1/public/categories`

Wildcards like `/api/v1/public/**` remain strictly forbidden. Tokens sent to public endpoints are ignored.

### 3. Public Thumbnail Download Flow
The thumbnail endpoint (`GET /api/v1/public/courses/{courseId}/thumbnail`) is the single sanctioned public use of `ResourceDownloadService`. The endpoint itself performs strict authorization:
- Course exists and is `PUBLISHED`.
- Thumbnail resource belongs to that course.
- Purpose is `COURSE_THUMBNAIL` and status is `READY`.
Returns `302 Found` to fresh presigned GET URL with `Cache-Control: public, max-age=60`.

### 4. Cache Key Registry
All cached payloads use `SafeCache` wrapped in `{ "schemaVersion": 1, "payload": ... }`:
- `course:preview:{id}` (TTL: 10m)
- `course:structure:{id}` (TTL: 10m)
- `courses:popular` (TTL: 2m)
- `categories:all` (TTL: 10m)

Cache hits on course preview always verify `isPublished(courseId)` against the database before serving.

### 5. Stats Extension Point
Discovery exposes `CourseStatsRecorder` (push interface) for Phase 6 (enrollment) and Phase 8 (ratings):
- `recordEnrollmentStats(long courseId, int enrollmentCount, int completionCount)`
- `recordRatingStats(long courseId, BigDecimal ratingAverage, int ratingCount)`
Absolute, idempotent values called after commit.

### 6. Additive Earlier-Phase Touches
- `CourseStructureQueryService`:
  - `Optional<PublishedCourseSnapshot> getPublishedCourse(Long courseId)`
  - `boolean isPublished(Long courseId)`
  - `List<Long> listPublishedCourseIds(Long afterId, int limit)`
- `UserService`:
  - `Map<Long, PublicProfileDto> getPublicProfiles(Collection<Long> userIds)` (reusing existing public profile allowlist)

## Phase 6: Enrollment & Learner Experience

### 1. Migrations and Tables
- Migration prefix range: `V6_xxx`
- Tables owned by `enrollment`:
  - `enrollments`: Enrollment entity (PK `id`, FK `user_id` -> `users(id)`, FK `course_id` -> `courses(id)`, `status` ENUM('ACTIVE','WITHDRAWN'), `enrolled_at`, `withdrawn_at`, `last_lesson_id` NULL with NO FK, `last_accessed_at`, `version`, `created_at`, `updated_at`). Unique `(user_id, course_id)`, indexes `(user_id, status, updated_at)` and `(course_id, status)`.
  - `lesson_progress`: Individual lesson completion (PK `id`, FK `enrollment_id` -> `enrollments(id)` ON DELETE CASCADE, `lesson_id` with NO FK, `completed_at`, `created_at`, `updated_at`). Unique `(enrollment_id, lesson_id)`, index `(lesson_id)`.
  - `course_progress`: Aggregate completion rollups (PK `enrollment_id` FK -> `enrollments(id)` ON DELETE CASCADE, `course_id`, `total_lessons`, `completed_lessons`, `percent`, `completed_at`, `stale`, `computed_at`, `updated_at`). Index `(course_id, stale)`.
- Foreign key policy: FKs are strictly allowed to `users`, `courses`, and internally among Phase 6 tables. Absolutely NO foreign keys to `lessons`, `modules`, or `resources`.

### 2. Learner Visibility and Indistinguishability Rules
- Nonexistent, deleted (`deleted = true`), or draft/unpublished courses (for users who are not active learners) return byte-identical HTTP 404 (`NOT_FOUND`).
- Published courses return HTTP 403 (`ENROLLMENT_REQUIRED`) for signed-in non-enrolled or withdrawn users when accessing outline, lesson content, or continue routes.
- Anonymous requests return HTTP 401 (`UNAUTHORIZED`).
- Learners retain active access if a course is subsequently unpublished (Assumption A4). Access is terminated only upon course deletion or learner withdrawal.
- Denied or invisible resources return HTTP 404 (existence is never revealed).
- All learner responses carry `Cache-Control: no-store`.

### 3. Extension Points and Seams
- **`EnrolledLearnerVoter`**: New Spring bean implementing `ResourceAccessVoter` (`@Order(200)`). Grants `VIEW` and `DOWNLOAD` (never `MANAGE`) when caller has an `ACTIVE` enrollment in a non-deleted course, resource purpose is `LESSON_RESOURCE`, and status is `READY`. Abstains otherwise (never returns `DENY`). Memoizes checks per request.
- **`EnrollmentQueryService`**: The ONLY interface permitted for later phases (Phases 7–11) to inspect enrollment data:
  - `boolean isActivelyEnrolled(Long userId, Long courseId)`
  - `Optional<Long> getActiveEnrollmentId(Long userId, Long courseId)`
  - `Set<Long> filterEnrolledCourseIds(Long userId, Collection<Long> courseIds)`
  - `Map<Long, Boolean> checkEnrollmentsBulk(Collection<Long> userIds, Long courseId)`
  - `PageResponse<Long> listActiveLearnerIds(Long courseId, Pageable pageable)`
- **Domain Events**: Published `AFTER_COMMIT` extending `DomainEvent`:
  - `LearnerEnrolledEvent(courseId, userId, enrollmentId)`
  - `LearnerWithdrawnEvent(courseId, userId)`
  - `LessonCompletedEvent(courseId, userId, lessonId, completedLessons, totalLessons)`
- **`CourseStatsRecorder` Integration**: `StatsRefresher` listens to Phase 6 domain events after commit and pushes absolute idempotent counts (`enrollmentCount`, `completionCount`) to `CourseStatsRecorder.recordEnrollmentStats`.
- **Frontend Slots**:
  - `course-page.enroll-area`: receives `{ courseId }`
  - `player.side-panel`: receives `{ courseId, lessonId }`
  - `player.lesson-actions`: receives `{ courseId, lessonId }`

### 4. Additive Earlier-Phase Touches
- `CourseStructureQueryService`:
  - `Map<Long, CourseMetadataSnapshot> getCourseMetadataBulk(Collection<Long> courseIds)` (additive read-only bulk lookup seam to avoid N+1 queries in My Learning).

## Phase 7: Assignments & Assessments

### 1. Migrations and Tables
- Migration prefix range: `V7_xxx`
- Tables owned by `assignment`:
  - `assignments`: Definitions and metadata (`id`, `course_id` FK -> `courses(id)`, `module_id` NULL, `lesson_id` NULL, `kind` ENUM('PROJECT','QUESTIONS'), `title`, `instructions`, `status` ENUM('DRAFT','PUBLISHED','UNPUBLISHED'), `max_points`, `opens_at`, `due_at`, `time_limit_minutes`, `allow_late`, `allow_text`, `allow_files`, `show_correct_answers`, `attempt_count`, `published_at`, `version`, `created_by` FK -> `users(id)`, `created_at`, `updated_at`).
  - `assignment_questions`: Question sets (`id`, `assignment_id` FK -> `assignments(id)` ON DELETE CASCADE, `position`, `type` ENUM('MCQ_SINGLE','MCQ_MULTI','DESCRIPTIVE','CODE'), `prompt`, `points`, `options` JSON, `code_language`).
- Tables owned by `submission`:
  - `submissions`: Single attempt per learner (`id`, `assignment_id` FK -> `assignments(id)`, `user_id` FK -> `users(id)`, `status` ENUM('IN_PROGRESS','SUBMITTED','GRADED'), `started_at`, `deadline_at`, `submitted_at`, `late`, `auto_submitted`, `auto_graded`, `text_answer`, `marks`, `total_points`, `overall_feedback`, `graded_by` FK -> `users(id)`, `graded_at`, `version`, `created_at`, `updated_at`). UNIQUE `(assignment_id, user_id)`.
  - `submission_answers`: Learner responses per question (`id`, `submission_id` FK -> `submissions(id)` ON DELETE CASCADE, `question_id`, `answer_text`, `selected_option_ids` JSON, `auto_points`, `points`, `feedback`, `updated_at`). UNIQUE `(submission_id, question_id)`.
  - `submission_files`: Learner uploaded project files (`id`, `submission_id` FK -> `submissions(id)` ON DELETE CASCADE, `uploaded_by` FK -> `users(id)`, `original_filename`, `object_key` UNIQUE, `mime_type`, `size_bytes`, `checksum_sha256`, `status` ENUM('PENDING','READY','FAILED'), `failure_reason`, `created_at`, `updated_at`).
- Foreign key policy: FKs allowed only to `users`, `courses`, and internally among Phase 7 tables. No FKs to `modules`, `lessons`, or `resources`.

### 2. Error Codes
- `ASSIGNMENT_LOCKED`: Attempted to modify or replace questions/kind on an assignment with existing attempts (HTTP 409).
- `ASSIGNMENT_NOT_OPEN`: Attempted to start an attempt before `opens_at` (HTTP 409).
- `DEADLINE_PASSED`: Attempted to start or submit after `due_at` when `allow_late = false` (HTTP 409).
- `ATTEMPT_EXPIRED`: Attempted to save or submit after `deadline_at + grace` (HTTP 409; auto-finalizes attempt).
- `ALREADY_SUBMITTED`: Attempted to save or duplicate submit on a finalized attempt (HTTP 409).
- `DRAFT_VERSION_CONFLICT`: Version mismatch during draft autosave (HTTP 409).
- `GRADING_INCOMPLETE`: Manual grading submitted without points for all non-auto questions (HTTP 400).

### 3. Events & Read Seams
- **Domain Events** (published `AFTER_COMMIT`):
  - `AssignmentPublishedEvent(courseId, assignmentId, title)`
  - `SubmissionSubmittedEvent(courseId, assignmentId, submissionId, userId, late, autoSubmitted)`
  - `SubmissionGradedEvent(courseId, assignmentId, submissionId, learnerId, graderId, totalPoints, maxPoints, autoGraded, regrade)`
- **Read-Only Query Services** (exclusive seams for later phases):
  - `AssignmentQueryService`: dead-line queries, learner submission status lookups, and assignment summaries.
  - `SubmissionQueryService`: completion and score distribution aggregations.
- **ReadinessRule**:
  - `ASSIGNMENT_DRAFT_EXISTS` [WARNING, FAST]: Non-blocking warning when course has draft assignments.

### 4. Frontend Seams & Slots
- Added slot: `player.header-actions` (receives `{ courseId }`).
- Registered contributions into `player.header-actions`, `player.lesson-actions`, and `course-editor.header-actions`.

### 5. Additive Earlier-Phase Touches
- `EnrollmentQueryService`:
  - `PageResponse<Long> listActiveCourseIds(Long userId, Pageable pageable)` (read-only query seam for learner cross-course assignment tracking).
- `file` package:
  - Made stateless content sniffing helpers public without altering any behavior.

---

## Phase 10: Instructor Analytics Contracts

### 1. Read-Only Analytics API Contract
- Route: `GET /api/v1/instructor/courses/{courseId}/analytics`
- Method: `GET`
- Authorization: Requires authenticated instructor owning the specified course (`OwnershipGuard.requireOwnerOrNotFound(ownerId, instructorId)`).
- Error Behavior:
  - Unauthenticated requests: HTTP 401 Unauthorized (`UNAUTHORIZED`).
  - Unauthorized requests (course owned by another instructor): HTTP 404 Not Found (`NOT_FOUND`) to prevent course ID enumeration and retain authorization indistinguishability.
  - Non-existent course: HTTP 404 Not Found (`NOT_FOUND`).
- Response Payload (`InstructorCourseAnalyticsDto`):
  ```json
  {
    "courseId": 10,
    "courseTitle": "Distributed Systems Architecture",
    "totalEnrollments": 25,
    "activeLearners": 20,
    "completionRate": 60.0,
    "averageAssessmentScore": 82.5,
    "averageRating": 4.8,
    "ratingCount": 15,
    "assignmentSubmissionRate": 75.0,
    "pendingSubmissions": 3,
    "lessonDropOff": [
      {
        "lessonId": 101,
        "lessonTitle": "Introduction to Raft",
        "moduleTitle": "Consensus Algorithms",
        "orderIndex": 1,
        "completedCount": 20,
        "completionPercent": 100.0,
        "dropOffRate": 0.0
      }
    ],
    "assignmentStats": [
      {
        "id": 1,
        "title": "Raft RPC Simulator",
        "status": "PUBLISHED",
        "dueAt": "2026-11-01T00:00:00Z",
        "maxPoints": 100.0,
        "submittedCount": 18,
        "gradedCount": 15,
        "submissionRate": 90.0,
        "averageScore": 85.0
      }
    ],
    "scoreDistribution": [
      { "bucket": "0-20%", "count": 0, "percentage": 0.0 },
      { "bucket": "21-40%", "count": 1, "percentage": 3.7 },
      { "bucket": "41-60%", "count": 4, "percentage": 14.8 },
      { "bucket": "61-80%", "count": 10, "percentage": 37.0 },
      { "bucket": "81-100%", "count": 12, "percentage": 44.5 }
    ]
  }
  ```

### 2. Metric Calculations & Denominators
1. `totalEnrollments`: Total count of records in `enrollments` for `course_id`.
2. `activeLearners`: Count of records in `enrollments` with `course_id = ? AND status = 'ACTIVE'`.
3. `completionRate`: Percentage of active learners with `course_progress.completed_at IS NOT NULL` (`completedLearners * 100.0 / activeLearners`). 0.0 if `activeLearners == 0`.
4. `averageAssessmentScore`: Average percentage across finalized `GRADED` submissions with `max_points > 0` (`AVG(s.total_points / a.max_points * 100)`). Null if no graded submissions exist.
5. `averageRating`: Average star rating across non-deleted reviews in `reviews` (`AVG(rating)`). Null if `ratingCount == 0`.
6. `lessonDropOff`: Sequential retention list for each module/lesson in the course structure. Tracks `completedCount`, `completionPercent` relative to `activeLearners`, and `dropOffRate` relative to the previous lesson.
7. `assignmentSubmissionRate`: Overall percentage of published assignment submissions submitted relative to total expected submissions (`totalSubmitted * 100.0 / (publishedAssignments.size() * activeLearners)`). 0.0 if `activeLearners == 0` or no published assignments.
8. `pendingSubmissions`: Count of submissions awaiting evaluation with `s.status = 'SUBMITTED'`.

### 3. Caching Contract
- Technology: Redis via `SafeCache`.
- Key Format: `analytics:course:{courseId}`.
- TTL: Short configurable duration (default: 300 seconds / 5 minutes).
- Invalidation: Transactional event listeners evict cache entries upon domain events:
  `LearnerEnrolledEvent`, `LearnerWithdrawnEvent`, `LessonCompletedEvent`, `SubmissionSubmittedEvent`, `SubmissionGradedEvent`, `AssignmentPublishedEvent`, `ReviewCreatedEvent`, `CourseContentChangedEvent`.
- Fault Tolerance: If Redis fails or connection is severed, `SafeCache` transparently falls back to executing the aggregation queries against MySQL without throwing an error.

## Phase 11: Learner Extras (Bookmarks & Notes)

### 1. Endpoints

#### Course & Lesson Bookmarks
- `POST /api/v1/courses/{courseId}/bookmarks`: Bookmark a course (Authenticated, Idempotent). Returns `200 OK` with `BookmarkDto`.
- `DELETE /api/v1/courses/{courseId}/bookmarks`: Remove course bookmark (Authenticated, Idempotent). Returns `204 No Content`.
- `GET /api/v1/courses/{courseId}/bookmarks/status`: Check course bookmark status (Authenticated). Returns `200 OK` with `BookmarkStatusDto`.
- `POST /api/v1/courses/{courseId}/lessons/{lessonId}/bookmarks`: Bookmark a lesson (Authenticated, Idempotent). Returns `200 OK` with `BookmarkDto`.
- `DELETE /api/v1/courses/{courseId}/lessons/{lessonId}/bookmarks`: Remove lesson bookmark (Authenticated, Idempotent). Returns `204 No Content`.
- `GET /api/v1/courses/{courseId}/lessons/{lessonId}/bookmarks/status`: Check lesson bookmark status (Authenticated). Returns `200 OK` with `BookmarkStatusDto`.
- `GET /api/v1/bookmarks[?type=COURSE|LESSON]`: List authenticated user's bookmarks (Authenticated). Returns `200 OK` with `List<BookmarkDto>`.
- `DELETE /api/v1/bookmarks/{bookmarkId}`: Delete bookmark by primary ID (Authenticated, user ownership verified). Returns `204 No Content`.

#### Private Lesson Notes
- `GET /api/v1/courses/{courseId}/lessons/{lessonId}/notes`: Retrieve authenticated user's private note for lesson (Authenticated, Enrollment required). Returns `200 OK` with `LessonNoteDto` or `204 No Content` if no note exists.
- `PUT /api/v1/courses/{courseId}/lessons/{lessonId}/notes`: Create or update note (Authenticated, Enrollment required). Request body: `UpsertNoteRequest` (`content` string up to 50,000 chars). Returns `200 OK` with `LessonNoteDto`.
- `POST /api/v1/courses/{courseId}/lessons/{lessonId}/notes`: Alias for note upsert (Authenticated, Enrollment required).
- `DELETE /api/v1/courses/{courseId}/lessons/{lessonId}/notes`: Delete user's note for lesson (Authenticated, Enrollment required). Returns `204 No Content`.

### 2. DTO Specifications

#### `BookmarkDto`
```json
{
  "id": 101,
  "userId": 12,
  "targetType": "COURSE",
  "courseId": 42,
  "courseTitle": "Distributed Systems in Go",
  "lessonId": null,
  "lessonTitle": null,
  "createdAt": "2026-10-21T02:00:00Z"
}
```

#### `BookmarkStatusDto`
```json
{
  "bookmarked": true
}
```

#### `LessonNoteDto`
```json
{
  "id": 55,
  "userId": 12,
  "courseId": 42,
  "lessonId": 204,
  "content": "Key takeaways on Raft log compaction and snapshotting.",
  "createdAt": "2026-10-21T02:10:00Z",
  "updatedAt": "2026-10-21T02:15:00Z"
}
```

### 3. Security, Privacy & Invariants
1. **Ownership & Derivation**: User identity is derived strictly from the authenticated security context (`CurrentUserProvider.requireCurrentUser()`). Client-supplied user IDs are never accepted.
2. **Strict Privacy**: A learner's notes are strictly confidential. Instructors, other learners, and anonymous users can never view, update, or delete another learner's notes. Queries always filter by `user_id = :authenticatedUserId`.
3. **Enrollment Enforcement**: Note endpoints require active enrollment in the course (`existsByUserIdAndCourseIdAndStatus(userId, courseId, ACTIVE)`). Unenrolled users receive `403 FORBIDDEN` with code `ENROLLMENT_REQUIRED`.
4. **Relational Integrity & Uniqueness**:
   - `bookmarks`: `(user_id, target_key)` has a unique constraint where `target_key` is a stored generated column (`C:<courseId>` or `L:<lessonId>`). Prevents duplicate bookmarks.
   - `lesson_notes`: `(user_id, lesson_id)` has a unique constraint enforcing one note per user per lesson.
5. **Phase Isolation**: Phase 11 is completely decoupled from discovery, enrollment, course playback, assignments, reviews, notifications, and analytics. If Phase 11 is removed or disabled, no other domain feature is affected.

---

## Phase 13: Email OTP Verification & Tokenized Password Reset

### 1. API Endpoints

#### Email Verification
- `POST /api/v1/auth/register`: Register user account (Public). Request: `{ email, password, displayName }`. Returns `201 Created` with `RegisterResponse` (`{ message, email, verificationRequired: true }`). Account is created with `email_verified = FALSE`.
- `POST /api/v1/auth/verify-email`: Verify email with 6-digit OTP (Public). Request: `VerifyEmailRequest` (`{ email, otp }`). Validates OTP, marks `email_verified = TRUE`, consumes OTP. Returns `200 OK` with `GenericMessageResponse`.
- `POST /api/v1/auth/resend-verification-otp`: Resend email verification code (Public). Request: `ResendVerificationRequest` (`{ email }`). Returns `200 OK` with `GenericMessageResponse`. Enforces a 60-second cooldown between resend requests; returns generic acknowledgment if email is unknown or already verified.
- `POST /api/v1/auth/login`: Authenticate user (Public). If credentials are valid but `email_verified == false`, throws `403 Forbidden` with error code `EMAIL_NOT_VERIFIED`.

#### Tokenized Password Reset
- `POST /api/v1/auth/forgot-password`: Request password reset OTP (Public). Request: `ForgotPasswordRequest` (`{ email }`). Returns `202 Accepted` with generic response. Enforces 60-second cooldown.
- `POST /api/v1/auth/verify-reset-otp`: Verify 6-digit password reset OTP and obtain reset token (Public). Request: `VerifyResetOtpRequest` (`{ email, otp }`). Validates OTP and returns `200 OK` with `VerifyResetOtpResponse` (`{ resetToken, message }`).
- `POST /api/v1/auth/reset-password`: Set new password (Public). Request: `ResetPasswordRequest` (`{ email, resetToken, newPassword }`, with fallback support for legacy `{ email, otp, newPassword }`). Validates single-use `resetToken`, updates password hash, revokes all active refresh tokens for the user, consumes authorization, and dispatches confirmation email. Returns `204 No Content`.

### 2. DTO Specifications

#### `RegisterResponse`
```json
{
  "message": "Verification code sent to your email",
  "email": "user@example.com",
  "verificationRequired": true
}
```

#### `VerifyResetOtpResponse`
```json
{
  "resetToken": "32-byte-base64url-token",
  "message": "Reset code verified successfully"
}
```

### 3. Security Invariants
1. **Zero Secret Leakage**: OTPs and reset tokens are never written to server logs, URLs, `localStorage`, or `sessionStorage`. They exist only in memory during the request lifecycle.
2. **Cryptographic Storage**: OTP values are hashed using BCrypt before persisting in `auth_otps`. Reset tokens are hashed using SHA-256 before storage.
3. **Brute-Force Protection & Burning**: Maximum 5 failed OTP verification attempts per code. Exceeding 5 attempts immediately burns (consumes) the OTP, requiring a new code request.
4. **Timing Attack Defense**: Constant-time dummy BCrypt matching is executed for non-existent users, already verified users, or missing OTP records.
5. **Session Revocation**: Password reset revokes all refresh token families for the user (`refreshTokens.revokeAllByUserId(user.getId())`), invalidating any active sessions across devices.
6. **Backward Compatibility**: `ResetPasswordRequest` supports both `resetToken` (recommended 2-step flow) and legacy `otp` (atomic reset flow). Existing users default to `email_verified = TRUE` via Flyway migration `V13_001__email_verification_and_otps.sql`.

---

## Phase 14: Administrative Monitoring Dashboard Contracts

### 1. Endpoint Contracts (`ROLE_ADMIN` Only)

- `GET /api/v1/admin/dashboard/summary`: Retrieve platform-wide aggregate counts.
  - Returns `200 OK` with `AdminDashboardSummaryDto`:
    ```json
    {
      "totalUsers": 120,
      "totalStudents": 95,
      "totalInstructors": 25,
      "totalCourses": 18,
      "publishedCourses": 12,
      "openReportsCount": 4,
      "totalActivitiesCount": 450
    }
    ```
- `GET /api/v1/admin/dashboard/activities?page=0&size=20`: Chronological audit log of platform actions sorted newest first.
  - Query parameters: `page` (int, default 0), `size` (int, default 20).
  - Returns `200 OK` with `PageResponse<AdminActivityDto>`:
    ```json
    {
      "content": [
        {
          "id": 1,
          "action": "COURSE_PUBLISHED",
          "actorId": 12,
          "actorName": "Dr. Smith",
          "actorRole": "USER",
          "entityType": "COURSE",
          "entityId": "101",
          "title": "Course Published",
          "description": "Dr. Smith published course \"Computer Systems\"",
          "details": { "title": "Computer Systems", "version": 2 },
          "createdAt": "2026-10-22T10:00:00Z"
        }
      ],
      "page": 0,
      "size": 20,
      "totalElements": 1,
      "totalPages": 1
    }
    ```
- `GET /api/v1/admin/reports?status=PENDING&query=systems&page=0&size=20`: List course issue reports with optional filters.
  - Query parameters: `status` (ReportStatus, optional), `query` (course title substring, optional), `page`, `size`.
  - Returns `200 OK` with `PageResponse<AdminReportDto>`.
- `PATCH /api/v1/admin/reports/{reportId}` & `PUT /api/v1/admin/reports/{reportId}/status`: Update status and notes for a course issue report.
  - Request body: `UpdateAdminReportRequest` (`{ status: "RESOLVED", resolutionNotes: "Issue investigated and fixed." }`).
  - Returns `200 OK` with updated `AdminReportDto`.
- `GET /api/v1/admin/users?role=USER&query=john&page=0&size=20`: Search and filter registered users.
  - Query parameters: `role` (UserRole, optional), `query` (name/email search, optional), `page`, `size`.
  - Returns `200 OK` with `PageResponse<AdminUserDto>`.
- `GET /api/v1/admin/courses?status=PUBLISHED&query=systems&page=0&size=20`: Browse platform courses with active enrollment totals.
  - Query parameters: `status` (CourseStatus, optional), `query` (course title search, optional), `page`, `size`.
  - Returns `200 OK` with `PageResponse<AdminCourseDto>`.






