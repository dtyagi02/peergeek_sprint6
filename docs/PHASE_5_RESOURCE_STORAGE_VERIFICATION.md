# Phase 5 — Lesson Resource Upload and Storage Verification Report

**Service:** `course-learning-assessment-service`  
**Object Storage:** MinIO / AWS S3 Compatible (`peergeek20-minio-1` on port 9000, bucket: `peergeek`)  
**Status:** **VERIFIED & PASSED (4 / 4 Scenarios Passing)**  
**Date:** 2026-11-07  

---

## 1. Executive Summary

As part of the PeerGeek Phase 5 Supplemental Verification, the storage subsystem of the Course, Learning & Assessment Service was audited and verified through automated end-to-end integration tests (`ResourceStorageIntegrationTest`).

The verification confirms:
1. **End-to-End Upload Lifecycle**: Instructors can request presigned PUT upload URLs, upload binary content to MinIO, and confirm the upload. Upload confirmation performs a synchronous `head()` probe against MinIO and transitions the resource from `PENDING` to `READY`.
2. **Data Integrity**: Retrieved files match the exact binary content and SHA-256 checksum of the uploaded file.
3. **Strict Authorization & Access Control**:
   - Instructors attempting to upload or confirm resources on courses they do not own are rejected with `ForbiddenException` (HTTP 403).
   - Unenrolled students attempting to request download URLs are rejected with `EnrollmentRequiredException` (HTTP 403).
   - Enrolled students receive fresh, time-limited presigned GET URLs.
4. **File & Type Validation**: Executable files (`.exe`, `.bat`, `.sh`, `.cmd`), invalid mime-types, negative sizes, and oversized payloads exceeding policy limits are rejected with `BadRequestException` (HTTP 400).
5. **Historical Storage Integrity**: 100% of historical `READY` resources and certificates physically exist in the MinIO storage volume.

---

## 2. End-to-End Lifecycle & Empirical Trace

```text
[Instructor Request] POST /api/v1/instructor/lessons/{lessonId}/resources/uploads
       │
       ▼
[ResourceUploadService] Validates course ownership, file extension, mime-type, and size limits
       │  Creates ResourceEntity (status = PENDING)
       ▼
[MinIO / S3 Presigner] Generates presigned PUT URL (TTL = 15 minutes)
       │
       ▼
[Upload Action] Client uploads bytes to presigned PUT URL
       │
       ▼
[Instructor Confirmation] POST /api/v1/instructor/resources/{resourceId}/confirm
       │
       ▼
[Storage Head Probe] storageService.head(objectKey) verifies object exists in MinIO
       │  Transitions status: PENDING -> READY
       ▼
[Learner Access] GET /api/v1/learning/resources/{resourceId}/url
       │
       ▼
[LearningContentService] Verifies active enrollment -> Generates presigned GET URL (TTL = 30 minutes)
```

### Empirical Test Execution Log
```text
[INFO] Running com.peergeek.courselearning.ResourceStorageIntegrationTest
2026-11-07T12:40:18.373Z  INFO [course-learning-assessment-service-test] : Starting ResourceStorageIntegrationTest using Java 21.0.12.1
2026-11-07T12:40:58.002Z  INFO [course-learning-assessment-service-test] : Initialized JPA EntityManagerFactory for persistence unit 'default'
2026-11-07T12:41:29.891Z  INFO [course-learning-assessment-service-test] : Uploaded object to bucket=peergeek, key=courses/1/lessons/1/782559e1-c10c-4a10-98b7-3eebb2114c05-phase5_audit_spec.pdf
Auditing 9 historical READY resources in MinIO storage bucket: peergeek
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 80.19 s -- in com.peergeek.courselearning.ResourceStorageIntegrationTest
[INFO] 
[INFO] Results:
[INFO] 
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

---

## 3. Security & Access Control Enforcement Matrix

| Scenario | Actor | Target Resource | Expected Result | Verified Result |
|---|---|---|---|---|
| Instructor Upload Initiation | Owning Instructor (ID: 501) | Lesson 1 of Course 1 | `201 CREATED` with Presigned PUT URL | **PASSED** |
| Non-Owning Instructor Upload | Foreign Instructor (ID: 502) | Lesson 1 of Course 1 | `403 FORBIDDEN` (`You do not own this course`) | **PASSED** |
| Non-Owning Instructor Confirm | Foreign Instructor (ID: 502) | Resource owned by 501 | `403 FORBIDDEN` (`You do not own this course`) | **PASSED** |
| Enrolled Learner URL Retrieval | Enrolled Learner (ID: 601) | Ready Resource on Course 1 | `200 OK` with Presigned GET URL | **PASSED** |
| Unenrolled Learner URL Retrieval | Unenrolled User (ID: 602) | Ready Resource on Course 1 | `403 FORBIDDEN` (`Enrollment is required`) | **PASSED** |

---

## 4. File Validation & Limit Safeguards

To prevent abuse, malware injection, and resource exhaustion, `ResourceUploadService` enforces strict upload validation:

| Rule Type | Test Case | Payload | Outcome |
|---|---|---|---|
| Executable Blacklist | `payload.exe` | Binary executable | **REJECTED**: `Executable and script files are not allowed` |
| Shell Script Blacklist | `script.sh` | Shell script (`application/x-sh`) | **REJECTED**: `Executable and script files are not allowed` |
| Oversized PDF | `huge_book.pdf` | 105 MB (> 100 MB limit) | **REJECTED**: `PDF file exceeds maximum limit of 100MB` |
| Mime-Type Mismatch | `test.pdf` | Content-type `text/plain` | **REJECTED**: `Mime type must be application/pdf for PDF resources` |
| Non-positive Size | `empty.pdf` | Size `-5` bytes | **REJECTED**: `File size must be positive` |

---

## 5. Historical MinIO Storage Audit

All 9 historical production resources with status `READY` in the database and historical completion certificates were inspected and verified against the live MinIO container:

| Resource ID | Course ID | Lesson ID | Type | Title | Object Key in MinIO | Physical Status |
|---|---|---|---|---|---|---|
| 93 | 1 | N/A | `IMAGE` | Capture001 | `courses/1/thumbnail/b81b1961-f197-4241-aa6c-0b5cd7a3431a` | **EXISTS** |
| 95 | 31 | 452 | `IMAGE` | Capture001 | `courses/31/lessons/452/aae79ea0-59e4-4bd3-a20a-b67fb42802f7` | **EXISTS** |
| 96 | 31 | 452 | `PDF` | PeerGeek Feature Spec JS | `courses/31/lessons/452/17e0926f-c396-4d81-b0f3-240b4b28b235` | **EXISTS** |
| 97 | 31 | 452 | `PDF` | Development Roadmap v2 | `courses/31/lessons/452/fe4353b9-d8b2-4dd6-acc3-360cf5557fb6` | **EXISTS** |
| 98 | 32 | 454 | `PDF` | Development Roadmap v2 | `courses/32/lessons/454/4c15c252-ac72-408c-83b9-6f006a122886` | **EXISTS** |
| 100 | 32 | N/A | `IMAGE` | Screenshot 2026-08-05 | `courses/32/thumbnail/f73c8abe-da8b-4e01-92cd-c6ad7c463fe8` | **EXISTS** |
| 101 | 31 | 452 | `VIDEO` | WIN_20261025_02_51_59_Pro | `courses/31/lessons/452/75e8bea7-0713-43cd-b293-fb2057057695` | **EXISTS** |
| 102 | 33 | 455 | `PDF` | Database Schema Overview | `courses/33/lessons/455/07ea1bf2-05c3-4537-b55c-208ff6d46654` | **EXISTS** |
| Cert 1 | 1 | N/A | `CERTIFICATE` | Completion Certificate | `certificates/1/PG-A864308065F9.pdf` | **EXISTS** |

---

## 6. Conclusion

The object storage integration for lesson resources, course thumbnails, and certificates is verified end-to-end. File validations, authorization boundaries, presigned URLs, SHA-256 byte integrity, and historical object durability are fully operational.

