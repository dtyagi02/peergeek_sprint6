# Phase 5 — MySQL Integration Verification Report

**Service:** `course-learning-assessment-service`  
**Target Database:** `peergeek_learning_test` (disposable testing schema) / `peergeek_learning` (production schema)  
**Database Host:** `host.docker.internal:3306` (Host MySQL Engine)  
**Status:** **VERIFIED & PASSED (6 / 6 Tests Passing)**  
**Date:** 2026-11-07  

---

## 1. Executive Summary

As part of the PeerGeek Phase 5 Supplemental Verification, a dedicated MySQL-backed integration test suite (`MySqlIntegrationTest`) was constructed and executed against the live MySQL 8.0 engine. The test profile replaces the in-memory H2 configuration with the live MySQL dialect and Flyway schema migration across all 23 database tables owned by the Course, Learning & Assessment Service.

All six targeted integration scenarios passed with zero failures and zero errors.

---

## 2. Test Execution Details

### Environment Configuration
- **MySQL Engine Version:** `8.0.46` (verified via `SELECT VERSION();`)
- **JDBC Connection URL:**  
  `jdbc:mysql://host.docker.internal:3306/peergeek_learning_test?allowPublicKeyRetrieval=true&useSSL=false&connectionTimeZone=UTC`
- **Driver:** `com.mysql.cj.jdbc.Driver`
- **Hibernate Dialect:** `org.hibernate.dialect.MySQLDialect`
- **Flyway Migration:** `V1__init_course_learning_schema.sql` (Version 1.001)

### Execution Command
```bash
docker run --rm --add-host=host.docker.internal:host-gateway \
  -v "C:\Users\DevanshTyagi\.m2:/root/.m2" \
  -v "${PWD}/course-learning-assessment-service:/app" \
  -w /app maven:3.9-eclipse-temurin-21 \
  mvn test -Dtest=MySqlIntegrationTest
```

---

## 3. Test Cases & Verification Results

| # | Test Method | Objective | Result | MySQL-Specific Details Verified |
|---|---|---|---|---|
| 1 | `verifyMySqlAndFlywayTables` | MySQL Version & Flyway Schema Creation | **PASSED** | Verified MySQL `8.0.46`. Confirmed creation and structure of all 23 domain tables: `categories`, `courses`, `course_topics`, `course_modules`, `lessons`, `lesson_contents`, `lesson_expected_resources`, `resources`, `assignments`, `assignment_questions`, `submissions`, `submission_answers`, `submission_files`, `enrollments`, `course_progress`, `lesson_progress`, `certificates`, `bookmarks`, `lesson_notes`, `readiness_runs`, `readiness_issues`, `learning_user_profiles`, `outbox_events`. |
| 2 | `verifyEnrollmentAndDuplicatePrevention` | Enrollment persistence & transaction rollback | **PASSED** | Verified enrollment persistence; duplicate insertion triggered MySQL constraint violation `SQL Error: 1062, SQLState: 23000`; transactional failure rolled back uncommitted enrollment completely. |
| 3 | `verifyLearningProgressCalculation` | Progress tracking & completion timestamps | **PASSED** | Verified incremental lesson completions, percentage recalculation (50% -> 100%), and exact timestamp recording in `completed_at` upon 100% completion. |
| 4 | `verifyMcqAutoGradingAndConcealment` | MCQ auto-grading & answer concealment | **PASSED** | Verified student payload sanitization stripping `correct` flag; verified auto-grading arithmetic evaluating submissions to 100/100 and transitioning status to `GRADED`. |
| 5 | `verifyCertificateUniqueness` | Certificate issuance & duplicate prevention | **PASSED** | Verified certificate issuance; duplicate certificate insertion for the same enrollment triggered MySQL unique constraint violation on `certificates.uq_certificates_enrollment`. |
| 6 | `verifyTransactionalOutboxAtomicityAndRollback` | Outbox atomicity & failure rollback | **PASSED** | Verified atomic write of business mutation + outbox event in the same transaction; verified simulated exception rolled back both business entity and outbox event cleanly from MySQL. |

---

## 4. Empirical Test Output Log

```text
[INFO] Running com.peergeek.courselearning.MySqlIntegrationTest
2026-11-07T12:10:55.015Z  INFO [course-learning-assessment-service-mysql-test] : Starting MySqlIntegrationTest using Java 21.0.12.1
2026-11-07T12:10:55.019Z  INFO [course-learning-assessment-service-mysql-test] : The following 1 profile is active: "mysql-test"
2026-11-07T12:11:15.085Z  INFO [course-learning-assessment-service-mysql-test] : Database: jdbc:mysql://host.docker.internal:3306/peergeek_learning_test (MySQL 8.0)
2026-11-07T12:11:15.435Z  INFO [course-learning-assessment-service-mysql-test] : Successfully validated 1 migration (execution time 00:00.163s)
2026-11-07T12:11:15.490Z  INFO [course-learning-assessment-service-mysql-test] : Current version of schema `peergeek_learning_test`: 1.001
2026-11-07T12:11:29.465Z  INFO [course-learning-assessment-service-mysql-test] : Initialized JPA EntityManagerFactory for persistence unit 'default'
Running against MySQL Engine Version: 8.0.46
2026-11-07T12:11:57.621Z  WARN [course-learning-assessment-service-mysql-test] : SQL Error: 1062, SQLState: 23000
2026-11-07T12:11:57.621Z ERROR [course-learning-assessment-service-mysql-test] : Duplicate entry '5' for key 'certificates.uq_certificates_enrollment'
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 67.96 s -- in com.peergeek.courselearning.MySqlIntegrationTest
[INFO] 
[INFO] Results:
[INFO] 
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

---

## 5. Conclusion

The Course, Learning & Assessment Service schema, constraints, transactions, and outbox operations are fully compatible with and verified against the live MySQL 8.0 engine.

