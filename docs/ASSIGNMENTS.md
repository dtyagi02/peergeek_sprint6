# PeerGeek Phase 7: Assignments & Assessments

Phase 7 implements course assignments and assessments for PeerGeek. Instructors create practical/project assignments and question-based timed/untimed assessments. Enrolled learners start attempts, autosave responses, upload project files, and submit. The server is the sole timing and scoring authority. Instructors review submissions, evaluate questions, and award marks/feedback.

---

## 1. Data Model

Phase 7 introduces migrations `V7_001` through `V7_005`:

### `assignments` (`V7_001__create_assignments.sql`)
- `id`: BIGINT AUTO_INCREMENT PRIMARY KEY
- `course_id`: BIGINT NOT NULL, FK to `courses(id)` ON DELETE RESTRICT
- `module_id`: BIGINT NULL (Informational placement, NO foreign key)
- `lesson_id`: BIGINT NULL (Informational placement, NO foreign key)
- `kind`: ENUM('PROJECT', 'QUESTIONS') NOT NULL
- `title`: VARCHAR(255) NOT NULL
- `instructions`: TEXT NULL
- `status`: ENUM('DRAFT', 'PUBLISHED', 'UNPUBLISHED') NOT NULL DEFAULT 'DRAFT'
- `max_points`: DECIMAL(7, 2) NOT NULL
- `opens_at`: DATETIME(6) NULL (UTC)
- `due_at`: DATETIME(6) NULL (UTC)
- `time_limit_minutes`: INT NULL (1 to 480)
- `allow_late`: BOOLEAN NOT NULL DEFAULT TRUE
- `allow_text`: BOOLEAN NOT NULL DEFAULT TRUE
- `allow_files`: BOOLEAN NOT NULL DEFAULT TRUE
- `show_correct_answers`: BOOLEAN NOT NULL DEFAULT FALSE
- `attempt_count`: INT NOT NULL DEFAULT 0 (Denormalized lock counter)
- `published_at`: DATETIME(6) NULL (UTC)
- `version`: BIGINT NOT NULL DEFAULT 0 (Optimistic locking)
- `created_by`: BIGINT NOT NULL, FK to `users(id)`
- `created_at`: DATETIME(6) NOT NULL (UTC)
- `updated_at`: DATETIME(6) NOT NULL (UTC)
- **Constraints & Indexes**:
  - `CHECK (NOT (module_id IS NOT NULL AND lesson_id IS NOT NULL))`
  - Index `idx_assignments_course_status (course_id, status)`
  - Index `idx_assignments_due_at (due_at)`
  - Index `idx_assignments_lesson_id (lesson_id)`
  - Index `idx_assignments_module_id (module_id)`

### `assignment_questions` (`V7_002__create_assignment_questions.sql`)
- `id`: BIGINT AUTO_INCREMENT PRIMARY KEY
- `assignment_id`: BIGINT NOT NULL, FK to `assignments(id)` ON DELETE CASCADE
- `position`: INT NOT NULL
- `type`: ENUM('MCQ_SINGLE', 'MCQ_MULTI', 'DESCRIPTIVE', 'CODE') NOT NULL
- `prompt`: TEXT NOT NULL
- `points`: DECIMAL(7, 2) NOT NULL
- `options`: JSON NULL (Array of `{ "id": "...", "text": "...", "correct": boolean }`)
- `code_language`: VARCHAR(30) NULL
- **Constraints & Indexes**:
  - Index `idx_assignment_questions_position (assignment_id, position)`

### `submissions` (`V7_003__create_submissions.sql`)
- `id`: BIGINT AUTO_INCREMENT PRIMARY KEY
- `assignment_id`: BIGINT NOT NULL, FK to `assignments(id)` ON DELETE RESTRICT
- `user_id`: BIGINT NOT NULL, FK to `users(id)` ON DELETE RESTRICT
- `status`: ENUM('IN_PROGRESS', 'SUBMITTED', 'GRADED') NOT NULL DEFAULT 'IN_PROGRESS'
- `started_at`: DATETIME(6) NOT NULL (UTC)
- `deadline_at`: DATETIME(6) NULL (UTC)
- `submitted_at`: DATETIME(6) NULL (UTC)
- `late`: BOOLEAN NOT NULL DEFAULT FALSE
- `auto_submitted`: BOOLEAN NOT NULL DEFAULT FALSE
- `auto_graded`: BOOLEAN NOT NULL DEFAULT FALSE
- `text_answer`: TEXT NULL (For PROJECT assignments)
- `marks`: DECIMAL(7, 2) NULL (For PROJECT manual grade)
- `total_points`: DECIMAL(7, 2) NULL (Computed server-side total)
- `overall_feedback`: TEXT NULL
- `graded_by`: BIGINT NULL, FK to `users(id)`
- `graded_at`: DATETIME(6) NULL (UTC)
- `version`: BIGINT NOT NULL DEFAULT 0
- `created_at`: DATETIME(6) NOT NULL (UTC)
- `updated_at`: DATETIME(6) NOT NULL (UTC)
- **Constraints & Indexes**:
  - UNIQUE `uk_submissions_assignment_user (assignment_id, user_id)` (Strictly 1 attempt per learner)
  - Index `idx_submissions_queue (assignment_id, status, submitted_at)`
  - Index `idx_submissions_user_status (user_id, status)`
  - Index `idx_submissions_expiry (status, deadline_at)`

### `submission_answers` (`V7_004__create_submission_answers.sql`)
- `id`: BIGINT AUTO_INCREMENT PRIMARY KEY
- `submission_id`: BIGINT NOT NULL, FK to `submissions(id)` ON DELETE CASCADE
- `question_id`: BIGINT NOT NULL (No FK to questions to maintain decouple integrity)
- `answer_text`: TEXT NULL
- `selected_option_ids`: JSON NULL (Array of selected string IDs)
- `auto_points`: DECIMAL(7, 2) NULL
- `points`: DECIMAL(7, 2) NULL (Manual awarded points)
- `feedback`: TEXT NULL
- `updated_at`: DATETIME(6) NOT NULL (UTC)
- **Constraints & Indexes**:
  - UNIQUE `uk_submission_answers_submission_question (submission_id, question_id)`

### `submission_files` (`V7_005__create_submission_files.sql`)
- `id`: BIGINT AUTO_INCREMENT PRIMARY KEY
- `submission_id`: BIGINT NOT NULL, FK to `submissions(id)` ON DELETE CASCADE
- `uploaded_by`: BIGINT NOT NULL, FK to `users(id)`
- `original_filename`: VARCHAR(255) NOT NULL
- `object_key`: VARCHAR(512) NOT NULL UNIQUE
- `mime_type`: VARCHAR(120) NOT NULL
- `size_bytes`: BIGINT NOT NULL
- `checksum_sha256`: VARCHAR(64) NULL
- `status`: ENUM('PENDING', 'READY', 'FAILED') NOT NULL DEFAULT 'PENDING'
- `failure_reason`: VARCHAR(255) NULL
- `created_at`: DATETIME(6) NOT NULL (UTC)
- `updated_at`: DATETIME(6) NOT NULL (UTC)
- **Constraints & Indexes**:
  - Index `idx_submission_files_submission (submission_id)`
  - Index `idx_submission_files_status_created (status, created_at)`

---

## 2. Assignment Lifecycle & Locking

```
[DRAFT]  <------------------->  [PUBLISHED]  <------------------->  [UNPUBLISHED]
   | (validate & publish)            | (learners can view/start)            |
   |                                 |                                      |
   v                                 v                                      v
(Cannot start)               (Active learners start)              (Hidden from learners;
                                                                   existing submissions gradeable)
```

### Publish Validation Rules (HTTP 400 with fieldErrors)
- `title`: Required, 1 to 255 chars.
- `instructions`: Plain text only, max 10,000 chars.
- `QUESTIONS` kind:
  - 1 to 100 questions.
  - Every MCQ must have 2 to 10 options and at least one marked correct.
  - `MCQ_SINGLE` must have exactly one correct option.
  - Every question must have `points > 0`.
  - `max_points` is automatically recomputed by the server as the sum of question points.
- `PROJECT` kind:
  - Must have `allow_text = true` OR `allow_files = true`.
  - `max_points > 0` specified by instructor.
- Timing: `opens_at < due_at` when both are present.
- Course status: Cannot publish assignment if parent course is deleted (returns 404).

### Locking Rules (HTTP 409 `ASSIGNMENT_LOCKED`)
- As soon as **any attempt** has been started (`attempt_count > 0`):
  - **LOCKED**: `kind`, question list, question types, prompts, points, and MCQ options cannot be modified or replaced.
  - **EDITABLE**: `title`, `instructions`, `opens_at`, `due_at`, `allow_late`, and `show_correct_answers`.
  - **DELETE**: Assignments with existing attempts cannot be deleted (returns 409). Instructors must use unpublish instead.

---

## 3. Visibility & Access Matrix (404/403/401 Rules)

| Caller State | Resource State | Expected Response |
| --- | --- | --- |
| Anonymous | Any assignment or submission route | `401 UNAUTHORIZED` |
| Non-owner Instructor | Any instructor assignment/submission route | `404 NOT_FOUND` (Course ownership guard) |
| Active Learner | Unpublished / Draft Assignment | `404 NOT_FOUND` |
| Enrolled Learner | Soft-deleted Course Assignment | `404 NOT_FOUND` |
| Signed-in, NOT Enrolled | Published Course Assignment | `403 FORBIDDEN` (`code: ENROLLMENT_REQUIRED`) |
| Withdrawn Learner | Course Assignment | `403 FORBIDDEN` (`code: ENROLLMENT_REQUIRED`) |
| Active Learner | Published Course Unpublished Later | `200 OK` (Learners retain access per Assumption A4) |
| Active Learner | Own Started Attempt / Submission | `200 OK` (`Cache-Control: no-store`) |
| Learner A | Learner B's Submission or Files | `404 NOT_FOUND` |

---

## 4. Timing Rules & Expiry Flow

### Authoritative Time Architecture
- Client clocks are untrusted. The injected Spring bean `java.time.Clock` is the sole authority.
- In `POST /api/v1/learning/assignments/{id}/start`:
  - If `opensAt` is in the future: rejected with `409 ASSIGNMENT_NOT_OPEN`.
  - If `dueAt` is past and `allowLate = false`: rejected with `409 DEADLINE_PASSED`.
  - If `dueAt` is past and `allowLate = true`: attempt started, marked `late = true`.
  - For timed assessments (`timeLimitMinutes` set):
    $$\text{nominalDeadline} = \text{startedAt} + \text{timeLimitMinutes}$$
    If `allowLate = false` and `dueAt` is present:
    $$\text{deadlineAt} = \min(\text{nominalDeadline}, \text{dueAt})$$
    Otherwise:
    $$\text{deadlineAt} = \text{nominalDeadline}$$

### Worked Timing Example
- Assessment has `timeLimitMinutes = 60`, `dueAt = 12:00:00 UTC`, `allowLate = false`.
- Learner starts at `11:15:00 UTC`.
- Nominal deadline: `12:15:00 UTC`.
- Capped deadline: `deadlineAt = 12:00:00 UTC` (45 minutes effective time).
- Server grace period (`peergeek.assignment.timer.grace-seconds`, default 10s) applies for network latency:
  - Saves/submits accepted until `12:00:10 UTC`.
  - Requests received at `12:00:11 UTC` are rejected with `409 ATTEMPT_EXPIRED`.

### Expiry Finalizer
An `IN_PROGRESS` timed attempt past `deadlineAt + grace` is finalized automatically:
1. **Lazy Finalization**: Triggered on any read (`GET .../attempt`) or write (`PUT .../draft`, `POST .../submit`).
2. **Scheduled Job**: Runs periodically (`peergeek.assignment.expiry.cron`, default every 1 minute) querying `status = 'IN_PROGRESS' AND deadline_at < now - grace`.
3. Auto-finalization scores MCQs, sets `status = 'SUBMITTED'`, `auto_submitted = true`, and publishes `SubmissionSubmittedEvent`. If all questions are auto-gradable, transitions immediately to `GRADED` with `auto_graded = true`.

---

## 5. Scoring & Auto-Grading Rules

1. **MCQ Scoring**:
   - `MCQ_SINGLE`: 100% points if selected option matches the correct option; 0 otherwise.
   - `MCQ_MULTI`: All-or-nothing. 100% points if the set of selected options exactly matches the set of correct options; 0 otherwise.
2. **Fully Auto-Gradable Submissions**:
   - If an assessment consists solely of auto-gradable questions (`MCQ_SINGLE` and `MCQ_MULTI`), upon submit (or auto-submit) the submission transitions directly to `GRADED` with `graded_by = NULL` and `auto_graded = true`.
   - `SubmissionSubmittedEvent` and `SubmissionGradedEvent` are both published after commit.
3. **Manual Grading (`PUT /api/v1/instructor/submissions/{id}/grade`)**:
   - For `PROJECT`: Instructor provides `marks` ($0 \le \text{marks} \le \text{maxPoints}$) and optional `overallFeedback`.
   - For `QUESTIONS`: Instructor must provide `points` ($0 \le \text{points} \le \text{question.points}$) for every non-auto question (incomplete grading returns `400 GRADING_INCOMPLETE`). Instructor may override MCQ auto-points.
   - `total_points` is recomputed server-side as the sum of effective question points.
   - Optimistic locking via `version` prevents concurrent grading collisions (returns `409 Conflict`).
   - Sets `graded_by = currentUserId`, `graded_at = now()`, and `status = 'GRADED'`.

---

## 6. Learner Data Leak Guards

Learners are strictly protected against data leaks:
1. **Questions Before Start**: Assignment details prior to starting an attempt contain NO questions (only title, instructions, maxPoints, and timing).
2. **Correct Answer Secrecy**: Correct answers/flags are NEVER returned in details, start, attempt, or draft responses.
3. **Conditional Correct Answer Revelation**: Correct options are returned to the learner if and only if:
   - `submission.status == 'GRADED'` **AND**
   - `assignment.show_correct_answers == true`
   Under any other condition, options contain only `{ "id": "...", "text": "..." }`.
4. **Internal Metadata Shielding**: Grader IDs, internal object storage keys, bucket names, and grader emails are never serialized in learner DTOs.
5. **No Web Storage**: Attempts, drafts, and responses are never stored in `localStorage` or `sessionStorage`.

---

## 7. Submission Files & Object Storage Pipeline

- Prefix: `submissions/{assignmentId}/{submissionId}/{UUID}` (distinct from Phase 3 resource keys).
- Allowlist: `pdf, docx, pptx, xlsx, txt, csv, png, jpeg, zip`.
- Technical Caps: Max 5 files per submission, 25 MB per file.
- Direct Upload Flow:
  1. `POST /api/v1/learning/assignments/{id}/files/uploads`: Generates presigned PUT URL with exact Content-Type and Content-Length.
  2. Client uploads file directly to object storage via XHR PUT.
  3. `POST /api/v1/learning/assignments/{id}/files/{fileId}/confirm`: Performs HEAD size verification, streams file to compute SHA-256 checksum, and sniffs magic bytes. If verification passes, status becomes `READY`; if tampered or invalid, status becomes `FAILED` with stable failure reason.
- Downloads: Always presigned GET with `Content-Disposition: attachment; filename="..."`. Authorized exclusively for the submission owner learner or the course instructor.
- Cleanup Worker (`peergeek.assignment.cleanup.cron`): Cleans up abandoned `PENDING` uploads older than 24h and permanently deletes `FAILED` records older than 24h.

---

## 8. Seams, Events & Downstream Integration

### Domain Events (Published `AFTER_COMMIT`)
- `AssignmentPublishedEvent(courseId, assignmentId, title)`
- `SubmissionSubmittedEvent(courseId, assignmentId, submissionId, userId, late, autoSubmitted)`
- `SubmissionGradedEvent(courseId, assignmentId, submissionId, learnerId, graderId, totalPoints, maxPoints, autoGraded, regrade)`

### Read-Only Query Services (Exclusive Seams for Phases 9 & 10)
Later phases (e.g. Phase 9 notifications, Phase 10 certificates and analytics) must read assignment and submission data exclusively through these two Spring beans:
1. **`AssignmentQueryService`**:
   - `PageResponse<UpcomingDeadlineDto> listUpcomingDeadlines(Instant from, Instant to, Pageable pageable)`
   - `PageResponse<Long> listLearnerIdsWithoutSubmission(Long assignmentId, Pageable pageable)`
   - `long countPendingSubmissions(Long courseId)`
   - `List<AssignmentSummaryDto> getAssignmentSummaries(Long courseId)`
2. **`SubmissionQueryService`**:
   - `Optional<AssignmentCompletionStatsDto> getCompletionStats(Long assignmentId)`
   - `Optional<AssignmentScoreDistributionDto> getScoreDistribution(Long assignmentId)`

### ReadinessRule Contribution
- `AssignmentDraftExistsReadinessRule` (`ASSIGNMENT_DRAFT_EXISTS`):
  - Category: `CONTENT`
  - Severity: `WARNING`
  - Evaluation: `FAST`
  - Checks if a course has assignments in `DRAFT` status. Emits a non-blocking warning recommending instructors publish or delete drafts. Never blocks course publishing.
