# Publishing and instructor preview

## Publish lifecycle

Publishing and unpublishing are authenticated instructor operations:

| Method | Path | Behavior |
| --- | --- | --- |
| `POST` | `/api/v1/instructor/courses/{courseId}/publish` | Publishes a draft or unpublished course only after a current passing readiness report. |
| `POST` | `/api/v1/instructor/courses/{courseId}/unpublish` | Moves a published course to `UNPUBLISHED`. |

Course ownership is enforced with not-found semantics. A database row lock
serializes publish state transitions. A publish request returns `409 Conflict`
if there is no completed report, the report is stale or expired, or it has
blocking issues. Warnings do not prevent publishing. The transaction updates
status and `published_at`, increments the course version, and records an audit
entry. `CoursePublishedEvent` and `CourseUnpublishedEvent` are emitted only
after transaction commit. Repeating an already-applied operation is
idempotent.

See [READINESS.md](./READINESS.md) for the run API, issue semantics, fingerprint,
checks, and configuration.

## Instructor preview

`GET /api/v1/instructor/courses/{courseId}/preview` returns the course title,
description, category, difficulty, topics, modules, module descriptions,
lessons, lesson summaries, text documents, and ready lesson resources. It is
restricted to the owning instructor. The response never exposes storage object
keys or internal signed URLs; stored resources use short-lived public
presigned GET URLs and external links remain ordinary HTTPS/HTTP links.

The frontend route `/instructor/courses/{courseId}/preview` renders the returned
lesson documents using the shared React renderer. The renderer maps supported
document nodes and safe links to React elements; it does not inject HTML.
