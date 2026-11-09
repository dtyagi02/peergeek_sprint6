# Phase 5 — Supplemental Verification & Remediation Final Report

**Service Extracted:** `course-learning-assessment-service` (Port: `8090`)  
**Service Database:** `peergeek_learning` (MySQL 8.0.46)  
**Monolith Database:** `peergeek` (MySQL 8.0.46)  
**API Gateway:** Port `8081`  
**Object Storage:** MinIO / S3 (`peergeek20-minio-1`, Port `9000`, bucket: `peergeek`)  
**Message Broker:** Apache Kafka 3.7.0 (`peergeek20-kafka-1`, Port `9092`)  
**Downstream Consumer:** `notification-service` (Port `8087`)  
**Frontend Test Suite:** Vitest (242 / 242 tests passing)  
**Backend Test Suite:** JUnit 5 / Spring Boot Test (15 / 15 tests passing)  
**Status:** **SUPPLEMENTAL VERIFICATION COMPLETE**  
**Date:** 2026-11-07  

---

## 1. Executive Summary & Verification Overview

Following the initial extraction of the Course, Learning & Assessment Service, this supplemental verification task investigated and closed all remaining architectural verification areas and storage integration requirements:

1. **Table Mapping Discrepancies Resolved**: Clarified canonical naming vs table names for all 7 disputed tables, documented excluded schemas, and completed a 2-pass idempotency run in a disposable MySQL test schema confirming zero duplicate records, zero key shifts, and preservation of target edits.
2. **Real MySQL Integration Tests Executed**: Transitioned testing from H2 to the live MySQL 8.0.46 engine (`MySqlIntegrationTest`). Verified all 23 domain tables created by Flyway, enrollment uniqueness constraint enforcement, transaction rollback, progress percentage calculation, MCQ server-side auto-grading and answer concealment, certificate enrollment uniqueness, and transactional outbox atomic commit/rollback.
3. **Kafka Delivery, Broker ACK & Idempotency Verified**: Verified synchronous broker ACK via `kafkaTemplate.send(...).get()` in `OutboxRelay`. Tested end-to-end publication and consumption across learning topics with `notification-service`, confirming zero deserialization errors, successful notification creation, RabbitMQ email dispatch, and strict duplicate deduplication via `eventKey`.
4. **Monolith Source-Code Dependency Scan Completed**: Executed an AST/symbol scan across `backend/src/main/java`. Categorized all occurrences across 6 cross-domain callers and 11 legacy preserved modules. Verified strict runtime datasource isolation (`peergeek` vs `peergeek_learning`) and complete API Gateway routing cutover.
5. **Storage & Lesson Resource Upload Subsystem Verified**: Audited the end-to-end upload flow (`createLessonUpload` -> presigned PUT -> MinIO persistence -> `confirm` with synchronous `head()` probe -> presigned GET for enrolled learners). Verified SHA-256 byte-level integrity, role-based authorization boundaries, strict payload validation (executable blacklist, mime whitelist, size limits), and verified physical existence of 100% of historical `READY` resources in MinIO.
6. **Zero Regression Confirmed**: 15/15 backend service tests and 242/242 frontend vitest tests pass cleanly.

---

## 2. Workstream Summary & Evidence Artifacts

### Workstream 1: Source-to-Target Table Mapping Discrepancies
- **Detailed Report:** [`PHASE_5_SUPPLEMENTAL_TABLE_MAPPING.md`](file:///c:/Users/DevanshTyagi/OneDrive%20-%20Academian%20India%20Private%20Limited/Desktop/PeerGeek2.0/PHASE_5_SUPPLEMENTAL_TABLE_MAPPING.md)
- **Reconciliation Highlights:**
  - `lesson_completions` -> `lesson_progress`: The monolith never had a table named `lesson_completions`; the physical table was always `lesson_progress`.
  - `assignment_submissions` -> `submissions`: Monolith table name was `submissions`.
  - `course_bookmarks` & `lesson_bookmarks` -> `bookmarks`: Unified single-table design with `target_type` discriminator (`COURSE` vs `LESSON`).
  - `course_readiness_reports` & `course_readiness_issues` -> `readiness_runs` & `readiness_issues`: Preserved 1:N run-to-issue hierarchy.
  - `user_profiles` -> `learning_user_profiles`: Decoupled read-model projection omitting sensitive passwords and tokens.
- **Idempotency Proof:** Two-pass migration executed on `peergeek_learning_idempotency_test`. Target Course 1 title modified between runs. Pass 2 confirmed identical record counts, 0 duplicate keys, and preserved target title modification. Test schema dropped cleanly after test.

---

### Workstream 2: MySQL-Backed Integration Testing
- **Detailed Report:** [`PHASE_5_MYSQL_INTEGRATION_VERIFICATION.md`](file:///c:/Users/DevanshTyagi/OneDrive%20-%20Academian%20India%20Private%20Limited/Desktop/PeerGeek2.0/PHASE_5_MYSQL_INTEGRATION_VERIFICATION.md)
- **Database Engine:** MySQL 8.0.46 on `host.docker.internal:3306`
- **Schema Tested:** `peergeek_learning_test` (Flyway migration version 1.001)
- **Test Class:** `com.peergeek.courselearning.MySqlIntegrationTest` (6 / 6 Tests Passed)
  1. `verifyMySqlAndFlywayTables`: PASSED (Engine 8.0.46, all 23 tables confirmed).
  2. `verifyEnrollmentAndDuplicatePrevention`: PASSED (Duplicate enrollment triggered `SQL Error: 1062, SQLState: 23000`; rollback confirmed empty).
  3. `verifyLearningProgressCalculation`: PASSED (50% -> 100% recalculation, `completed_at` timestamp).
  4. `verifyMcqAutoGradingAndConcealment`: PASSED (Correct answers concealed from student, server evaluated 100.00 marks).
  5. `verifyCertificateUniqueness`: PASSED (Duplicate certificate for enrollment failed with unique constraint violation).
  6. `verifyTransactionalOutboxAtomicityAndRollback`: PASSED (Atomic commit persisted both course and outbox event; error rolled back both).

---

### Workstream 3: Kafka Delivery, Broker ACK & Downstream Consumers
- **Detailed Report:** [`PHASE_5_KAFKA_DELIVERY_VERIFICATION.md`](file:///c:/Users/DevanshTyagi/OneDrive%20-%20Academian%20India%20Private%20Limited/Desktop/PeerGeek2.0/PHASE_5_KAFKA_DELIVERY_VERIFICATION.md)
- **Outbox Relay Correctness:** Verified synchronous broker acknowledgement:
  ```java
  kafkaTemplate.send(event.getTopic(), event.getEventId(), event.getPayload()).get();
  ```
- **Live Consumer Verification (`notification-service`):**
  - **`peergeek.course.course-published.v1`**: Consumed successfully; created Notification #79 of type `COURSE_PUBLISHED`.
  - **`peergeek.learning.submission-graded.v1`**: Consumed successfully; created Notification #80 of type `SUBMISSION_GRADED`.
  - **`peergeek.learning.course-completed.v1`**: Consumed successfully; created Notification #81 of type `COURSE_COMPLETED`, triggered RabbitMQ email queue task, and dispatched completion email.
- **Idempotency & Deduplication Proof:**
  - Duplicate `SUBMISSION_GRADED` payload produced:  
    `Duplicate notification ignored for user 42 with eventKey SUBMISSION_GRADED:9992`.
  - Duplicate `COURSE_COMPLETED` payload produced:  
    `Duplicate notification ignored for user 42 with eventKey COURSE_COMPLETED:9991:42`.
  - Database count in `peergeek_notification.notifications` verified: zero duplicate rows created.

---

### Workstream 4: Complete Monolith Source-Code Dependency Scan
- **Detailed Report:** [`PHASE_5_MONOLITH_DEPENDENCY_AUDIT.md`](file:///c:/Users/DevanshTyagi/OneDrive%20-%20Academian%20India%20Private%20Limited/Desktop/PeerGeek2.0/PHASE_5_MONOLITH_DEPENDENCY_AUDIT.md)
- **Scope:** Complete automated symbol scan of `backend/src/main/java/`.
- **Classification Findings:**
  - **Cross-Domain Callers (6 modules):** `admin` (dashboard counts), `discovery` (thumbnail URLs), `doubtsession` (course/enrollment check), `report` (course audit reference), `review` (active enrollment check), `user` (profile stats).
  - **Legacy Monolith Packages (11 modules):** `course`, `module`, `lesson`, `enrollment`, `assignment`, `submission`, `certificate`, `bookmark`, `note`, `readiness`, `file`. Preserved in monolith as fallback / rollback code per migration preflight rules.
- **Isolation Verification:**
  - Monolith: `spring.datasource.url=.../peergeek`
  - Course Service: `spring.datasource.url=.../peergeek_learning`
  - Gateway: Intercepts all `/api/v1/courses/**`, `/api/v1/categories/**`, `/api/v1/instructor/**`, `/api/v1/learning/**`, `/api/v1/certificates/**`, `/api/v1/admin/certificates/**`, `/api/v1/admin/courses/**`, `/api/v1/bookmarks/**` and routes to `http://localhost:8090`. No learning traffic reaches the monolith.

---

### Workstream 5: Lesson Resource Upload and Storage Verification
- **Detailed Report:** [`PHASE_5_RESOURCE_STORAGE_VERIFICATION.md`](file:///c:/Users/DevanshTyagi/OneDrive%20-%20Academian%20India%20Private%20Limited/Desktop/PeerGeek2.0/PHASE_5_RESOURCE_STORAGE_VERIFICATION.md)
- **Test Suite:** `com.peergeek.courselearning.ResourceStorageIntegrationTest` (4 / 4 Tests Passed)
- **Verification Results:**
  - `verifyEndToEndResourceUploadAndRetrieval`: PASSED. Full presigned PUT -> upload `phase5_audit_spec.pdf` -> `confirm` via MinIO `head()` check -> `READY` status -> learner presigned GET retrieval. SHA-256 matched uploaded bytes.
  - `verifyAccessControlEnforcement`: PASSED. Non-owning instructor rejected with `ForbiddenException` (HTTP 403); unenrolled student rejected with `EnrollmentRequiredException` (HTTP 403).
  - `verifyFileValidation`: PASSED. Executable files (`.exe`, `.bat`, `.sh`, `.cmd`), invalid mime-types, negative sizes, and oversized PDFs (>100MB) rejected with `BadRequestException` (HTTP 400).
  - `verifyHistoricalStorageObjectsExist`: PASSED. 100% of the 9 historical `READY` resources and historical certificates physically exist in MinIO container `/bitnami/minio/data/peergeek/`.

---

## 3. Remediations & Fixes Applied

1. **Transactional Outbox Broker Acknowledgement**:
   - Confirmed and verified synchronous `.get()` broker ACK in `OutboxRelay.java`.
   - Guaranteed atomic outbox commit with business transaction via `@Transactional(propagation = Propagation.REQUIRED)`.
2. **Kafka Payload Interoperability**:
   - Added `instructorId` alias alongside `ownerId` in `CourseService.java` for `CoursePublished` and `CourseUnpublished` events.
   - Added `learnerId` alias alongside `userId` in `InstructorGradingService.java` and `LearnerSubmissionService.java` for `SubmissionGraded` events.
   - Eliminated deserialization / null identifier issues in `NotificationKafkaConsumer`.
3. **Storage Security & Payload Safeguards**:
   - Implemented `validateLessonUpload` and `validateThumbnailUpload` in `ResourceUploadService.java`, enforcing strict blacklisting of executable and script files, content-type verification, and file size limits (5MB thumbnail, 50MB document, 100MB PDF, 500MB video).
4. **MySQL Integration Test Profile**:
   - Added `application-mysql-test.yml` pointing to disposable `peergeek_learning_test` on MySQL 8.0.46 with Flyway enabled.
   - Added comprehensive integration test suites verifying live MySQL dialect and MinIO object storage.

---

## 4. Test Execution Summary

### Backend Service Tests
```text
[INFO] -------------------------------------------------------
[INFO]  T E S T S
[INFO] -------------------------------------------------------
[INFO] Running com.peergeek.courselearning.AssessmentGradingTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.peergeek.courselearning.CertificateEligibilityTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.peergeek.courselearning.CourseLearningApplicationTests
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.peergeek.courselearning.EnrollmentProgressTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.peergeek.courselearning.ResourceStorageIntegrationTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.peergeek.courselearning.MySqlIntegrationTest
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] Results:
[INFO] 
[INFO] Tests run: 15, Failures: 0, Errors: 0, Skipped: 0
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

### Frontend Vitest Suite
```text
Test Files  30 passed (30)
     Tests  242 passed (242)
  Duration  112.49s
```

---

## 5. Architectural Acceptance Declaration

All five target verification areas have been completed with empirical evidence, passing automated test suites, and dedicated documentation. The Course, Learning & Assessment Service (`course-learning-assessment-service`) operates with independent schema ownership, reliable event messaging, secure storage integration, and strict isolation from the monolithic backend.

**Status: SUPPLEMENTAL VERIFICATION COMPLETE**

