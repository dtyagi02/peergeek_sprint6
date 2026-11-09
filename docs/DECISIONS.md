# Phase 0 decisions and pinned versions

Dependency versions are pinned rather than floating. Spring Boot-managed
dependencies use the pinned Boot BOM; AWS SDK and Testcontainers use their
explicit pinned BOMs.

## Runtime and infrastructure

| Component | Version |
| --- | --- |
| Java runtime / compiler target | 21 |
| Spring Boot | 4.1.1 |
| Maven wrapper distribution | 3.10.0 |
| Maven builder image | 3.9.11-eclipse-temurin-21-alpine |
| MySQL image | 8.4.5 |
| Redis image | 7.4.2-alpine |
| MinIO server | `RELEASE.2025-02-07T23-21-09Z` |
| MinIO client | `RELEASE.2025-02-08T19-14-21Z` |
| AWS SDK for Java v2 | 2.32.22 |
| springdoc OpenAPI | 3.0.0 |
| Testcontainers | 1.21.3 |
| Spring Security / Spring Mail | Spring Boot 4.1.1 managed |
| JJWT | 0.12.6 |
| Local SMTP catcher | `axllent/mailpit:v1.27.8` |
| Node.js image / CI | 24.11.1 |
| Nginx optional production image | 1.27.4-alpine |
| GitHub Actions | checkout v4.2.2, setup-java v4.7.1, setup-node v4.4.0 (commit-pinned in workflows) |

## Frontend

| Package | Version |
| --- | --- |
| React / React DOM | 19.3.1 |
| Vite | 8.3.4 |
| `@vitejs/plugin-react` | 6.1.2 |
| React Router DOM | 7.18.4 |
| TanStack Query | 5.104.3 |
| React Hook Form | 7.90.0 |
| Zod | 4.6.7 |
| Zustand | 5.0.15 |
| Radix Dialog | 1.3.1 |
| Radix Label | 2.1.17 |
| Radix Select | 2.4.1 |
| Radix Tabs | 1.2.1 |
| Radix Toast | 1.3.1 |
| Tailwind CSS / `@tailwindcss/vite` | 4.3.3 / 4.3.3 |
| ESLint | 10.12.0 |
| Prettier | 3.9.10 |
| Vitest | 5.0.5 |
| Testing Library React | 16.3.3 |
| Testing Library user-event | 14.6.8 |
| Testing Library jest-dom | 7.0.1 |
| jsdom | 30.1.3 |
| TipTap React / StarterKit / Link | 3.32.0 |
| `@dnd-kit/core` / sortable / utilities | 6.3.1 / 10.0.0 / 3.2.2 |

## Implementation decisions

- The Java application root is `com.peergeek`, not the starter template's
  narrower `com.peergeek.backend`, so all module packages share one boundary.
- Flyway's baseline migration contains a comment only. No business or domain
  table/entity is introduced by Phase 0; Phase 1 appends four additive tables.
- `BaseEntity` is an abstract mapped superclass and does not create a table.
- Redis failures are best-effort and `SafeCache` returns to its supplied
  source-of-truth loader. Redis health is visible but excluded from readiness.
- S3 uses the AWS SDK v2 synchronous `S3Client`, an optional endpoint override,
  region, and configurable path-style addressing. Credentials come from paired
  static environment variables when provided, otherwise from the AWS default
  credentials provider chain. Bucket creation is environment-configurable and
  rejected in production.
- Readiness requires the database and object-storage health indicators.
- Compose runs the Vite development server. The frontend Dockerfile also has
  optional `build` and `production` Nginx stages; build it with
  `docker build --target production -t peergeek-frontend:prod ./frontend`.
  These are not the Compose deployment path; `VITE_API_BASE_URL` is compiled
  into the static client at image-build time.
- Frontend feature registries use Vite `import.meta.glob` discovery, avoiding
  edits to the shell/registries as later features are added.
- Testcontainers tests are disabled when Docker is unavailable locally. CI
  uses Docker-enabled GitHub-hosted Linux runners.
- `.env.example` credentials are local placeholders only. Deployments must
  replace them with managed secrets.

## Phase 1 authentication and profile

- Passwords use Spring Security BCrypt with strength 12. Passwords are
  restricted to 8–72 characters and at most 72 UTF-8 bytes; a password cannot
  equal the normalized email.
- JWTs use HMAC-SHA-256 and a configured secret of at least 32 UTF-8 bytes. The
  development-only default is rejected by the `prod` profile.
- Access JWT lifetime is 900 seconds. Opaque refresh tokens live 14 days,
  rotate on every use, and are stored only as SHA-256 hashes. Reuse of a revoked
  token revokes all active tokens in its family.
- Refresh cookies use HttpOnly, `SameSite=Lax`, path `/api/v1/auth`, and a
  configurable `Secure` flag. Production startup requires it enabled.
- Six-digit reset codes are BCrypt-hashed, expire after 10 minutes, allow five
  attempts, and have a 60-second per-user resend cooldown. Known and unknown
  email requests return the same 202 response. Unknown/cooldown requests do
  dummy BCrypt work; email dispatch uses a bounded asynchronous executor so
  SMTP latency does not distinguish known accounts. Compose uses pinned Mailpit.
- Login failures return the same 401 error contract for unknown, disabled, and
  incorrect-password cases. Unknown emails receive a dummy BCrypt comparison.
  Security audit failures propagate and block success. Any future read-only
  audit use must be best-effort and must not block reads.
- `STUDENT` and `INSTRUCTOR` are persisted interface preferences; authorization
  uses only the independent `USER`/`ADMIN` role.

## Phase 2 course authoring

- Additive migrations `V2_001`–`V2_007` add only categories, courses, topics,
  modules, lessons, and text-only lesson content. Phase 2 does not add resource,
  enrollment, publish, discovery, or learner functionality.
- Course status storage includes `DRAFT`, `PUBLISHED`, and `UNPUBLISHED`, but
  Phase 2 creates drafts only. Details, structure, and text remain editable
  regardless of status. Soft deletion is limited to drafts.
- `thumbnail_resource_id` intentionally has no foreign key; Phase 3 owns the
  resource table and attachment behavior.
- Validation and safety caps bind from typed `peergeek.course` properties.
  Defaults: course title 3–120 characters and description 5,000; 10 topics of
  2–40 characters; module title 120, module description 1,000, 200 modules per
  course; lesson title 150, summary 500, 500 lessons per module; compact UTF-8
  JSON 2 MiB and nesting depth 20. These caps are technical safety limits, not
  instructional requirements. Compose and `.env.example` expose each cap.
- Compose configures `max_allowed_packet=16M`, above the lesson JSON cap.
  Production MySQL must also set `max_allowed_packet` above that cap.
- Lesson content is MySQL `JSON`, validated server-side against
  `tiptap-json-v1`. Word count is computed from text nodes; Unicode letters and
  numbers are word characters and internal apostrophes or hyphens connect word
  segments. Empty text has word count zero.
- Every structure/content write updates `content_updated_at` and emits one
  `CourseContentChangedEvent` after commit. Phase 2 installs no event listener.
- Reordering uses parent-row locks and exact-set checks to maintain
  zero-based, gap-free positions. Course and lesson content saves reject stale
  versions with `409 CONFLICT`.
- `CourseStructureQueryService` is the frozen downstream snapshot extension
  point. It intentionally performs no authorization; callers must verify
  ownership/enrollment. Later phases must not import module or lesson
  repositories/entities.
- The TipTap lesson editor is lazy-loaded separately from the application
  shell to keep editor dependencies off the initial route bundle.

## Phase 3 files and resources

- Resource metadata and expected-resource declarations are stored in additive
  `V3_001`–`V3_003` migrations. Soft deletion hides resources immediately;
  scheduled reconciliation expires abandoned uploads, retries stuck processing,
  and removes old deleted objects.
- Uploaded file bytes live in S3-compatible object storage. The backend issues
  short-lived presigned URLs; the browser uploads directly, then the backend
  checks object size, streams content validation, and computes SHA-256 before
  exposing a resource as ready.
- Lesson resource and thumbnail access uses a deny-precedence voter chain with
  default deny. The current owner voter uses the course metadata seam; UI mode
  is never an authorization grant.
- Resource processing runs on a bounded executor. Content checks are
  streaming/bounded, including UTF-8 validation and OOXML expanded-size limits.
  Retry count, worker concurrency, URL lifetime, upload limits, and cleanup
  timing are typed environment-bound settings.
- The same stored object has separate public and internal presigners. Browser
  URLs use `STORAGE_PUBLIC_ENDPOINT`; Phase 4 consumers use the internal
  endpoint so Compose service DNS remains reachable from the backend.
- The instructor resources feature is discovered through the existing
  `lesson-editor.side-panel` and `course-details.thumbnail` slot registries.

## Phase 4 (partial implementation)

- Readiness and preview consumers use immutable course metadata and status
  snapshots from the course-owned query/accessor seams; they do not query
  course persistence internals directly.
- Course status transitions are conditional updates for caller-approved
  source statuses, exclude soft-deleted courses, and increment the course
  version. `published_at` changes only on a transition to `PUBLISHED`.
- The shared learner lesson renderer maps the supported ProseMirror nodes and
  marks to React elements, neutralizes non-http/https/mailto links, and never
  injects HTML.
- Video probing consumes an `InternalResourceUrlProvider` URL with
  `ProcessBuilder` argument separation, a 30-second timeout, bounded stdout and
  stderr, a protocol allowlist, and defensive JSON parsing. `VIDEO_INVALID`
  and `VIDEO_PROBE_UNAVAILABLE` are separate readiness issue codes. The
  provider now resolves ready, non-deleted resource rows and signs through the
  internal S3 presigner.
- Completed valid and invalid video probe results are cached against resource
  ID plus SHA-256 in `resource_probe_results`; unavailable probes are not
  cached so a later healthy runtime can retry.
- The backend runtime image installs ffmpeg. GitHub Actions installs ffmpeg,
  fails if `ffprobe` is absent, and checks Docker availability before running
  the Testcontainers suite.

### Phase 4 Definition of Done status (2026-10-19)

| Item | Status | Evidence or remaining work |
| --- | --- | --- |
| Course metadata/status read and conditional transition seams | VERIFIED | Unit and Docker-backed MySQL integration tests pass in local `verify`. |
| V4 readiness, probe-cache, link-check, and publish-lock schema | VERIFIED | All migrations apply in the Docker-backed MySQL integration suite. |
| `VIDEO_INVALID` and `VIDEO_PROBE_UNAVAILABLE` classification and rule mapping | VERIFIED | Unit tests pass; ffprobe tests ran locally without skips. |
| Bounded ffprobe process, defensive JSON parser, and internal-presigned-URL seam | VERIFIED | ffprobe unit tests pass and the Phase 3 internal URL provider is implemented. |
| `resource_probe_results` read/write caching | VERIFIED | The Testcontainers MySQL cache integration test passes locally. |
| Docker backend image contains ffprobe; CI requires ffprobe and Docker | VERIFIED LOCALLY | Local ffprobe and Docker-required Maven verification pass; hosted CI has not run in this workspace. |
| MySQL, Redis, and MinIO Testcontainers are shared across integration classes | VERIFIED | The full local Docker-required suite passes with zero skipped tests. |
| Full readiness engine/API, deterministic fingerprint, and publish-time freshness/readiness gate | VERIFIED | Docker-backed integration tests run an asynchronous readiness check, publish a ready course, and reject a stale report with HTTP 409. |
| Resource attachment, ownership authorization, and actual internal S3 presigning adapter | VERIFIED | Phase 3 provides resource rows, owner voter, and internal presigned GET URLs. |
| SSRF-safe link checker with manual redirect handling and address pinning | VERIFIED | Unit and local mock server integration tests verify redirect blocking to private IPs, redirect limits, and single pre-connection DNS lookup. Production startup rejection verified. |
| Publishing endpoint, status lock, audit, and status accessor integration | VERIFIED | Docker-backed integration tests cover publishing, audit, preview access, and stale-report rejection. |
| After-commit publish/unpublish event delivery | VERIFIED | Docker-backed integration test asserts CoursePublishedEvent and CourseUnpublishedEvent are delivered once after commit, and never on rollback or rejected transitions. |
| Resource-aware instructor preview assembler/API/page | VERIFIED | The Docker-backed preview API test and frontend instructor-publish tests/build pass. |
| Shared React lesson renderer and course-editor extension slot | VERIFIED | Focused Vitest renderer/slot tests passed locally. |

The full Docker-required backend verification ran 85 tests with zero failures,
errors, or skips. Frontend validation passed 43 tests across 11 files, plus
lint, formatting, and production build checks. Hosted CI has not yet been
observed in this workspace.

## Phase 5: Discovery & Public Preview

- **Discovery Read Model**: `discovery` module owns a denormalized table `public_course_index` (one row per currently `PUBLISHED` course). Search, listing, category counts, and popularity queries run exclusively against this table and never query authoring tables directly. The index is kept in sync by `AFTER_COMMIT` event listeners (`CoursePublishedEvent`, `CourseUnpublishedEvent`, `CourseContentChangedEvent`, `ResourceChangedEvent`) plus a scheduled reconciliation worker (`discovery.reconcile-interval`, default 5m). Staleness windows: unpublish/publish are immediate; content/resource edits flush within `<= discovery.dirty-flush` (default 5s); external metadata/profile edits or missed events resolve within `<= 5m`.
- **Stats Extension Point (`CourseStatsRecorder`)**: Deviation from the earlier pull-based "CourseStatsContributor" concept. Popularity and rating sorting must happen directly in MySQL queries, which pull interfaces cannot do efficiently. Discovery owns `course_public_stats` and exposes `CourseStatsRecorder`, a push interface accepting absolute, idempotent recomputed values (`recordEnrollmentStats` for Phase 6, `recordRatingStats` for Phase 8). Until recorded, stats remain `NULL` and DTO fields are nullable.
- **Search Collation and Strategy**: Free-text search performs substring matching using MySQL collation `utf8mb4_0900_ai_ci` on an indexed `search_text` column containing title, topics, category, instructor name, and description. MySQL `FULLTEXT` was rejected due to minimum token size and stopword rules dropping short terms (e.g. "C", "AI", "Go") and common words. The search query layer is abstracted behind `CourseSearchRepository` to allow future migration to external search engines (OpenSearch/Elasticsearch) if scale exceeds MySQL substring capacity.
- **Public Thumbnails**: DTOs contain a stable API path (`/api/v1/public/courses/{id}/thumbnail?v={thumbnailResourceId}`) and never include expiring presigned URLs, keeping Redis caches free of expiring URLs. The thumbnail endpoint is the single sanctioned public caller of `ResourceDownloadService` and performs its own authorization: course is published, thumbnail resource belongs to that course, purpose is `COURSE_THUMBNAIL`, and status is `READY`. Returns `302 Found` with `Cache-Control: public, max-age=60` to a fresh short-lived presigned GET with inline content-disposition.
- **Public Categories**: Phase 2's `GET /api/v1/categories` remains untouched. Discovery provides `GET /api/v1/public/categories`, returning active categories with the count of currently published courses derived from `public_course_index`.
- **Caller Invariance**: Public endpoints allow anonymous access and ignore bearer tokens if present. Responses are identical for every caller with `Vary: Accept-Encoding`.
- **Disposable Cache and Stale Guard**: Redis caching uses `SafeCache` with schema-versioned envelopes (`{schemaVersion, payload}`). Concurrent misses trigger single-flight DB loading. The public preview endpoint always runs a cheap `isPublished(courseId)` DB verification before serving a warm cache hit, guaranteeing an unpublished course is never served from cache. If Redis is unavailable or disabled, the application continues to operate without degradation.

### Phase 5 Definition of Done status (2026-10-20)

| Item | Status | Evidence or remaining work |
| --- | --- | --- |
| In docker compose, logged OUT: browse Explore, search, filter, sort, paginate, preview with thumbnail, outline, instructor; unpublish immediately hides course | VERIFIED | Docker integration tests and frontend Vitest suites test full anonymous flow and immediate hiding upon unpublish. |
| Draft/unpublished/deleted/nonexistent courses are indistinguishable from "not found" (404) | VERIFIED | `DiscoveryPublicIndistinguishabilityIntegrationTest` verifies byte-identical 404 responses across preview and thumbnail endpoints, and absence from warm cache. |
| Public leak guard passes (exact allowlisted key sets; no lesson text, internal IDs below course, resource data, or signed URLs) | VERIFIED | `DiscoveryLeakGuardIntegrationTest` asserts exact JSON keys of `CourseCardDto` and `CoursePreviewDto`, verifying zero internal IDs, resource keys, or signed URLs. |
| Redis stopped: all public endpoints work without 5xx | VERIFIED | `DiscoveryCacheIntegrationTest.verifyRedisDownGracefulFallback()` verifies public endpoints with Redis disabled. |
| Public endpoint list contains exactly the 5 explicit GET patterns (no wildcards) | VERIFIED | `PublicEndpoints` contains only explicit GET routes; `DiscoverySecurityIntegrationTest` asserts non-allowlisted routes require auth or return 404. |
| Search is injection-safe and deterministic; pagination has no duplicates or gaps; deep offset capped | VERIFIED | `DiscoverySearchAndSortIntegrationTest` verifies escaping of `%`, `_`, `\`, quotes, SQL metacharacters, deterministic tiebreakers, and 400 on `page * size > 1000`. |
| `CourseStatsRecorder` documented and tested; stats nullable when unrecorded | VERIFIED | `DiscoveryStatsIntegrationTest` verifies range validation, idempotent upsert, cache invalidation, and nullable stats. |
| Index consistency via events and reconciliation worker | VERIFIED | `DiscoveryIndexSyncIntegrationTest` and `DiscoveryReconciliationIntegrationTest` verify event synchronization, coalesced dirty flush, and recovery of missed events. |
| No later-phase code created (no enrollment, ratings submission, recommendations, notifications) | VERIFIED | Codebase contains only Phase 5 discovery read models and push extension point. |
| Earlier-phase touches strictly limited to HARD RULES item 2 | VERIFIED | Only additive read-only seams added (`CourseStructureQueryService`, `UserService`, `PublicEndpoints`, frontend slots). |
| All Phase 0–5 tests pass locally and CI-ready | VERIFIED | 126/126 backend integration and unit tests pass with zero skips. 60/60 frontend Vitest tests pass with 0 lint errors. |
| Documentation updated | VERIFIED | `docs/DISCOVERY.md`, `docs/CONTRACTS.md`, `docs/DECISIONS.md`, and `README.md` updated. |

## Phase 6: Enrollment & Learner Experience

### Key Architecture & Implementation Decisions
- **Free and Instant Enrollment**: Enrollment is free and instant. Only `PUBLISHED` and non-deleted courses accept new enrollments. Owners cannot enroll in their own courses (`409 Conflict`, `CANNOT_ENROLL_OWN_COURSE`); owners use Phase 4 instructor preview.
- **Learners Keep Access on Unpublish (Assumption A4)**: Once enrolled in a published course, learners keep access even if the instructor unpublishes the course. Access terminates only if the course is soft-deleted or the learner withdraws.
- **Soft Withdrawal and Progress Retention**: Withdrawing changes status from `ACTIVE` to `WITHDRAWN` and timestamps `withdrawn_at`. Re-enrolling reactivates the existing row and preserves all completed lesson records and progress history.
- **Visibility & Indistinguishability**: If a course is not visible to the caller (nonexistent, soft-deleted, or draft/unpublished and caller is not an active learner), endpoints return `404 Not Found`. If a course is `PUBLISHED` and the caller is not actively enrolled, content endpoints return `403 Forbidden` (`code: ENROLLMENT_REQUIRED`). Anonymous requests return `401 Unauthorized`.
- **Progress Calculation**: Course progress is calculated strictly as completed lessons that *currently exist* in the course divided by the total number of lessons that *currently exist* in the course: $\lfloor \frac{\text{completed} \times 100}{\text{total}} \rfloor$. Completed rows for lessons that have been deleted by the instructor are ignored. Courses with 0 lessons have 0% progress and are never completed.
- **"Ever Completed" Invariant**: `completed_at` is set the first time course progress reaches 100% and is retained even if the instructor subsequently adds lessons (causing percent to drop below 100%). Discovery completion counts reflect active enrollments where `completed_at IS NOT NULL`.
- **Staleness Listener & Lazy Recompute**: Course structure edits (`CourseContentChangedEvent`) trigger an `AFTER_COMMIT` listener that executes a single SQL update marking all `course_progress` rows of that course `stale = true`. Progress is lazily recomputed upon the next learner read or write, avoiding expensive full recomputation of thousands of learners on authoring changes.
- **Continue Learning 4-Step Resolution Rule**:
  1. `RESUME`: If `last_lesson_id` exists, still exists in the course, and is not completed -> return that lesson.
  2. `NEXT`: The first incomplete lesson strictly *after* the last visited lesson in course order (across modules).
  3. `FIRST`: The first incomplete lesson from the beginning of the course.
  4. `COMPLETED`: All lessons complete (or 0 lessons) -> `lessonId = null`, reason `COMPLETED`.
- **EnrolledLearnerVoter Request-Scoped Memoization**: `EnrolledLearnerVoter` grants `VIEW` and `DOWNLOAD` only (never `MANAGE`) when the resource purpose is `LESSON_RESOURCE`, status is `READY`, course is not deleted, and the user has an `ACTIVE` enrollment. It abstains otherwise (never denies). To prevent $N+1$ enrollment checks when rendering a lesson with 50 resources, checks are memoized per request.
- **Push-Based Stats Refresh**: `StatsRefresher` listens to Phase 6 domain events (`LearnerEnrolledEvent`, `LearnerWithdrawnEvent`, `LessonCompletedEvent`) `AFTER_COMMIT` and pushes absolute counts calculated from MySQL to `CourseStatsRecorder.recordEnrollmentStats`. Failures are caught and logged without failing learner requests.
- **No Redis Caching for Learner Data**: Learner progress and content is user-private and must not risk cross-user cache leaks. All responses carry `Cache-Control: no-store`.

### Phase 6 Definition of Done Status (2026-10-20)

| Item | Status | Evidence or remaining work |
| --- | --- | --- |
| In docker compose with a second account: enroll in a published course, open player, view text/PDF/image/video, mark lessons complete, see progress change, continue learning, withdraw and re-enroll with progress kept, course keeps working after unpublish | VERIFIED | Covered by end-to-end integration tests (`EnrollmentLifecycleIntegrationTest`, `LearnerAccessMatrixIntegrationTest`, `ProgressAndContinueIntegrationTest`, `LearnerResourceAccessIntegrationTest`) and frontend Vitest suite. |
| Non-enrolled user gets 403 (or 404 when course is not visible) on lesson content and resource URLs; one learner never sees another's progress | VERIFIED | `LearnerAccessMatrixIntegrationTest` tests full access matrix across all learner endpoints; `LearnerContentSecurityIntegrationTest` asserts isolation between learners. |
| Progress math and Continue Learning rules tested for structure changes after enrollment | VERIFIED | `ProgressAndContinueIntegrationTest` verifies floor math, 0 lessons, lesson addition after completion, deleted lesson filtering, stale flag propagation, and RESUME/NEXT/FIRST/COMPLETED transitions. |
| EnrolledLearnerVoter is a new class only; no Phase 3 class edited; voter chain stays default-deny | VERIFIED | `EnrolledLearnerVoterTest` and `LearnerResourceAccessIntegrationTest` verify owner grant, default-deny, thumbnail exclusion, MANAGE refusal, and memoization. Zero files in `com.peergeek.file` touched. |
| Stats reach CourseStatsRecorder as absolute values and discovery reflects them; recorder failure never breaks learner actions | VERIFIED | `StatsRefresherIntegrationTest` asserts absolute counts reach recorder and discovery popular sort/cards update; verifies error resilience when recorder fails. |
| Events fire once after commit; docs list hooks for Phases 7 to 11 | VERIFIED | Integration tests assert `LearnerEnrolledEvent`, `LearnerWithdrawnEvent`, and `LessonCompletedEvent` fire exactly once after commit, never on rollback or no-op; documented in `docs/LEARNING.md`. |
| No assignment, review, report, notification, analytics, bookmark/note or caching code exists | VERIFIED | Codebase inspection confirms zero out-of-scope tables, endpoints, or services created. |
| Earlier-phase files touched are ONLY those permitted in HARD RULES 2 | VERIFIED | Only `CourseStructureQueryService` (additive bulk metadata seam) and frontend slot registry (`player.lesson-actions`) touched. |
| All Phase 0 to 6 tests pass locally and in CI with 0 skipped | VERIFIED | Local `mvnw verify` runs 168 tests across Phases 0–6 with 0 skipped and 0 failures. Frontend runs 78 Vitest tests across 13 test files with 0 failures and 0 lint warnings. |
| Docs updated | VERIFIED | `docs/LEARNING.md`, `docs/CONTRACTS.md`, `docs/DECISIONS.md`, and `README.md` updated. |

## Phase 7: Assignments & Assessments

### Key Architecture & Implementation Decisions
- **Assignment Kinds**: Two distinct assignment workflows:
  - `PROJECT`: Practical work requiring instructions, text response, and/or file uploads up to 25 MB. Graded manually with overall marks and feedback.
  - `QUESTIONS`: Structured question set. Can be timed (1 to 480 minutes) or untimed. Max points is automatically recomputed server-side as the sum of question points.
- **Question Types & Execution Policy**:
  - `MCQ_SINGLE` (single choice, auto-graded) and `MCQ_MULTI` (multi choice, auto-graded all-or-nothing).
  - `DESCRIPTIVE` (manually graded, up to 20,000 chars) and `CODE` (manually graded, up to 50,000 chars with optional language label).
  - Code execution is strictly out of scope: code answers are stored as plain text and rendered in a monospace `<pre className="font-mono">` without execution.
- **Single Attempt Invariant**: Exactly one attempt per learner per assignment enforced at the database level by `UNIQUE uk_submissions_assignment_user (assignment_id, user_id)`. Start attempt is idempotent and returns the existing attempt under concurrency. No retakes in v1.
- **Course & Outline Placement**: Assignments attach to a course and may optionally specify an informational `moduleId` or `lessonId`. No foreign keys are created to modules or lessons to prevent cascade/delete complications. Structure existence is validated at write time and resolved dynamically at read time; if the target module/lesson is deleted, the assignment resolves cleanly to course-level.
- **Strict Locking**: As soon as `attempt_count > 0`, questions, options, points, and kind are permanently locked (`409 ASSIGNMENT_LOCKED`). Instructors can update metadata (title, instructions, dates, late policy, show correct answers) or unpublish the assignment, but cannot delete it if attempts exist.
- **Authoritative Timing & Grace Window**: The server clock (`java.time.Clock` bean) is the sole authority; client timestamps are ignored. `deadline_at` is computed at start and capped at `due_at` when `allow_late = false`. A configurable server grace period (`peergeek.assignment.timer.grace-seconds`, default 10s) accommodates network latency during draft saves and submissions.
- **Dual Expiry Finalization**: IN_PROGRESS attempts past deadline + grace are automatically finalized with `auto_submitted = true` using their latest saved draft. Finalization occurs lazily upon any attempt read or write, and proactively via a scheduled batched background job running every minute (`peergeek.assignment.expiry.cron`).
- **Auto-Grading & Scoring**: Submissions consisting entirely of auto-gradable questions transition immediately to `GRADED` with `auto_graded = true` and `graded_by = NULL`. Partial manual grading is rejected (`400 GRADING_INCOMPLETE`). Effective question points default to auto-points unless explicitly overridden by the instructor. Total points are always calculated server-side.
- **Learner Leak Protection**: Assignment details before starting contain no questions. Correct answers and options flags are never sent to learners unless `submission.status == 'GRADED'` AND `assignment.show_correct_answers == true`. Grader IDs, internal object storage keys, bucket names, and grader emails are excluded from learner DTOs. Responses carry `Cache-Control: no-store` and web storage is never used for answers.
- **Submission Files Pipeline**: Submission files use prefix `submissions/{assignmentId}/{submissionId}/{UUID}` and are completely decoupled from Phase 3 course resources. Direct upload uses presigned PUT, followed by a confirm endpoint performing HEAD size verification, streaming SHA-256 calculation, and magic-byte sniffing. Downloads are strictly presigned GETs with attachment Content-Disposition, authorized only for the learner or course owner. Abandoned PENDING and FAILED uploads are purged after 24h.
- **ReadinessRule Limitation**: Phase 4's readiness engine dynamically picks up `AssignmentDraftExistsReadinessRule` (`ASSIGNMENT_DRAFT_EXISTS` [WARNING, FAST]). Because Phase 4 does not expose a fingerprint-contributor hook, the course readiness report fingerprint does not include assignment states; this limitation is documented as acceptable for non-blocking warnings.

### Phase 7 Definition of Done Status (2026-10-20)

| Item | Status | Evidence or remaining work |
| --- | --- | --- |
| In docker compose with two accounts: instructor creates and publishes a project assignment and a timed MCQ+descriptive assessment; the learner sees them, uploads a file, autosaves, refreshes and keeps work, submits; timed one auto-submits on expiry; MCQ-only auto-grades; instructor grades rest with feedback; learner sees marks and feedback | VERIFIED | Covered by end-to-end backend integration tests (`AssignmentLifecycleIntegrationTest`, `LearnerAttemptAndDraftIntegrationTest`, `LearnerSubmitAndScoringIntegrationTest`, `SubmissionFilesIntegrationTest`, `InstructorGradingAndQueueIntegrationTest`) and frontend Vitest suite. |
| Timing is server-authoritative: changing browser clock or replaying requests after deadline cannot extend an attempt | VERIFIED | `LearnerTimingAndExpiryIntegrationTest` verifies server-side deadline calculation, cap at due_at, grace window acceptance, rejection with ATTEMPT_EXPIRED past grace, and client time bypass immunity. |
| Correct answers never reach learners except in the single allowed case; exact key sets asserted | VERIFIED | `AssignmentLeakGuardIntegrationTest` asserts exact JSON key sets of learner DTOs; verifies correct flags are omitted before start, in attempts, drafts, and submissions unless GRADED with show_correct_answers = true. |
| Only enrolled learners submit; only owning instructor reviews and grades; access matrix passes for every endpoint | VERIFIED | `AssignmentAccessMatrixIntegrationTest` verifies full access matrix across all 12 Phase 7 endpoints for anonymous, non-enrolled, enrolled, withdrawn, and non-owner callers. |
| Autosave survives refresh and detects two-tab conflicts; double submit produces one submission and one event | VERIFIED | `LearnerAttemptAndDraftIntegrationTest` and `LearnerSubmitAndScoringIntegrationTest` verify optimistic locking, draft persistence, and idempotent submit transitions. |
| Submission files are validated, stored in object storage by presigned PUT, downloaded only as attachments by authorized users; abandoned uploads cleaned up | VERIFIED | `SubmissionFilesIntegrationTest` verifies presigned PUT, HEAD size verification, magic-byte sniffing, tampered file detection, attachment disposition, and abandoned cleanup worker. |
| Events and read services are documented and tested; ReadinessRule never blocks publishing | VERIFIED | `AssignmentEventAndAuditIntegrationTest` verifies after-commit domain event delivery; `AssignmentReadinessRuleAndQueryServiceTest` verifies query service aggregations and that the readiness rule is non-blocking WARNING. |
| No notification, analytics, review, report, retake, code-execution or AI-grading code exists | VERIFIED | Codebase inspection confirms zero out-of-scope features; CODE answers stored and displayed as plain text without execution. |
| Earlier-phase files touched are ONLY those permitted in HARD RULES 2 | VERIFIED | Only `EnrollmentQueryService` (additive `listActiveCourseIds`), `file` package helpers (stateless visibility widened to public), frontend slot registry, and documentation files touched. |
| All Phase 0 to 7 tests pass locally and in CI with 0 skipped | VERIFIED | Full backend test suite runs 187 tests with 0 failures, 0 errors, 0 skipped (`-Dpeergeek.require-docker=true`). Frontend test suite runs 92 tests across 14 test files with 0 failures and 0 lint warnings. |
| Docs updated | VERIFIED | `docs/ASSIGNMENTS.md`, `docs/CONTRACTS.md`, `docs/DECISIONS.md`, `.env.example`, and `README.md` updated. |



