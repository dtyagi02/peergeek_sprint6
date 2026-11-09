# Phase 2: Course authoring core

Phase 2 lets any authenticated account author its own draft courses. `USER`
and `ADMIN` remain the only authorization roles; `INSTRUCTOR` is a saved UI
mode and is not required by the API. Ownership is enforced in backend
services. A different owner receives the standard `404 NOT_FOUND` response to
avoid disclosing the resource.

## Data model

- `categories` contains seeded, uniquely named/sluggified categories. Only
  active categories are returned by the public category endpoint.
- `courses` belongs to a `users` row and may reference an active category. It
  has nullable description/difficulty, `DRAFT` status at creation, a
  content-updated timestamp, soft-delete timestamp, and optimistic version.
- `course_topics` contains the course's ordered high-level topic labels.
- `course_modules` and `lessons` store gap-free, zero-based positions.
- `lesson_contents` is created lazily on first save, one row per lesson. Its
  `content_document` is MySQL `JSON`, `content_format` is
  `tiptap-json-v1`, and its `version` protects concurrent editor tabs.
- `thumbnail_resource_id` is nullable and deliberately has **no foreign key**.
  Phase 3 owns resources and decides how resource IDs are attached.

Deleting a course sets `deleted_at`, only if it is a draft. All reads and
structure operations treat a deleted course as not found. Deleting a module or
lesson transactionally deletes its child lesson/content rows and closes the
position gaps. The database also cascades module/lesson/content removal if the
parent row is removed outside the normal service path.

Course status stores `DRAFT`, `PUBLISHED`, and `UNPUBLISHED`; Phase 2 exposes no
publish/unpublish endpoint and creates only drafts. Metadata, outline, and
content edits are not restricted by status.

## Endpoints

All paths below include the `/api/v1` prefix. Except for the category
endpoint, each route requires authentication and enforces ownership in its
service.

| Method | Path | Behavior |
| --- | --- | --- |
| `GET` | `/categories` | Public; active categories ordered by `sort_order` |
| `POST` | `/instructor/courses` | Create a draft owned by the caller |
| `GET` | `/instructor/courses` | Caller-owned, non-deleted courses; `page`, `size`, `sort`, `status`, `q` |
| `GET` | `/instructor/courses/{courseId}` | Details, topics, category, counts, timestamps, version |
| `PATCH` | `/instructor/courses/{courseId}` | Partial details update; `version` is required |
| `DELETE` | `/instructor/courses/{courseId}` | Soft-delete a draft; `204` on success |
| `GET` | `/instructor/courses/{courseId}/outline` | Full module/lesson outline with content flags and word counts |
| `POST` | `/instructor/courses/{courseId}/modules` | Append a module |
| `PATCH` | `/instructor/modules/{moduleId}` | Update module title/description |
| `DELETE` | `/instructor/modules/{moduleId}` | Delete module, its lessons/content, and close the gap |
| `PUT` | `/instructor/courses/{courseId}/modules/order` | Replace module order with the exact module ID set |
| `POST` | `/instructor/modules/{moduleId}/lessons` | Append a lesson |
| `PATCH` | `/instructor/lessons/{lessonId}` | Update lesson title/summary |
| `DELETE` | `/instructor/lessons/{lessonId}` | Delete lesson/content and close the gap |
| `PUT` | `/instructor/modules/{moduleId}/lessons/order` | Replace lesson order with the exact lesson ID set |
| `POST` | `/instructor/lessons/{lessonId}/move` | Move a lesson within the same course to a requested position |
| `GET` | `/instructor/lessons/{lessonId}/content` | Return the saved document or the empty-document/version-0 payload |
| `PUT` | `/instructor/lessons/{lessonId}/content` | Validate and save document using `baseVersion` |

The course list uses the common `PageResponse<T>` wrapper. Its sort whitelist is
`updatedAt`, `createdAt`, or `title`; the default is `updatedAt,desc`.
`q` performs case-insensitive title containment. `size` follows the frozen
common pagination size cap.

Course creation requires only a valid title. Description, category, difficulty,
and topics can be filled later. Topics are trimmed, 2–40 characters, unique
case-insensitively per course, and limited to 10.

## Validation and technical limits

`CourseSettings` binds typed properties under `peergeek.course`. Compose
environment names and defaults are in `.env.example`.

| Setting | Default |
| --- | ---: |
| Course title | 3–120 characters |
| Course description | 5,000 characters |
| Topics | 10 per course; each 2–40 characters |
| Module title / description | 120 / 1,000 characters |
| Modules per course | 200 |
| Lesson title / summary | 150 / 500 characters |
| Lessons per module | 500 |
| Compact UTF-8 lesson JSON document | 2 MiB |
| Lesson JSON nesting depth | 20 |

The counts are abuse-protection limits, not instructional requirements. Text
fields are trimmed before persistence. Invalid values return the standard
error format with `code: "VALIDATION_FAILED"` and field errors. An oversized
document is rejected as a validation error (`400`). MySQL's
`max_allowed_packet` must exceed the content cap; Compose sets it to 16 MiB.

## Lesson document contract

Lesson documents are ProseMirror JSON using `content_format =
'tiptap-json-v1'`. The server validates every save and computes `wordCount`;
client-supplied counts are not accepted.

Allowed node types:

- `doc`
- `paragraph`
- `heading` with only an integer `attrs.level` from 1 through 3
- `text`
- `bulletList`, `orderedList`, and `listItem`
- `blockquote`
- `codeBlock` with optional string `attrs.language` (maximum 30 characters)
- `hardBreak` and `horizontalRule`

Allowed marks on text nodes are `bold`, `italic`, `code`, and `link`. A link
has only the `href` attribute and must use `http`, `https`, or `mailto`.
Unknown nodes, marks, attributes, and URL protocols are rejected. Images,
files, embeds, and other resource nodes are not supported in this phase.

Word count matches Unicode letter/number sequences in text nodes. A single
apostrophe or hyphen between word segments keeps them together (for example,
`don't` and `re-enter` each count as one). Punctuation and whitespace separate
words. A document with no word characters has word count zero.

## Concurrency, audit, and events

Course detail PATCH requires the current course `version`; content PUT requires
`baseVersion` (`0` if no content row exists yet). A stale version returns
`409 CONFLICT`. Successful writes return the current version. Module/lesson
reorders must provide exactly the existing IDs, without duplicates, missing
IDs, or extras. Parent-row locks serialize concurrent reorders and updates.

Course creation, details updates, and soft deletion write `COURSE_CREATED`,
`COURSE_UPDATED`, and `COURSE_DELETED` audit actions. Update audit details
contain only the changed field names, never the submitted text. Individual
module, lesson, and content edits are not audited in Phase 2.

Every structure or lesson-content write updates `courses.content_updated_at`
and publishes one `CourseContentChangedEvent(courseId)` **after the
transaction commits**. A rollback publishes no event. There are no listeners
in this phase; Phases 4 and 5 may consume this hook.

## Downstream read extension point

Later backend phases must read structure and lesson text through
`CourseStructureQueryService` in `com.peergeek.course`, rather than importing
module/lesson repositories or entities. It provides immutable snapshots:

```java
CourseStructure structure = structureQueryService.getStructure(courseId);
LessonContentSnapshot content = structureQueryService.getLessonContent(lessonId);
Long ownerId = structureQueryService.getCourseOwnerId(courseId);
```

The query service deliberately performs **no authorization**. A caller first
applies the policy appropriate to its endpoint (course ownership in Phase 4,
enrollment in Phase 6, or another explicitly defined policy), then uses the
snapshot. It also hides soft-deleted courses.

## Frontend

The feature registers protected routes for the dashboard, new-course form,
course Details/Outline tabs, and lesson editor. Its navigation entry is shown
only in `INSTRUCTOR` UI mode. Student-mode visits display a non-blocking
switch-mode banner; server authorization remains authoritative.

The TipTap editor is lazy-loaded and configured for the same node/mark
whitelist. It provides a keyboard-operable toolbar, live word count, explicit
save state, Ctrl/Cmd+S, conflict choices, and unsaved-change prompts. Outline
drag-and-drop supports pointer and keyboard sensors, with explicit move-up and
move-down controls as a keyboard fallback.
