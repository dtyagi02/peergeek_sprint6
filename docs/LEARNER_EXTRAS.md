# PeerGeek — Phase 11: Learner Extras (Bookmarks & Notes)

## 1. Overview & Goals
Phase 11 introduces two personal learning capabilities for enrolled students and learners:
1. **Course and Lesson Bookmarks**: Allows authenticated learners to bookmark courses from discovery/player and lessons from the player lesson list, quickly view all bookmarks at `/learning/bookmarks`, and remove bookmarks idempotently.
2. **Private Lesson Notes**: Allows enrolled learners to write, update, and delete rich notes for individual lessons in a dedicated panel within the course player's side-panel slot.

Both features are completely isolated from core learning workflows. Removing either feature causes zero regression to discovery, enrollment, lesson playback, assignments, reviews, notifications, or instructor analytics.

---

## 2. Relational Schema & Flyway Migrations

### `V11_001__create_bookmarks.sql`
Creates the `bookmarks` table with target discriminator (`COURSE`, `LESSON`), course/lesson references, and a stored generated column to enforce uniqueness without nullable index quirks:
```sql
CREATE TABLE bookmarks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    target_type ENUM('COURSE', 'LESSON') NOT NULL,
    course_id BIGINT NOT NULL,
    lesson_id BIGINT NULL,
    target_key VARCHAR(64) GENERATED ALWAYS AS (
        CASE
            WHEN target_type = 'COURSE' THEN CONCAT('C:', course_id)
            ELSE CONCAT('L:', lesson_id)
        END
    ) STORED,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_bookmarks_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_bookmarks_course FOREIGN KEY (course_id) REFERENCES courses (id),
    CONSTRAINT chk_bookmarks_target CHECK (
        (target_type = 'COURSE' AND lesson_id IS NULL) OR
        (target_type = 'LESSON' AND lesson_id IS NOT NULL)
    ),
    CONSTRAINT uq_bookmarks_user_target UNIQUE (user_id, target_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
```

### `V11_002__create_lesson_notes.sql`
Creates the `lesson_notes` table enforcing strictly one note per user per lesson:
```sql
CREATE TABLE lesson_notes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,
    lesson_id BIGINT NOT NULL,
    content MEDIUMTEXT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_lesson_notes_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_lesson_notes_course FOREIGN KEY (course_id) REFERENCES courses (id),
    CONSTRAINT fk_lesson_notes_lesson FOREIGN KEY (lesson_id) REFERENCES lessons (id),
    CONSTRAINT uq_lesson_notes_user_lesson UNIQUE (user_id, lesson_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
```

---

## 3. Security, Authorization & Mandatory Privacy Guarantees

1. **Authentication Boundary**: All bookmark and note endpoints require an authenticated JWT token. Anonymous calls receive `401 Unauthorized`.
2. **Current-User Derivation**: Ownership is exclusively derived via `CurrentUserProvider.requireCurrentUser()`. Client-supplied user IDs are ignored.
3. **Strict Note Confidentiality**:
   - Notes are private to each individual learner.
   - Instructors and other learners can never query, read, or modify another learner's notes.
   - Service methods filter queries by `user_id = :authenticatedUserId`.
   - Note contents are never exposed in audit logs, system logs, or public APIs.
4. **Course & Lesson Access Control**:
   - Adding or viewing notes requires active course enrollment (`EnrollmentStatus.ACTIVE`).
   - Unenrolled users attempting to access notes receive `403 Forbidden` with error code `ENROLLMENT_REQUIRED`.
   - Non-existent courses/lessons yield `404 Not Found`.

---

## 4. Frontend Architecture & Extension Points

### Features
- `frontend/src/features/bookmarks/`:
  - `BookmarkToggle.jsx`: Accessible toggle button with optimistic caching and aria labels.
  - `BookmarksPage.jsx`: Filterable listing ("All", "Courses", "Lessons"), loading skeletons, empty state, and remove action.
  - `BookmarkSlotComponents.jsx`: Wrappers for slot contributions.
  - `navigation.js`: Registers "Bookmarks" in student navigation menu.
  - `routes.js`: Protected route `/learning/bookmarks`.
- `frontend/src/features/notes/`:
  - `LessonNotesPanel.jsx`: Side-panel rich note editor with key-based lesson isolation (`key={courseId-lessonId}`), unsaved edits detection, character/word counter, and delete confirmation modal.

### Registered Slot Contributions
| Slot Name | Contributing Component | Order | Purpose |
| --- | --- | --- | --- |
| `course-page.enroll-area` | `CourseBookmarkSlot` | 15 | Allows bookmarking course from preview page |
| `player.header-actions` | `CourseBookmarkSlot` | 15 | Allows bookmarking course from player header |
| `player.lesson-actions` | `LessonBookmarkSlot` | 15 | Allows bookmarking individual lessons from lesson player |
| `player.side-panel` | `LessonNotesPanel` | 10 | Displays private lesson notes editor below lesson content |

---

## 5. Verification Matrix

| Test Suite | Tests | Result | Notes |
| --- | --- | --- | --- |
| `BookmarkIntegrationTest` | 4 tests | PASS | Course & lesson bookmark lifecycle, status check, user isolation, unauthenticated rejection |
| `LessonNoteIntegrationTest` | 4 tests | PASS | Note upsert & deletion, active enrollment check (403), strict cross-user privacy, 404 on missing lesson |
| `FoundationIntegrationTest` | 10 tests | PASS | Verified Flyway migration history and database tables including `bookmarks` and `lesson_notes` |
| `bookmarks.test.jsx` | 6 tests | PASS | Toggle states, course/lesson bookmarking, BookmarksPage empty/error/filter/delete |
| `notes.test.jsx` | 4 tests | PASS | Note loading, typing & saving, deletion with confirmation, lesson switching isolation, error retry |
| Full Frontend Suite | 126 tests across 20 files | PASS | 100% green with 0 failures |
| ESLint & Prettier | Codebase | PASS | 0 lint errors, 100% formatted |
| Vite Production Build | Client | PASS | Production bundle compiled successfully |
