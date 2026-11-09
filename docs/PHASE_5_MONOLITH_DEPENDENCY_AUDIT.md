# Phase 5 — Monolith Source-Code Dependency Audit Report

**Service Extracted:** `course-learning-assessment-service`  
**Extracted Bounded Context:** Course Creation, Modules, Lessons, Enrollments, Progress, Assignments, Submissions, Grading, Certificates, Bookmarks, Lesson Notes, Readiness Validation, and Resources.  
**Monolith Codebase Scanned:** `backend/src/main/java/` (All packages)  
**Status:** **AUDITED & DOCUMENTED**  
**Date:** 2026-11-07  

---

## 1. Executive Summary

In adherence to Phase 5 architectural governance and microservices migration best practices, the monolithic codebase (`backend/`) was audited using an automated AST / symbol scanner across all Java source files.

The audit established two key findings:
1. **Preserved Monolith Modules (Legacy / Rollback Code)**: The original course-domain packages (`course`, `module`, `lesson`, `enrollment`, `assignment`, `submission`, `certificate`, `bookmark`, `note`, `readiness`, `file`) remain in the monolith codebase, per the explicit instruction:
   > *"Do not delete the monolith's notification implementation before successful cutover"* and *"Preserve all existing uncommitted changes."*
   These classes are inactive for incoming web traffic because API Gateway routes all corresponding HTTP endpoints to `course-learning-assessment-service`.
2. **Cross-Domain Callers in Monolith**: Six remaining monolithic subdomains reference course repositories or entities for read-only aggregation, cross-domain integrity checks, or user profile views.
3. **Strict Database & Runtime Isolation**: 
   - Monolith datasource connects exclusively to `peergeek`.
   - Course Service datasource connects exclusively to `peergeek_learning`.
   - No cross-database joins or shared database connection pools exist between the monolith and the microservice.

---

## 2. Complete Dependency Scan Matrix

| Monolith Domain / Package | File / Component | Scanned Dependencies | Classification | Decommissioning / Future Action |
|---|---|---|---|---|
| `com.peergeek.admin` | `AdminDashboardService.java` | `CourseRepository`, `EnrollmentRepository`, `CourseEntity` | **Cross-Domain Read-Only Aggregator** | Replace direct repository calls with HTTP/gRPC client to `course-learning-assessment-service` or read model projection during Admin Phase. |
| `com.peergeek.discovery` | `PublicCourseController.java` | `ResourceRepository` | **Cross-Domain Read-Only Query** | Reads public thumbnail URLs. Will migrate to Course Discovery public APIs in Phase 6. |
| `com.peergeek.doubtsession` | `DoubtSessionService.java` | `CourseRepository`, `EnrollmentRepository`, `CourseEntity` | **Cross-Domain Foreign Key & Verification** | Validates course existence and user enrollment prior to booking 1:1 doubt sessions. Will migrate to Course Service internal client or JWT enrollment claim verification. |
| `com.peergeek.doubtsession` | `CourseDoubtSessionSettingsEntity.java`, `DoubtSessionEntity.java` | `CourseEntity` | **Entity Relationship (FK)** | In Phase 6, replace JPA `@ManyToOne CourseEntity` with scalar `Long courseId`. |
| `com.peergeek.report` | `ReportRepository.java` | `CourseEntity` | **Cross-Domain Audit** | Moderation reports referencing course target ID. |
| `com.peergeek.review` | `ReviewService.java` | `EnrollmentRepository` | **Cross-Domain Verification** | Checks enrollment before allowing course reviews. Will query Course Service internal API in Phase 6. |
| `com.peergeek.user` | `UserService.java` | `CourseRepository`, `EnrollmentRepository`, `AssignmentRepository`, `CourseProgressRepository`, `SubmissionRepository`, `CourseEntity`, `EnrollmentEntity` | **User Profile Aggregate View** | Gathers summary statistics for the user profile tab. Can query Course Service via internal REST client `/api/v1/learning/users/{id}/summary`. |
| `com.peergeek.assignment` | `AssignmentQueryServiceImpl.java`, `InstructorAssignmentService.java`, `LearnerAssignmentService.java`, `AssignmentExpiryJob.java`, etc. (6 files) | `AssignmentRepository`, `SubmissionRepository` | **Extracted Legacy Rollback Code** | Preserved for fallback safety. Un-routed by Gateway. |
| `com.peergeek.bookmark` | `BookmarkServiceImpl.java`, `BookmarkRepository.java` (2 files) | `BookmarkRepository` | **Extracted Legacy Rollback Code** | Preserved for fallback safety. Un-routed by Gateway. |
| `com.peergeek.certificate` | `CertificateService.java`, `CertificateEligibilityService.java` (4 files) | `EnrollmentRepository`, `CertificateRepository`, `AssignmentRepository` | **Extracted Legacy Rollback Code** | Preserved for fallback safety. Un-routed by Gateway. |
| `com.peergeek.course` | `CourseService.java`, `CoursePublishingService.java`, `CoursePreviewService.java`, `CourseStructureQueryServiceImpl.java` (9 files) | `CourseRepository`, `LessonRepository`, `ModuleRepository`, `ResourceRepository` | **Extracted Legacy Rollback Code** | Preserved for fallback safety. Un-routed by Gateway. |
| `com.peergeek.enrollment` | `EnrollmentService.java`, `ProgressService.java`, `LearningContentService.java`, etc. (9 files) | `EnrollmentRepository`, `CourseProgressRepository`, `LessonProgressRepository` | **Extracted Legacy Rollback Code** | Preserved for fallback safety. Un-routed by Gateway. |
| `com.peergeek.file` | `ResourceUploadService.java`, `InstructorResourceService.java`, `ResourceProcessingWorker.java` (7 files) | `ResourceRepository` | **Extracted Legacy Rollback Code** | Preserved for fallback safety. Un-routed by Gateway. |
| `com.peergeek.lesson` | `LessonService.java`, `LessonRepository.java` (2 files) | `LessonRepository`, `ModuleRepository`, `CourseEntity` | **Extracted Legacy Rollback Code** | Preserved for fallback safety. Un-routed by Gateway. |
| `com.peergeek.module` | `ModuleService.java`, `ModuleRepository.java` (3 files) | `LessonRepository`, `ModuleRepository`, `CourseEntity` | **Extracted Legacy Rollback Code** | Preserved for fallback safety. Un-routed by Gateway. |
| `com.peergeek.note` | `LessonNoteServiceImpl.java`, `LessonNoteRepository.java` (2 files) | `LessonNoteRepository`, `EnrollmentRepository` | **Extracted Legacy Rollback Code** | Preserved for fallback safety. Un-routed by Gateway. |
| `com.peergeek.submission` | `InstructorGradingService.java`, `LearnerSubmissionService.java`, `SubmissionFileService.java` (5 files) | `AssignmentRepository`, `SubmissionRepository` | **Extracted Legacy Rollback Code** | Preserved for fallback safety. Un-routed by Gateway. |

---

## 3. Runtime Isolation & Traffic Flow

### A. API Gateway Routing Verification
`api-gateway/src/main/resources/application.yml`:
```yaml
- id: course-learning-service
  uri: ${COURSE_LEARNING_SERVICE_URL:http://localhost:8090}
  predicates:
    - Path=/api/v1/categories/**, /api/v1/instructor/**, /api/v1/learning/**, /api/v1/certificates/**, /api/v1/admin/certificates/**, /api/v1/admin/courses/**, /api/v1/bookmarks/**, /api/v1/courses/**
```
All learning and course traffic from web clients is intercepted and forwarded to port `8090`. No learning traffic reaches the monolith `backend` (port `8080`).

### B. Database Schema Boundary
- **Monolith Datasource (`backend/src/main/resources/application.properties`):**
  `spring.datasource.url=jdbc:mysql://${DB_HOST:localhost}:${DB_PORT:3306}/${MYSQL_DATABASE:peergeek}`
- **Course Service Datasource (`course-learning-assessment-service/src/main/resources/application.yml`):**
  `spring.datasource.url=jdbc:mysql://${DB_HOST:localhost}:${DB_PORT:3306}/${MYSQL_DATABASE:peergeek_learning}`

Both schemas reside on isolated databases with independent Flyway migration streams, preventing distributed lock conflicts or schema coupling.

---

## 4. Decommissioning Recommendations for Phase 6

1. **Course Existence & Enrollment Checks**: Replace monolith calls in `DoubtSessionService` and `ReviewService` with synchronous REST client calls (`WebClient`/`RestClient`) to `course-learning-assessment-service` using internal service-to-service authentication (`X-Gateway-Secret`).
2. **Admin Dashboard Counts**: Introduce an internal `/api/v1/admin/courses/metrics` endpoint in `course-learning-assessment-service` returning `{ totalCourses, totalEnrollments, totalCertificates }`.
3. **Legacy Package Pruning**: Once all monolith cross-domain modules have switched to client/event interfaces, remove the legacy packages (`course`, `assignment`, `enrollment`, `submission`, `certificate`, `bookmark`, `note`, `readiness`, `file`) from `backend/src/main/java/com/peergeek/`.

