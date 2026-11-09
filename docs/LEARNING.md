# Enrollment & Learner Experience (Phase 6)

## Overview

Phase 6 implements the complete enrollment lifecycle and authenticated learner experience for PeerGeek. Authenticated users can enroll in published courses, access the full course outline and lesson contents, stream/view/download authorized lesson resources through the existing voter chain, track lesson completion and course progress, and pick up right where they left off via Continue Learning.

Enrollment and completion counts feed back into Phase 5 discovery metrics via `CourseStatsRecorder`.

---

## 1. Data Model & Migrations

Phase 6 creates three tables in the `V6_xxx` migration range:

### `enrollments` (`V6_001__create_enrollments.sql`)
Represents a user's enrollment record in a course.
- `id` BIGINT PRIMARY KEY AUTO_INCREMENT
- `user_id` BIGINT NOT NULL (FK `users(id)`)
- `course_id` BIGINT NOT NULL (FK `courses(id)`)
- `status` ENUM('ACTIVE', 'WITHDRAWN') NOT NULL
- `enrolled_at` TIMESTAMP NOT NULL
- `withdrawn_at` TIMESTAMP NULL
- `last_lesson_id` BIGINT NULL (no FK; lessons can be deleted without notice)
- `last_accessed_at` TIMESTAMP NULL
- `version` BIGINT NOT NULL DEFAULT 0
- `created_at` TIMESTAMP NOT NULL
- `updated_at` TIMESTAMP NOT NULL
- **Constraints & Indexes**:
  - `UNIQUE (user_id, course_id)`
  - `INDEX idx_enrollments_user_status (user_id, status, updated_at)`
  - `INDEX idx_enrollments_course_status (course_id, status)`

### `lesson_progress` (`V6_002__create_lesson_progress.sql`)
Tracks completion of individual lessons by an enrolled user.
- `id` BIGINT PRIMARY KEY AUTO_INCREMENT
- `enrollment_id` BIGINT NOT NULL (FK `enrollments(id)` ON DELETE CASCADE)
- `lesson_id` BIGINT NOT NULL (no FK; lessons can be deleted without notice)
- `completed_at` TIMESTAMP NOT NULL
- `created_at` TIMESTAMP NOT NULL
- `updated_at` TIMESTAMP NOT NULL
- **Constraints & Indexes**:
  - `UNIQUE (enrollment_id, lesson_id)`
  - `INDEX idx_lesson_progress_lesson (lesson_id)`

### `course_progress` (`V6_003__create_course_progress.sql`)
Stores computed course completion statistics for an enrollment.
- `enrollment_id` BIGINT PRIMARY KEY (FK `enrollments(id)` ON DELETE CASCADE)
- `course_id` BIGINT NOT NULL (indexed)
- `total_lessons` INT NOT NULL
- `completed_lessons` INT NOT NULL
- `percent` TINYINT NOT NULL
- `completed_at` TIMESTAMP NULL
- `stale` BOOLEAN NOT NULL DEFAULT FALSE
- `computed_at` TIMESTAMP NOT NULL
- `updated_at` TIMESTAMP NOT NULL
- **Constraints & Indexes**:
  - `INDEX idx_course_progress_stale (course_id, stale)`

### Foreign Key Rules
Foreign keys exist **strictly** to `users` and `courses`, and cascading between Phase 6's own tables (`enrollments` -> `lesson_progress`, `enrollments` -> `course_progress`). There are **no foreign keys** to `lessons`, `modules`, or `resources` because earlier phases allow instructors to delete lessons, modules, and resources without notice. Ignored or orphaned progress records are filtered at query/recomputation time.

---

## 2. Access & Visibility Matrix (404 vs 403)

Learner endpoints require authentication and active enrollment. The system enforces strict 404 indistinguishability to prevent unauthorized existence-probing of draft, unpublished, or deleted courses.

| Course Status / State | User Enrollment State | Accessing Course/Lesson Endpoint | Accessing Lesson Resource URL Endpoint |
| --- | --- | --- | --- |
| **Anonymous** (any course) | N/A | **401 Unauthorized** | **401 Unauthorized** |
| **Nonexistent** course | Any | **404 Not Found** | **404 Not Found** |
| **Deleted** (`deleted = true`) | Any (even if enrolled) | **404 Not Found** | **404 Not Found** |
| **Draft / Unpublished** | Not Enrolled | **404 Not Found** | **404 Not Found** |
| **Draft / Unpublished** | `WITHDRAWN` | **404 Not Found** | **404 Not Found** |
| **Draft / Unpublished** | `ACTIVE` Enrolled | **200 OK** (Assumption A4: learners keep access) | **200 OK** (Presigned URL) |
| **Published** | Not Enrolled | **403 Forbidden** (`code: ENROLLMENT_REQUIRED`) | **404 Not Found** (Resource existence never revealed) |
| **Published** | `WITHDRAWN` | **403 Forbidden** (`code: ENROLLMENT_REQUIRED`) | **404 Not Found** |
| **Published** | `ACTIVE` Enrolled | **200 OK** | **200 OK** (Presigned URL) |
| **Published** | Owner of course | **403 Forbidden** (Owner uses instructor preview) | **403/404** (Owner voter uses authoring seams) |

---

## 3. Endpoints Specification

All learner endpoints are under `/api/v1/learning`, require a valid bearer JWT, and return `Cache-Control: no-store` (learner data is private and must never be cached across users).

### Enrollment Operations

#### 1. Enroll in a Course
- `POST /api/v1/learning/courses/{courseId}/enroll`
- **Behavior**: Idempotent. Creates a new `ACTIVE` enrollment, reactivates a `WITHDRAWN` row, or returns the existing `ACTIVE` row. Uses database atomic upsert (`ON DUPLICATE KEY UPDATE`) to guarantee concurrency safety under concurrent double-submits.
- **Rules**:
  - Non-published or deleted course -> `404 Not Found`
  - Course owner -> `409 Conflict` (`code: CANNOT_ENROLL_OWN_COURSE`)
- **Audit**: `LEARNER_ENROLLED`
- **Response**:
  ```json
  {
    "courseId": 42,
    "status": "ACTIVE",
    "enrolledAt": "2026-10-20T10:00:00Z",
    "progress": {
      "completedLessons": 0,
      "totalLessons": 5,
      "percent": 0,
      "completedAt": null
    }
  }
  ```

#### 2. Withdraw from a Course
- `DELETE /api/v1/learning/courses/{courseId}/enrollment`
- **Behavior**: Soft withdrawal (`ACTIVE` -> `WITHDRAWN`). Sets `withdrawn_at`. Idempotent if already withdrawn.
- **Rules**:
  - If never enrolled -> `404 Not Found`
- **Audit**: `LEARNER_WITHDRAWN`
- **Response**: `204 No Content`

#### 3. Get Enrollment Status (for Preview / Enroll Slot)
- `GET /api/v1/learning/courses/{courseId}/enrollment`
- **Behavior**: Returns enrollment status for the current user. Used by the preview enroll area slot. Never returns 403.
- **Rules**: Non-visible course returns 404.
- **Response**:
  ```json
  {
    "enrolled": true,
    "status": "ACTIVE",
    "isOwner": false,
    "canEnroll": false,
    "progress": {
      "completedLessons": 2,
      "totalLessons": 5,
      "percent": 40,
      "completedAt": null
    }
  }
  ```

#### 4. My Learning Courses
- `GET /api/v1/learning/courses?status=all|in-progress|completed&q=&page=0&size=12&sort=lastAccessedAt,desc`
- **Behavior**: Returns paginated cards for the caller's `ACTIVE` enrollments.
- **Sorting**: Whitelisted to `lastAccessedAt` (default, desc), `enrolledAt`, `title`, `progress` (maps to `percent`). Unique tiebreaker (`id desc`).
- **N+1 Prevention**: Uses bulk lookups for courses and instructors; verified in test to run a constant small number of queries regardless of page size.
- **Response**:
  ```json
  {
    "content": [
      {
        "courseId": 42,
        "title": "Compiler Engineering",
        "thumbnailUrl": "/api/v1/public/courses/42/thumbnail",
        "instructor": { "id": 10, "displayName": "Prof. Aho" },
        "difficulty": "ADVANCED",
        "courseStatus": "PUBLISHED",
        "progress": {
          "completedLessons": 5,
          "totalLessons": 5,
          "percent": 100,
          "completedAt": "2026-10-20T12:00:00Z"
        },
        "lastAccessedAt": "2026-10-20T14:00:00Z",
        "continueLessonId": null
      }
    ],
    "page": 0,
    "size": 12,
    "totalElements": 1,
    "totalPages": 1
  }
  ```

---

### Learner Content & Progress Operations

#### 5. Learner Outline
- `GET /api/v1/learning/courses/{courseId}`
- **Behavior**: Assembles full course outline using `CourseLearnerViewAssembler`, decorated with lesson completion flags and Continue Learning recommendation.
- **Response**:
  ```json
  {
    "course": {
      "id": 42,
      "title": "Compiler Engineering",
      "description": "Full compiler pipeline.",
      "category": "Computer Science",
      "difficulty": "ADVANCED",
      "topics": ["Lexing", "Parsing", "LLVM"],
      "instructor": { "id": 10, "displayName": "Prof. Aho", "headline": "Professor", "bio": "..." },
      "courseStatus": "PUBLISHED"
    },
    "modules": [
      {
        "id": 101,
        "title": "Lexical Analysis",
        "description": "Tokens & regex",
        "position": 1,
        "lessons": [
          { "id": 201, "title": "Finite Automata", "summary": "DFA/NFA", "position": 1, "completed": true }
        ]
      }
    ],
    "progress": {
      "completedLessons": 1,
      "totalLessons": 5,
      "percent": 20,
      "completedAt": null
    },
    "continueLesson": {
      "lessonId": 202,
      "reason": "NEXT"
    }
  }
  ```

#### 6. Learner Lesson Content & Resources
- `GET /api/v1/learning/courses/{courseId}/lessons/{lessonId}`
- **Behavior**: Returns lesson content document (ProseMirror JSON), authorized resources, completion state, and adjacent lesson IDs (`previousLessonId`, `nextLessonId`) across modules in course order.
- **Resource Authorization**: Every resource URL is authorized via `ResourceAccessService.authorize(resourceId, VIEW/DOWNLOAD, caller)`. Denied or non-ready resources are excluded.
- **Presigned URLs**: Inline disposition for `PDF`, `IMAGE`, `VIDEO`; attachment for `DOCUMENT`; raw external URL for `LINK`. Exposes no bucket names, storage keys, or internal hostnames.

#### 7. Refresh Presigned Resource URL
- `GET /api/v1/learning/resources/{resourceId}/url?disposition=inline|attachment`
- **Behavior**: Obtains a fresh presigned GET URL for an expired resource URL.
- **Security**: Must be a `READY` `LESSON_RESOURCE` of a course in which the user is actively enrolled. Denied access returns `404 Not Found` (never reveals existence).

#### 8. Record Lesson Visit
- `POST /api/v1/learning/courses/{courseId}/lessons/{lessonId}/visit`
- **Behavior**: Updates `enrollment.last_lesson_id` and `last_accessed_at`. Called once per lesson view by frontend player. Lightweight, idempotent. GET endpoints never write.

#### 9. Toggle Lesson Completion
- `PUT /api/v1/learning/courses/{courseId}/lessons/{lessonId}/completion`
- **Body**: `{ "completed": true | false }`
- **Behavior**: Sets or unsets completion for the lesson. Updates `lesson_progress` and `course_progress` within a single database transaction. Returns updated progress DTO.

#### 10. Continue Learning Recommendation
- `GET /api/v1/learning/courses/{courseId}/continue`
- **Behavior**: Calculates the recommended lesson to resume learning according to the 4-step rule.
- **Response**: `{ "lessonId": 202, "reason": "NEXT" }`

---

## 4. Progress Math & Staleness Engine

### Progress Calculation Formula
$$\text{completed} = \text{number of completed lessons that CURRENTLY exist in the course}$$
$$\text{total} = \text{number of lessons that CURRENTLY exist in the course}$$
$$\text{percent} = \begin{cases} 0 & \text{if } \text{total} = 0 \\ \lfloor \frac{\text{completed} \times 100}{\text{total}} \rfloor & \text{if } \text{total} > 0 \end{cases}$$

- A course with 0 lessons always has 0% progress and is never completed.
- Completed progress rows for lessons deleted by the instructor are ignored.

### "Ever Completed" Invariant
`completed_at` is set the **first time** progress reaches 100% (`percent == 100`). If an instructor subsequently adds new lessons, `percent` drops (e.g. to 80%), but `completed_at` remains set. `completion_count` in discovery stats reflects active enrollments where `completed_at IS NOT NULL`.

### Staleness & Lazy Recomputation
When an instructor modifies course structure (modules or lessons added, removed, or reordered), `CourseContentChangedEvent` is published. A listener on this event runs **ONE** atomic SQL statement:
```sql
UPDATE course_progress SET stale = TRUE WHERE course_id = :courseId;
```
No bulk recomputation of all learners is executed on authoring edits. Instead, stale rows are lazily recomputed on the learner's next read or write operation.

---

## 5. Continue Learning Resolution Rules

Continue Learning resolves the next lesson in deterministic order:
1. **`RESUME`**: If `enrollment.last_lesson_id` is set, that lesson still exists in the course, and is not completed -> resume that lesson.
2. **`NEXT`**: Find the first incomplete lesson strictly *after* the last visited lesson in course order (across modules).
3. **`FIRST`**: Find the first incomplete lesson from the beginning of the course.
4. **`COMPLETED`**: If all lessons are completed (or course has 0 lessons) -> `lessonId = null`, reason `COMPLETED`.

---

## 6. EnrolledLearnerVoter Specification

`EnrolledLearnerVoter` is a new Spring component in `com.peergeek.enrollment` implementing `ResourceAccessVoter`:
- **Order**: Configured with `@Order(200)` (evaluates after `OwnerResourceVoter` at `@Order(100)`).
- **Grant Conditions**:
  - `action == VIEW` or `action == DOWNLOAD` (never `MANAGE`).
  - `resource.purpose == LESSON_RESOURCE`.
  - `resource.status == READY`.
  - `user` is authenticated (non-anonymous).
  - User has an `ACTIVE` enrollment in `resource.courseId`.
  - Course is not soft-deleted.
- **Abstain vs Deny**: Returns `ResourceAccessDecision.ABSTAIN` (never `DENY`) for all other cases, preserving the owner voter grant and default-deny security architecture.
- **Request-Scoped Memoization**: Uses a `ThreadLocal` or request-scoped cache so checking 50 resources on a single lesson costs at most a constant number of enrollment checks.

---

## 7. Events, Stats Refresher & Audit

### Domain Events (Extending `DomainEvent`, Published AFTER_COMMIT)
1. `LearnerEnrolledEvent(courseId, userId, enrollmentId)`
2. `LearnerWithdrawnEvent(courseId, userId)`
3. `LessonCompletedEvent(courseId, userId, lessonId, completedLessons, totalLessons)`

These events serve as extension hooks for Phase 9 (Assignments) and Phase 10 (Certificates/Notifications).

### StatsRefresher
An asynchronous / after-commit listener on Phase 6 events that recomputes absolute metrics with two fast count queries:
- `enrollmentCount = SELECT COUNT(*) FROM enrollments WHERE course_id = :id AND status = 'ACTIVE'`
- `completionCount = SELECT COUNT(*) FROM course_progress cp JOIN enrollments e ON cp.enrollment_id = e.id WHERE e.course_id = :id AND e.status = 'ACTIVE' AND cp.completed_at IS NOT NULL`

Calls `CourseStatsRecorder.recordEnrollmentStats(courseId, enrollmentCount, completionCount)`. Failures are caught and logged; they never fail learner requests.

### Audit Logging
Uses `LearningAuditActions`:
- `LEARNER_ENROLLED`
- `LEARNER_WITHDRAWN`
Audit records contain IDs only. Lesson completions are high-frequency and are not audited.

---

## 8. Extension Hooks for Later Phases

### Backend: `EnrollmentQueryService`
The **only** permitted mechanism for future phases (Phase 7 Reviews, Phase 8 Reports, Phase 9 Assignments, Phase 10 Certificates, Phase 11 Discussions) to check enrollment status:
- `boolean isActivelyEnrolled(Long userId, Long courseId)`
- `Optional<Long> getActiveEnrollmentId(Long userId, Long courseId)`
- `Set<Long> filterEnrolledCourseIds(Long userId, Collection<Long> courseIds)`
- `Map<Long, Boolean> checkEnrollmentsBulk(Collection<Long> userIds, Long courseId)`
- `PageResponse<Long> listActiveLearnerIds(Long courseId, Pageable pageable)`

`EnrollmentQueryService` contains no authorization logic; callers apply their own security rules.

### Frontend Slots
- `course-page.enroll-area`: Receives `{ courseId }`. Renders enrollment status, enroll button, progress summary, continue learning, and withdraw dialog.
- `player.side-panel`: Receives `{ courseId, lessonId }`. Extension slot for Phase 11 discussions / notes.
- `player.lesson-actions`: Receives `{ courseId, lessonId }`. Extension slot for Phase 8 issue reporting. Empty slots render nothing.
