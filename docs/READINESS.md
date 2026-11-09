# Course readiness

Readiness checks are instructor-owned, asynchronous checks against the current
course structure, authored lesson content, resource metadata, and stored
objects. A report is immutable once completed. The server calculates a
SHA-256 fingerprint over course metadata, module/lesson structure, lesson
documents and versions, expected resources, and resource state/checksums.
Publishing requires the latest completed report to have the current fingerprint,
no blocking issues, and to be younger than `READINESS_REPORT_MAX_AGE`.

## API

All routes require an authenticated course owner. A non-owner receives the
standard not-found response.

| Method | Path | Result |
| --- | --- | --- |
| `POST` | `/api/v1/instructor/courses/{courseId}/readiness/runs` | Starts a run and returns `202 Accepted` with the run ID/status. |
| `GET` | `/api/v1/instructor/courses/{courseId}/readiness/runs/{runId}` | Returns current state, counts, failure message, timestamps, fingerprint, and issues. |

Run states are `RUNNING`, `COMPLETED`, and `FAILED`. Every issue contains a
stable `ruleCode`, `severity` (`BLOCKING_ERROR` or `WARNING`), scope and
optional module/lesson/resource identifiers, a safe message, and a fix hint.
The report intentionally excludes object keys and signed internal URLs.
Repeated starts are bounded by the per-course cooldown, per-user in-flight
limit, bounded worker queue, and retained-run limit. A timeout marks a run
failed. Completed issue output is capped by `READINESS_REPORT_MAX_ISSUES`.

## Checks

Fast checks cover required course metadata, active category, difficulty and
topics, module/lesson presence, lesson content, expected-resource counts, and
resources that have not reached `READY`.

Deep checks inspect S3 object existence and size, verify SHA-256 for resources
up to the configured rehash size, parse bounded PDF files with PDFBox, inspect
OOXML package structure and expansion limits, inspect image dimensions without
decoding full images, probe videos through the internal resource URL and
`ffprobe`, and validate external links. Video probe results are cached by
resource ID and checksum. Link results are cached by URL hash and TTL.

The link checker permits only HTTP/HTTPS URLs on configured ports. It rejects
credentials/fragments, resolves DNS before connecting, rejects private,
loopback, link-local, multicast, and reserved addresses unless explicitly
allowed, and connects to the exact validated address (DNS-pinned). Redirects
are followed manually and each target is revalidated and pinned independently.
TLS hostname verification and SNI remain enabled. It sends a bounded `HEAD`
request, falling back to `GET` with a one-byte range when the server rejects
`HEAD`; response bodies are not consumed.
`READINESS_LINKS_ALLOW_PRIVATE_HOSTS=true` is rejected during startup whenever
the `prod` profile is active.

## Environment variables

Spring relaxed binding reads these variables. Defaults below are suitable for
local development; production should keep private-host link checks disabled.

| Variable | Default |
| --- | --- |
| `READINESS_REPORT_MAX_AGE` | `24h` |
| `READINESS_REPORT_MAX_ISSUES` | `500` |
| `READINESS_REPORT_COURSE_DESCRIPTION_WARNING_MIN_LENGTH` | `50` |
| `READINESS_RUNS_TIMEOUT` | `10m` |
| `READINESS_RUNS_COURSE_COOLDOWN` | `10s` |
| `READINESS_RUNS_MAX_CONCURRENT_PER_USER` | `3` |
| `READINESS_RUNS_THREADS` | `4` |
| `READINESS_RUNS_QUEUE_CAPACITY` | `32` |
| `READINESS_RUNS_RETENTION_PER_COURSE` | `10` |
| `READINESS_INTEGRITY_MAX_REHASH_BYTES` | `52428800` |
| `READINESS_VIDEO_PROBE_REQUIRED` | `true` |
| `READINESS_VIDEO_TIMEOUT` | `30s` |
| `READINESS_PDF_MAX_BYTES` | `52428800` |
| `READINESS_OOXML_MAX_ENTRIES` | `10000` |
| `READINESS_OOXML_MAX_EXPANDED_BYTES` | `67108864` |
| `READINESS_OOXML_MAX_COMPRESSION_RATIO` | `100` |
| `READINESS_IMAGE_MAX_PIXELS` | `50000000` |
| `READINESS_LINKS_CACHE_TTL` | `15m` |
| `READINESS_LINKS_ALLOW_PRIVATE_HOSTS` | `false` |
| `READINESS_LINKS_ALLOWED_PORTS` | `80,443` |
| `READINESS_LINKS_CONNECT_TIMEOUT` | `3s` |
| `READINESS_LINKS_TOTAL_TIMEOUT` | `8s` |
| `READINESS_LINKS_MAX_REDIRECTS` | `3` |
| `READINESS_LINKS_MAX_CONCURRENT` | `8` |
| `READINESS_LINKS_MAX_CONCURRENT_PER_HOST` | `2` |
