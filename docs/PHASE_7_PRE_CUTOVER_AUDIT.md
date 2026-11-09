# Phase 7 — Pre-Cutover Architecture & Monolith Inventory Audit

**Document Version:** 1.0.0  
**Audit Date:** 2026-11-08  
**Audit Scope:** Full repository scan of `backend/` (monolith), 6 extracted microservices (`identity-service`, `doubt-session-service`, `course-learning-assessment-service`, `community-service`, `wallet-payment-service`, `notification-service`), `api-gateway`, frontend (`frontend/`), and infrastructure.  
**Objective:** Establish definitive pre-cutover inventory, identify all remaining monolith capabilities, formulate authoritative final service ownership, and define the cutover and rollback strategies.

---

## 1. Executive Summary

PeerGeek has progressively extracted 6 independent microservices across Phases 1 through 6. All 6 microservices and the Spring Cloud API Gateway are currently operational, healthy, and communicating over the `peergeek20_default` Docker network.

However, the API Gateway (`api-gateway/src/main/resources/application.yml`) currently retains a wildcard fallback route:
```yaml
- id: monolith-api
  uri: ${MONOLITH_BASE_URL:http://localhost:8080}
  predicates:
    - Path=/api/v1/**
```
This audit examines every single monolith module, controller, repository, entity, database table, and frontend call to ensure that **100% of remaining business capabilities** are migrated to authoritative microservice owners before the monolith fallback is severed and the monolith is decommissioned.

---

## 2. Monolith Module & Capability Inventory

A complete source-code scan of `backend/src/main/java/com/peergeek` identified the following active packages and capabilities:

| Monolith Package | Key Controllers & Services | Entities & Tables in `peergeek` | Current Extraction Status | Final Authoritative Owner |
|---|---|---|---|---|
| `com.peergeek.auth` | `AuthController`, `JwtService` | `refresh_tokens`, `auth_otps`, `password_reset_otps` | **Extracted** in Phase 6 | `identity-service` |
| `com.peergeek.user` | `UserController`, `InstructorStudentController` | `users`, `user_educations`, `user_projects` | **Partially Extracted** (Profile/Auth in Identity; InstructorStudent in Monolith) | `identity-service` (User profiles), `course-learning-assessment-service` (Instructor student progress) |
| `com.peergeek.wallet` | `WalletController`, `WalletService` | `wallets`, `wallet_transactions` | **Extracted** in Phase 3 & audited Phase 6 | `wallet-payment-service` |
| `com.peergeek.payment` | `PaymentController`, `RazorpayWebhookController` | `payment_orders` | **Extracted** in Phase 3 & audited Phase 6 | `wallet-payment-service` |
| `com.peergeek.notification`| `NotificationController`, `NotificationService` | `notifications` | **Extracted** in Phase 2 | `notification-service` |
| `com.peergeek.community` | `CommunityController`, `DomainCommunityController`, `GlobalCommunityController` | `domain_communities`, `domain_chat_messages`, `domain_community_memberships`, `global_community_messages`, `community_posts`, `community_comments` | **Extracted** in Phase 4 | `community-service` |
| `com.peergeek.directmessage`| `DirectMessageController`, `DirectMessageService` | `direct_conversations`, `direct_messages`, `direct_participant_read_state` | **Unextracted** (in Monolith) | `community-service` (Direct 1-to-1 messaging) |
| `com.peergeek.course` | `CourseController`, `CategoryController` | `courses`, `categories`, `course_topics`, `course_publish_locks` | **Extracted** in Phase 5 | `course-learning-assessment-service` |
| `com.peergeek.module` | `ModuleController` | `course_modules` | **Extracted** in Phase 5 | `course-learning-assessment-service` |
| `com.peergeek.lesson` | `LessonController` | `lessons`, `lesson_contents` | **Extracted** in Phase 5 | `course-learning-assessment-service` |
| `com.peergeek.file` | `ResourceUploadController`, `InstructorResourceController` | `resources`, `lesson_expected_resources` | **Extracted** in Phase 5 | `course-learning-assessment-service` |
| `com.peergeek.enrollment` | `LearningEnrollmentController`, `LearningContentController` | `enrollments`, `lesson_progress`, `course_progress` | **Extracted** in Phase 5 | `course-learning-assessment-service` |
| `com.peergeek.assignment` | `InstructorAssignmentController`, `LearnerAssignmentController` | `assignments`, `assignment_questions` | **Extracted** in Phase 5 | `course-learning-assessment-service` |
| `com.peergeek.submission` | Submission processing | `submissions`, `submission_answers`, `submission_files` | **Extracted** in Phase 5 | `course-learning-assessment-service` |
| `com.peergeek.certificate` | `CertificateController`, `AdminCertificateController` | `certificates` | **Extracted** in Phase 5 | `course-learning-assessment-service` |
| `com.peergeek.bookmark` | `BookmarkController` | `bookmarks` | **Extracted** in Phase 5 | `course-learning-assessment-service` |
| `com.peergeek.note` | `LessonNoteController` | `lesson_notes` | **Extracted** in Phase 5 | `course-learning-assessment-service` |
| `com.peergeek.readiness` | `ReadinessController` | `readiness_runs`, `readiness_issues`, `link_check_results`, `resource_probe_results` | **Extracted** in Phase 5 | `course-learning-assessment-service` |
| `com.peergeek.doubtsession`| `DoubtSessionController`, `InstructorDoubtSessionController`, `LiveKitWebhookController` | `course_doubt_session_settings`, `doubt_sessions` | **Extracted** in Phase 6 | `doubt-session-service` |
| `com.peergeek.discovery` | `PublicCourseController`, `PublicCategoryController`, `AdminDiscoveryController` | `public_course_index`, `course_public_stats` | **Unextracted** (in Monolith) | `course-learning-assessment-service` (Course discovery & public catalog) |
| `com.peergeek.review` | `PublicReviewController`, `ReviewController` | `reviews` | **Unextracted** (in Monolith) | `course-learning-assessment-service` (Course reviews & ratings) |
| `com.peergeek.report` | `ReportController`, `InstructorReportController` | `community_reports` (course issue reports) | **Unextracted** (in Monolith) | `course-learning-assessment-service` (Course issue reporting) |
| `com.peergeek.analytics` | `InstructorAnalyticsController` | Aggregated views on learning tables | **Unextracted** (in Monolith) | `course-learning-assessment-service` (Instructor course analytics) |
| `com.peergeek.admin` | `AdminDashboardController` | `audit_logs` + cross-service views | **Unextracted** (in Monolith) | `identity-service` (`admin/users`), `course-learning-assessment-service` (`admin/courses`, `admin/reports`, `admin/dashboard/summary`) |
| `com.peergeek.referral` | `ReferralController`, `ReferralService` | `referrals` | **Unextracted** (in Monolith) | `referral-service` (Dedicated new service on Port `8093`) |

---

## 3. Database Dependency & Reconciliation Status

The original monolith database is `peergeek`. The current status of all 42 business tables in `peergeek` is:

| Database Table | Rows in `peergeek` | Target Database | Extraction Status |
|---|---|---|---|
| `users` | 28 | `peergeek_identity.users` | Reconciled (100% match) |
| `refresh_tokens` | 302 | `peergeek_identity.refresh_tokens` | Reconciled (100% match + 4 audit tokens) |
| `auth_otps` | 14 | `peergeek_identity.auth_otps` | Reconciled (100% match) |
| `password_reset_otps` | 6 | `peergeek_identity.password_reset_otps` | Reconciled (100% match) |
| `user_educations` | 0 | `peergeek_identity.user_educations` | Reconciled (Schema verified) |
| `user_projects` | 0 | `peergeek_identity.user_projects` | Reconciled (Schema verified) |
| `wallets` | 26 | `peergeek_wallet.wallets` | Reconciled (100% match) |
| `wallet_transactions` | 2 | `peergeek_wallet.wallet_transactions` | Reconciled (100% match) |
| `payment_orders` | 1 | `peergeek_wallet.payment_orders` | Reconciled (100% match) |
| `domain_communities` | 10 | `peergeek_community.domain_communities` | Reconciled (100% match) |
| `domain_chat_messages` | 6 | `peergeek_community.domain_chat_messages` | Reconciled (100% match) |
| `domain_community_memberships` | 2 | `peergeek_community.domain_community_memberships` | Reconciled (100% match) |
| `global_community_messages` | 5 | `peergeek_community.global_community_messages` | Reconciled (100% match) |
| `global_community_blocks` | 0 | `peergeek_community.global_community_blocks` | Reconciled (Schema verified) |
| `community_posts` | 0 | `peergeek_community.community_posts` | Reconciled (Schema verified) |
| `community_comments` | 0 | `peergeek_community.community_comments` | Reconciled (Schema verified) |
| `community_mentions` | 0 | `peergeek_community.community_mentions` | Reconciled (Schema verified) |
| `direct_conversations` | 1 | `peergeek_community.direct_conversations` | **To be migrated** to `community-service` |
| `direct_messages` | 2 | `peergeek_community.direct_messages` | **To be migrated** to `community-service` |
| `direct_participant_read_state` | 2 | `peergeek_community.direct_participant_read_state`| **To be migrated** to `community-service` |
| `course_doubt_session_settings` | 1 | `peergeek_doubt_session.course_doubt_session_settings` | Reconciled (100% match) |
| `doubt_sessions` | 0 | `peergeek_doubt_session.doubt_sessions` | Reconciled (100% match) |
| `notifications` | 70 | `peergeek_notification.notifications` | Reconciled (100% match) |
| `categories` | 10 | `peergeek_learning.categories` | Reconciled (100% match) |
| `courses` | 33 | `peergeek_learning.courses` | Reconciled (100% match) |
| `course_topics` | 150 | `peergeek_learning.course_topics` | Reconciled (100% match) |
| `course_modules` | 150 | `peergeek_learning.course_modules` | Reconciled (100% match) |
| `lessons` | 450 | `peergeek_learning.lessons` | Reconciled (100% match) |
| `lesson_contents` | 430 | `peergeek_learning.lesson_contents` | Reconciled (100% match) |
| `resources` | 99 | `peergeek_learning.resources` | Reconciled (100% match) |
| `lesson_expected_resources` | 0 | `peergeek_learning.lesson_expected_resources` | Reconciled (100% match) |
| `enrollments` | 80 | `peergeek_learning.enrollments` | Reconciled (100% match) |
| `lesson_progress` | 591 | `peergeek_learning.lesson_progress` | Reconciled (100% match) |
| `course_progress` | 77 | `peergeek_learning.course_progress` | Reconciled (100% match) |
| `assignments` | 60 | `peergeek_learning.assignments` | Reconciled (100% match) |
| `assignment_questions` | 150 | `peergeek_learning.assignment_questions` | Reconciled (100% match) |
| `submissions` | 86 | `peergeek_learning.submissions` | Reconciled (100% match) |
| `submission_answers` | 255 | `peergeek_learning.submission_answers` | Reconciled (100% match) |
| `submission_files` | 0 | `peergeek_learning.submission_files` | Reconciled (100% match) |
| `certificates` | 4 | `peergeek_learning.certificates` | Reconciled (100% match) |
| `bookmarks` | 7 | `peergeek_learning.bookmarks` | Reconciled (100% match) |
| `lesson_notes` | 0 | `peergeek_learning.lesson_notes` | Reconciled (100% match) |
| `readiness_runs` | 18 | `peergeek_learning.readiness_runs` | Reconciled (100% match) |
| `readiness_issues` | 31 | `peergeek_learning.readiness_issues` | Reconciled (100% match) |
| `link_check_results` | 7 | `peergeek_learning.link_check_results` | Reconciled (100% match) |
| `resource_probe_results` | 0 | `peergeek_learning.resource_probe_results` | Reconciled (100% match) |
| `course_publish_locks` | 3 | `peergeek_learning.course_publish_locks` | Reconciled (100% match) |
| `public_course_index` | 22 | `peergeek_learning.public_course_index` | **To be migrated** to `peergeek_learning` |
| `course_public_stats` | 20 | `peergeek_learning.course_public_stats` | **To be migrated** to `peergeek_learning` |
| `reviews` | 1 | `peergeek_learning.reviews` | **To be migrated** to `peergeek_learning` |
| `community_reports` | 4 | `peergeek_learning.course_reports` | **To be migrated** to `peergeek_learning` |
| `referrals` | 2 | `peergeek_referral.referrals` | **To be migrated** to `peergeek_referral` |
| `audit_logs` | 309 | `peergeek_learning.audit_logs` / archive | **To be migrated** / preserved |

---

## 4. API Endpoint Ownership & Cutover Mapping

The following unextracted endpoints currently hit the monolith via `/api/v1/**` fallback:

| Ingress Route Pattern | Methods | Monolith Handler | Target Service Owner | Target Service Port |
|---|---|---|---|---|
| `/api/v1/public/courses/**` | `GET` | `PublicCourseController` | `course-learning-assessment-service` | `8090` |
| `/api/v1/public/categories/**`| `GET` | `PublicCategoryController` | `course-learning-assessment-service` | `8090` |
| `/api/v1/courses/{id}/reviews/**`| `GET`, `POST`, `DELETE` | `ReviewController` | `course-learning-assessment-service` | `8090` |
| `/api/v1/courses/{id}/reports`| `POST` | `ReportController` | `course-learning-assessment-service` | `8090` |
| `/api/v1/instructor/courses/{id}/reports` | `GET` | `InstructorReportController` | `course-learning-assessment-service` | `8090` |
| `/api/v1/instructor/reports/{id}/status` | `PUT` | `InstructorReportController` | `course-learning-assessment-service` | `8090` |
| `/api/v1/instructor/courses/{id}/analytics` | `GET` | `InstructorAnalyticsController` | `course-learning-assessment-service` | `8090` |
| `/api/v1/instructor/courses/{id}/students/{id}` | `GET` | `InstructorStudentController` | `course-learning-assessment-service` | `8090` |
| `/api/v1/admin/discovery/**` | `POST` | `AdminDiscoveryController` | `course-learning-assessment-service` | `8090` |
| `/api/v1/admin/reports/**` | `GET`, `PATCH`, `PUT` | `AdminDashboardController` | `course-learning-assessment-service` | `8090` |
| `/api/v1/admin/dashboard/**` | `GET` | `AdminDashboardController` | `course-learning-assessment-service` | `8090` |
| `/api/v1/admin/users/**` | `GET`, `PATCH` | `AdminDashboardController` | `identity-service` | `8091` |
| `/api/v1/direct-messages/**` | `GET`, `POST` | `DirectMessageController` | `community-service` | `8089` |
| `/api/v1/referrals/**` | `GET`, `POST` | `ReferralController` | `referral-service` (New) | `8093` |

---

## 5. Proposed Final Architecture & Implementation Work

To decommission the monolith without losing any functionality:

1. **Course, Learning & Assessment Service (`8090`):**
   - Incorporate `discovery` (public courses, categories, popular, search, course preview, thumbnail redirect).
   - Incorporate `reviews` (course reviews, user's own review, review summaries).
   - Incorporate `reports` (learner course reports, instructor/admin report review & status updates).
   - Incorporate `analytics` (instructor course KPIs, drop-off, assessment distribution).
   - Incorporate `instructor/courses/{id}/students/{id}` (learner course progress detail).
   - Incorporate `admin/dashboard/summary` and `admin/dashboard/activities`.
   - Update Flyway migration in `course-learning-assessment-service` with `reviews`, `course_reports`, `public_course_index`, and `course_public_stats`.
2. **Identity Service (`8091`):**
   - Add `AdminUserController` for `/api/v1/admin/users` and `/api/v1/admin/users/{userId}/status`.
3. **Community Service (`8089`):**
   - Incorporate `direct-messages` (conversations, 1-to-1 messages, read receipts).
   - Add Flyway migration for `direct_conversations`, `direct_messages`, and `direct_participant_read_state`.
4. **Referral Service (`8093`):**
   - Create independent Spring Boot microservice `referral-service` owning schema `peergeek_referral` and table `referrals`.
   - Implement `/api/v1/referrals/**` (`invite`, `summary`, `history`, `validate`, `config`).
   - Integrate with `wallet-payment-service` internal API (`/api/v1/internal/wallet/rewards/referral`) for batch reward payouts.
   - Integrate with `notification-service` / RabbitMQ for referral invitation emails.
5. **API Gateway (`8081`):**
   - Update routes:
     - Route `/api/v1/public/courses/**`, `/api/v1/public/categories/**`, `/api/v1/courses/**`, `/api/v1/admin/dashboard/**`, `/api/v1/admin/reports/**`, `/api/v1/admin/discovery/**` -> `course-learning-assessment-service:8090`.
     - Route `/api/v1/admin/users/**` -> `identity-service:8091`.
     - Route `/api/v1/direct-messages/**` -> `community-service:8089`.
     - Route `/api/v1/referrals/**` -> `referral-service:8093`.
   - **Remove `monolith-api` and `monolith-ws` fallback routes.**

---

## 6. Cutover Strategy & Monolith Decommissioning Plan

1. **Pre-Cutover Migration:** Implement remaining endpoints and run Flyway migrations in target microservices. Migrate existing rows (`reviews`, `community_reports`, `direct_*`, `referrals`) from `peergeek` into target schemas.
2. **Gateway Route Cutover:** Update API Gateway routes to direct 100% of traffic to microservices. Remove monolith routes.
3. **Monolith Shutdown Test:** Ensure the monolith container is not started. Verify the complete frontend and backend test suites against the running microservices.
4. **Rollback Plan:**
   - Full MySQL dump of `peergeek` database retained.
   - Monolith source code in `backend/` preserved intact in Git repository.
   - If rollback is needed, restore gateway routing to `backend:8080`.

