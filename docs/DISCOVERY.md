# Discovery and Public Preview (Phase 5)

## Overview

Phase 5 introduces the public discovery and safe preview capabilities for PeerGeek. Anyone—whether unauthenticated (anonymous) or authenticated—can discover and preview published courses.

The discovery architecture is governed by strict boundaries:
- **Read model separation**: All public search and listing operations query a dedicated read model (`public_course_index`), never directly touching course authoring tables.
- **Strict public data allowlist**: DTOs are assembled field-by-field. No lesson body text, internal module/lesson/resource identifiers, resource counts, storage keys/buckets, or instructor emails are ever exposed.
- **404 indistinguishability**: Non-published courses (drafts, unpublished, deleted, or nonexistent) return byte-identical HTTP 404 responses across preview and thumbnail endpoints.
- **Disposable cache**: Redis speeds up read operations via `SafeCache` with single-flight DB loading and `{schemaVersion, payload}` versioning. The application functions identically (with low overhead) when Redis is down.
- **Push-based stats recorder**: Discovery exposes `CourseStatsRecorder` for Phase 6 (enrollment) and Phase 8 (ratings) to record absolute, idempotent metrics.

---

## Read Model Architecture

### 1. `public_course_index` Table

Owned by `discovery`, this denormalized table contains exactly one row per currently `PUBLISHED` and non-deleted course.

```sql
CREATE TABLE public_course_index (
    course_id BIGINT PRIMARY KEY,
    title VARCHAR(120) NOT NULL,
    short_description VARCHAR(300) NOT NULL,
    search_text TEXT COLLATE utf8mb4_0900_ai_ci NOT NULL,
    category_id BIGINT NOT NULL,
    category_name VARCHAR(100) NOT NULL,
    category_slug VARCHAR(120) NOT NULL,
    difficulty VARCHAR(32) NOT NULL,
    topics_text VARCHAR(500) NOT NULL,
    instructor_id BIGINT NOT NULL,
    instructor_name VARCHAR(100) NOT NULL,
    module_count INT NOT NULL,
    lesson_count INT NOT NULL,
    thumbnail_resource_id BIGINT NULL,
    published_at TIMESTAMP NOT NULL,
    source_fingerprint CHAR(64) NOT NULL,
    indexed_at TIMESTAMP NOT NULL,
    INDEX idx_pci_cat_pub (category_id, published_at),
    INDEX idx_pci_difficulty (difficulty),
    INDEX idx_pci_pub (published_at),
    INDEX idx_pci_title (title(50))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
```

### 2. `course_public_stats` Table

```sql
CREATE TABLE course_public_stats (
    course_id BIGINT PRIMARY KEY,
    enrollment_count INT NULL,
    completion_count INT NULL,
    rating_average DECIMAL(3, 2) NULL,
    rating_count INT NULL,
    updated_at TIMESTAMP NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

---

## Index Synchronization and Reconciliation

### Event-Driven Sync (`AFTER_COMMIT`)

Discovery listens for domain events and updates `public_course_index` without impacting primary transactional paths:

| Event | Action | Latency / Debouncing |
| --- | --- | --- |
| `CoursePublishedEvent` | Reads published snapshot, instructor profile, and saves index row; evicts caches. | Immediate (`0s` debounce) |
| `CourseUnpublishedEvent` | Deletes index row; evicts caches. | Immediate (`0s` debounce) |
| `CourseContentChangedEvent` | If course is indexed, marks course dirty in buffer. | Coalesced flush (`discovery.dirty-flush`, default `5s`) |
| `ResourceChangedEvent` (thumbnail / structure) | If course is indexed, marks course dirty in buffer. | Coalesced flush (`discovery.dirty-flush`, default `5s`) |

Events for non-published courses are ignored. Event listener failures are logged and never propagate or roll back caller transactions.

### Reconciliation Worker (`DiscoveryReconciliationService`)

Runs periodically (default: every 5 minutes, configured by `discovery.reconcile-interval=5m`) to guarantee eventual consistency:
1. **Re-index published courses**: Batches through all published courses, computes deterministic SHA-256 fingerprint, and updates any row where the fingerprint differs or is absent.
2. **Remove stale index rows**: Scans indexed rows and purges any entries whose courses are no longer published or have been deleted.
3. **Admin Trigger**: `POST /api/v1/admin/discovery/reindex` triggers reconciliation asynchronously. Returns `202 Accepted` to `ADMIN` users (audited with action `DISCOVERY_REINDEX_REQUESTED`), `403 Forbidden` to regular users, and `401 Unauthorized` to anonymous callers.

### Staleness Windows

| Scenario | Max Staleness |
| --- | --- |
| Publish / Unpublish | Immediate (`0s` after commit) |
| Lesson title / outline edit on published course | `<= discovery.dirty-flush` (default `5s`) |
| Category rename / Instructor profile edit / Missed event | `<= discovery.reconcile-interval` (default `5m`) |

---

## Public API

All public endpoints are under `/api/v1/public/`. Anonymous requests are permitted and bearer tokens are ignored.

### 1. Course Search & Filter

`GET /api/v1/public/courses?q=&category=&difficulty=&page=&size=&sort=`

- **`q`**: Free-text query. Whitespace-trimmed, max 100 characters, up to 8 whitespace-separated terms, each max 40 characters. All terms are matched via `AND`. Special characters (`%`, `_`, `\`) are safely escaped.
- **`category`**: Category slug.
- **`difficulty`**: Repeatable or comma-separated (`BEGINNER`, `INTERMEDIATE`, `ADVANCED`). Unknown value returns `400 Bad Request`.
- **`sort`**:
  - `popularity,desc`: `COALESCE(enrollment_count,0) DESC, COALESCE(rating_count,0) DESC, published_at DESC, course_id DESC` (default when `q` is absent)
  - `publishedAt,desc`: `published_at DESC, course_id DESC`
  - `rating,desc`: Courses with `rating_count > 0` ordered by `rating_average DESC, rating_count DESC`, followed by rest by `published_at DESC, course_id DESC`
  - `title,asc`: `title ASC, course_id DESC`
  - `relevance`: Weighted match (exact prefix > word prefix > substring > description). Only allowed when `q` is non-empty; requesting without `q` returns `400 Bad Request`.
- **`page` & `size`**: 0-based pagination, size default 12, max 50. `page * size` must not exceed `discovery.max-offset` (default 1000), otherwise returns `400 Bad Request`.
- **Response**: `PageResponse<CourseCardDto>` with `Cache-Control: public, max-age=60`.

### 2. Popular Courses

`GET /api/v1/public/courses/popular`

- Returns top `discovery.popular.size` (default 12) courses ordered by popularity.
- Response: `List<CourseCardDto>` with `Cache-Control: public, max-age=60`.

### 3. Public Course Preview

`GET /api/v1/public/courses/{courseId}`

- Returns `CoursePreviewDto` with weak `ETag` and `Cache-Control: public, max-age=60`.
- Even on cache hit, checks `isPublished(courseId)` in the database to prevent serving unpublished courses from warm cache.
- Non-published or missing courses return standard 404:
  ```json
  {
    "timestamp": "2026-10-20T00:00:00Z",
    "status": 404,
    "error": "Not Found",
    "code": "NOT_FOUND",
    "message": "Course not found",
    "path": "/api/v1/public/courses/999",
    "fieldErrors": []
  }
  ```

### 4. Course Thumbnail Redirect

`GET /api/v1/public/courses/{courseId}/thumbnail?v={thumbnailResourceId}`

- Sanctioned public caller of `ResourceDownloadService` with explicit authorization checks:
  1. Course exists and is `PUBLISHED`.
  2. Thumbnail resource belongs to that course.
  3. Resource purpose is `COURSE_THUMBNAIL` and status is `READY`.
- Returns `302 Found` with `Location: <presigned-GET-url-on-public-endpoint>` and `Cache-Control: public, max-age=60` (shorter than presigned URL expiry).

### 5. Public Categories

`GET /api/v1/public/categories`

- Returns active categories ordered by `sort_order ASC`, including count of currently published courses from the index:
  ```json
  [
    {
      "id": 1,
      "name": "Programming",
      "slug": "programming",
      "courseCount": 12
    }
  ]
  ```

---

## DTO Allowlist (Data Leak Prevention)

Public responses use strict allowlists built field-by-field:

### `CourseCardDto`
```json
{
  "id": 42,
  "title": "Introduction to Computer Systems",
  "shortDescription": "A guided journey through system architecture.",
  "thumbnailUrl": "/api/v1/public/courses/42/thumbnail?v=10",
  "category": { "id": 1, "name": "Programming", "slug": "programming" },
  "difficulty": "BEGINNER",
  "instructor": { "id": 100, "displayName": "Dennis Ritchie" },
  "moduleCount": 3,
  "lessonCount": 12,
  "publishedAt": "2026-10-15T12:00:00Z",
  "stats": {
    "enrollmentCount": 250,
    "completionCount": 180,
    "completionRate": 72,
    "ratingAverage": 4.85,
    "ratingCount": 42
  }
}
```

### `CoursePreviewDto`
Contains all card fields plus:
- `description`: Full course description (plain text).
- `topics`: String array of topic names (e.g. `["c", "systems"]`).
- `instructor`: `{ "id": 100, "displayName": "Dennis Ritchie", "headline": "...", "bio": "..." }` (public profile only; no email or timestamps).
- `outline`: `[{ "moduleTitle": "Module 1", "lessons": [{ "title": "Lesson 1" }] }]` (module and lesson titles only; no IDs, no contents, no resource data).

### Forbidden in Public Payloads
- Lesson body content or JSON structure
- Module, lesson, or resource IDs below course level
- Storage keys, bucket names, internal hostnames
- Presigned URLs in JSON or cached data
- Instructor emails, roles, or internal account state
- Readiness report data, word counts, course status/version

---

## Redis Caching and Invalidation Matrix

Cached objects are wrapped in `{ "schemaVersion": 1, "payload": ... }`. Any schema version mismatch is treated as a cache miss.

| Cache Key | TTL | Invalidation Trigger |
| --- | --- | --- |
| `course:preview:{id}` | 10m (`discovery.cache.preview-ttl`) | Publish / Unpublish of course; Content/resource change flush; Stats recorded for course. |
| `course:structure:{id}` | 10m (`discovery.cache.preview-ttl`) | Publish / Unpublish of course; Content/resource change flush. |
| `courses:popular` | 2m (`discovery.cache.popular-ttl`) | Publish / Unpublish of any course; Stats recorded for any course. |
| `categories:all` | 10m (`discovery.cache.categories-ttl`) | Publish / Unpublish of any course. |

All cache operations use `SafeCache` with short timeouts and single-flight lock-free loading. If Redis is down, all requests pass through to MySQL seamlessly.

---

## Stats Extension Point (`CourseStatsRecorder`)

For Phase 6 (Enrollment & Completion) and Phase 8 (Ratings), discovery provides a push interface:

```java
package com.peergeek.discovery;

import java.math.BigDecimal;

public interface CourseStatsRecorder {
    void recordEnrollmentStats(long courseId, int enrollmentCount, int completionCount);
    void recordRatingStats(long courseId, BigDecimal ratingAverage, int ratingCount);
}
```

### Integration Guidelines for Phase 6 & Phase 8
1. **Push Absolute Values**: Compute new total values and call recorder methods after committing state changes.
2. **Idempotent**: Calling multiple times with the same values produces identical database state.
3. **Non-blocking / Cache-safe**: Recorder updates `course_public_stats` and safely evicts `courses:popular` and `course:preview:{id}`.

#### Code Example for Phase 6:
```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onEnrollmentChanged(EnrollmentChangedEvent event) {
    int totalEnrolled = enrollmentRepository.countByCourseId(event.courseId());
    int totalCompleted = enrollmentRepository.countCompletedByCourseId(event.courseId());
    courseStatsRecorder.recordEnrollmentStats(event.courseId(), totalEnrolled, totalCompleted);
}
```

#### Code Example for Phase 8:
```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onRatingChanged(RatingChangedEvent event) {
    BigDecimal avgRating = reviewRepository.calculateAverageRating(event.courseId());
    int count = reviewRepository.countByCourseId(event.courseId());
    courseStatsRecorder.recordRatingStats(event.courseId(), avgRating, count);
}
```

---

## Replacing the Search Implementation

The search repository is isolated behind the interface `CourseSearchRepository`:

```java
public interface CourseSearchRepository {
    Page<CourseCardDto> search(CourseSearchCriteria criteria, Pageable pageable);
}
```

Currently, `JdbcCourseSearchRepository` provides case- and accent-insensitive substring matching using MySQL's `utf8mb4_0900_ai_ci` collation. To migrate to OpenSearch or Elasticsearch at larger scale, implement `CourseSearchRepository` with an Elasticsearch client and bind that bean.
