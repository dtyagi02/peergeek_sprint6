# Document 3: Dependency and Coupling Analysis

## 1. Monolithic Dependency Graph

Our automated static analysis of all Java classes in `backend/src/main/java/com/peergeek` identified significant cross-package coupling. Below is the comprehensive dependency map representing direct service and repository injections across monolithic modules:

```text
[admin]
  ├── UserRepository (user)
  ├── CourseRepository (course)
  ├── EnrollmentRepository (enrollment)
  ├── ReportRepository (report)
  ├── AuditLogRepository (audit)
  ├── UserService (user)
  ├── ReportService (report)
  ├── RefreshTokenRepository (auth)
  └── AuditService (audit)

[certificate]
  ├── EnrollmentRepository (enrollment)
  ├── ProgressService (enrollment)
  ├── CourseStructureQueryService (course)
  ├── UserRepository (user)
  ├── AssignmentRepository (assignment)
  ├── SubmissionRepository (submission)
  ├── ObjectStorageService (file)
  └── NotificationService (notification)

[doubtsession]
  ├── CourseRepository (course)
  ├── UserRepository (user)
  ├── EnrollmentRepository (enrollment)
  ├── WalletService (wallet)
  ├── NotificationService (notification)
  └── LiveKitService (internal)

[enrollment]
  ├── CourseStructureQueryService (course)
  ├── UserService (user)
  ├── ResourceRepository (file)
  ├── ResourceAccessService (file)
  ├── ObjectStorageService (file)
  └── AuditService (audit)

[assignment & submission]
  ├── CourseStructureQueryService (course)
  ├── EnrollmentQueryService (enrollment)
  ├── ObjectStorageService (file)
  ├── UserService (user)
  └── AuditService (audit)

[discovery & analytics]
  ├── CourseStructureQueryService (course)
  ├── CategoryRepository (course)
  ├── UserService (user)
  ├── EnrollmentRepository (enrollment)
  ├── LessonProgressRepository (enrollment)
  ├── ReviewRepository (review)
  ├── AssignmentRepository (assignment)
  └── SubmissionRepository (submission)

[referral]
  ├── UserRepository (user)
  ├── WalletService (wallet)
  └── NotificationService (notification)

[payment]
  ├── WalletService (wallet)
  ├── UserRepository (user)
  └── RazorpayService (internal)
```

---

## 2. Detailed Coupling Analysis by Architectural Pattern

### 2.1. The "God Entity" Coupling Hub: `UserEntity`
- **Discovered Pattern**: Almost every service directly injects `UserRepository` or declares a foreign key to `users(id)`.
  - `CourseEntity` has `@ManyToOne UserEntity owner`.
  - `WalletEntity` has `@ManyToOne UserEntity user`.
  - `DoubtSessionEntity` has `@ManyToOne UserEntity instructor` and `student`.
  - `DirectConversationEntity` has `@ManyToOne UserEntity userOne` and `userTwo`.
  - `ReferralEntity` has `@ManyToOne UserEntity inviter` and `referredUser`.
- **Microservices Impact**: In a distributed system, foreign keys cannot cross service database boundaries. Having `courses` or `wallets` depend on a JPA object reference to `UserEntity` creates a hard compile-time and runtime dependency on Identity Service's database.
- **Decoupling Strategy**:
  - Replace all entity object associations `UserEntity user` with primitive foreign keys: `Long userId`.
  - Microservices retrieve user details (e.g., student name, email, avatar URL) either from:
    1. The authenticated JWT claim token passed in the `X-User-*` headers by the API Gateway.
    2. Local read-model projections populated via `UserRegistered` or `UserProfileUpdated` Kafka events.
    3. Lightweight synchronous REST calls (`GET /api/v1/users/{id}/public`) when real-time freshness is critical.

### 2.2. Course Structure Inspection: `CourseStructureQueryService`
- **Discovered Pattern**: `CourseStructureQueryService` is injected across 9 different packages:
  - `enrollment` uses it to validate lesson existence and count total lessons for course completion percentage.
  - `assignment` and `submission` use it to verify whether an assignment belongs to a published course.
  - `certificate` uses it to fetch the official course title and instructor name for PDF generation.
  - `readiness` uses it to inspect modules and lessons.
  - `discovery` uses it to build full-text search indexing documents.
  - `report` and `review` use it to verify course ownership.
- **Microservices Impact**: This represents synchronous read coupling. If Course Service restarts or becomes slow, enrollment progress, assignment grading, and discovery index reconciliation are blocked.
- **Decoupling Strategy**:
  - **For Learning & Certificate**: Course publishing emits `CoursePublishedEvent` containing the immutable course structure snapshot (course ID, title, instructor name, module count, lesson list). Learning Service caches this snapshot in its local database (`course_structure_cache`).
  - **For Discovery & Analytics**: Consume `CoursePublishedEvent` and `CourseContentChangedEvent` via Kafka to asynchronously maintain the public catalog search projection.

### 2.3. Cross-Domain Transaction Coupling: Doubt Sessions & Wallet
- **Discovered Pattern**: In `DoubtSessionService.java`:
  ```java
  @Transactional
  public DoubtSessionDto bookSession(Long studentId, BookSessionRequest request) {
      ...
      // Direct synchronous in-memory call to another module
      walletService.reserveFunds(studentId, hourlyRate, "DOUBT_SESSION", session.getId(), ...);
      ...
  }
  ```
  And upon session completion:
  ```java
  @Transactional
  public void settleSessionPayment(Long sessionId) {
      ...
      walletService.settleSessionPayment(studentId, instructorId, ...);
      ...
  }
  ```
- **Microservices Impact**:
  - `DoubtSessionService` and `WalletService` currently share the **exact same database transaction**. If database locking or wallet constraints fail, the doubt session rollback is tightly bound.
  - When extracted into separate microservices, this transaction must be split across the network.
- **Decoupling Strategy**:
  - **Step 1 (Booking - Synchronous Command)**: Doubt Session Service makes a synchronous REST call with idempotency key: `POST /api/v1/wallet/reservations` with timeout (2 seconds). If reservation succeeds, session status transitions to `REQUESTED`. If it fails (e.g. insufficient funds), booking is rejected immediately.
  - **Step 2 (Completion - Saga / Eventual Consistency)**: When the WebRTC session finishes, Doubt Session Service marks session `COMPLETED` and publishes `DoubtSessionCompletedEvent` to Kafka. Wallet Service consumes this event, releases the reservation, and credits the instructor's wallet idempotently.

### 2.4. Cross-Domain Aggregate Coupling: Certificate Eligibility
- **Discovered Pattern**: `CertificateEligibilityService` in `com.peergeek.certificate` queries:
  1. `EnrollmentRepository` &rarr; verifies active student enrollment.
  2. `ProgressService` &rarr; verifies 100% lesson progress.
  3. `AssignmentRepository` &rarr; fetches all assignments in the course.
  4. `SubmissionRepository` &rarr; verifies passing grade on every mandatory assignment.
- **Microservices Impact**: This spans 4 distinct domain concepts (Enrollment, Progress, Assignments, Submissions).
- **Decoupling Strategy**:
  - By keeping `enrollment`, `assignment`, `submission`, and `certificate` inside the **Learning & Assessment Service** (as proposed in Document 2), this query remains a fast, local transactional query within the same database, eliminating any distributed multi-service coordination during Phase 0–5!

### 2.5. Administrative Orchestration Coupling: `AdminDashboardService`
- **Discovered Pattern**: `AdminDashboardService` injects 9 repositories from 6 different modules (`UserRepository`, `CourseRepository`, `EnrollmentRepository`, `ReportRepository`, `AuditLogRepository`, `RefreshTokenRepository`).
- **Microservices Impact**: Represents an "anti-pattern" where a single service reaches into every database table to gather statistics.
- **Decoupling Strategy**:
  - Admin dashboard metrics are moved to the **Analytics & Discovery Service**, which already consumes platform events (signups, course publishes, enrollments, reports) and maintains high-speed pre-aggregated metric counters in Redis and MySQL.
  - Administrative command actions (e.g. `blockUser`, `unpublishCourse`) are routed via the API Gateway directly to the owning microservice (`Identity Service` and `Course Service`) with `ROLE_ADMIN` validation.

---

## 3. Coupling Matrix and Resolution Plan

| Source Module | Target Module | Current Mechanism | Proposed Microservices Resolution |
| :--- | :--- | :--- | :--- |
| `doubtsession` | `wallet` | Direct service call in `@Transactional` | Synchronous REST for reservation; Asynchronous Kafka event for completion settlement. |
| `certificate` | `course` | `CourseStructureQueryService` call | Course snapshot cached locally in Learning DB via `CoursePublishedEvent`. |
| `certificate` | `notification` | Direct `NotificationService.create()` | Kafka event `CertificateIssuedEvent` &rarr; Notification Service. |
| `referral` | `wallet` | `WalletService.credit()` | Kafka event `ReferralQualifiedEvent` &rarr; Wallet Service credits PGC. |
| `community` | `user` | Entity JPA `@ManyToOne` join | Stored `Long userId`; User display name and avatar fetched via JWT headers or read cache. |
| `review` | `enrollment` | `EnrollmentRepository.findByCourseIdAndUserId` | Synchronous REST check (`GET /api/v1/learning/enrollment-check`) before submitting review. |
| `discovery` | `course` | Direct DB index reads and queries | Event-driven CQRS: Course Service emits events &rarr; Discovery Service updates search index. |
| `payment` | `wallet` | Local service injection | Co-located in **Wallet & Payment Service**; local ACID transaction preserved. |

