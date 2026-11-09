# PeerGeek Microservices Migration — Phase 6: Identity & Doubt Session Preflight Audit

**Date:** 2026-11-07  
**Phase:** 6 — Identity & Doubt Session Service Extraction  
**Status:** Preflight Audit Complete  

---

## 1. Executive Summary

Phase 6 extracts the two remaining core bounded contexts from the PeerGeek modular monolith:
1. **Identity Service** (`identity-service/`): Dedicated microservice owning authentication, user profile management, credential verification, JWT issuance/validation, password reset flows, education, and portfolio projects.
2. **Doubt Session Service** (`doubt-session-service/`): Dedicated microservice owning 1:1 on-demand audio/video doubt clearing session scheduling, instructor availability, session lifecycle state machine, real-time LiveKit room token generation, webhook lifecycle tracking, and GeekCoins financial billing coordination via `wallet-payment-service`.

This preflight audit inspects the existing monolith implementation, catalogs historical data and database structures, validates port and infrastructure availability, and establishes strict architectural boundaries for independent extraction.

---

## 2. Infrastructure & Environment Preflight

### 2.1 Container & Service Runtime Status

All supporting shared infrastructure and previously extracted microservices are healthy and running:

| Container Name | Service / Component | Port(s) | Status |
|---|---|---|---|
| `peergeek20-mysql-1` | Shared MySQL 8.0 Engine | 3306 (host) | Healthy |
| `peergeek20-kafka-1` | Apache Kafka Broker | 9092, 29092 | Healthy |
| `peergeek20-rabbitmq-1` | RabbitMQ (AMQP + Mgmt) | 5672, 15672 | Healthy |
| `peergeek20-redis-1` | Redis Cache / State Store | 6379 | Healthy |
| `peergeek20-mailpit-1` | Mailpit Mock SMTP / Web UI | 1025, 8025 | Healthy |
| `peergeek20-minio-1` | MinIO S3 Object Storage | 9000, 9001 | Healthy |
| `peergeek-api-gateway-test` | Spring Cloud API Gateway | 8081 | Up & Routing |
| `peergeek-notification-test` | Notification Service (Phase 2) | 8087 | Up & Listening |
| `peergeek-wallet-payment-test`| Wallet & Payment Service (Phase 3) | 8088 | Up & Serving Ledger |
| `peergeek-community-test` | Community Service (Phase 4) | 8089 | Up & Serving Feeds |
| `peergeek-course-learning-test`| Course & Learning Service (Phase 5)| 8090 | Up & Serving LMS |

### 2.2 Port Availability Check

Target ports for the new microservices were verified against the local TCP socket stack:

- **Port 8091 (Target: `identity-service`):** Verified **FREE** (No active listeners).
- **Port 8092 (Target: `doubt-session-service`):** Verified **FREE** (No active listeners).

---

## 3. Monolith Identity Bounded Context Audit

### 3.1 Historical Database Tables & Row Counts (`peergeek`)

An inspection of the original `peergeek` database schema identified the following identity-related tables:

| Table Name | Description | Row Count | Migration Strategy |
|---|---|---|---|
| `users` | Primary user identity, BCrypt passwords, role, profile details, active status | **28** | Idempotent INSERT IGNORE to `peergeek_identity.users` |
| `refresh_tokens` | Active and historical JWT refresh tokens with expiry and revocation status | **302** | Idempotent INSERT IGNORE to `peergeek_identity.refresh_tokens` |
| `auth_otps` | Email registration verification one-time passwords | **14** | Idempotent INSERT IGNORE to `peergeek_identity.auth_otps` |
| `password_reset_otps`| Password recovery one-time passwords | **6** | Idempotent INSERT IGNORE to `peergeek_identity.password_reset_otps` |
| `user_educations` | User academic background / education records | **0** | Schema replicated; ready for new records |
| `user_projects` | User portfolio project showcase records | **0** | Schema replicated; ready for new records |

### 3.2 Security, Password Hashing & JWT Claims

- **Password Hashing:** `BCryptPasswordEncoder` with strength factor 10 (`$2a$10$...`). Existing hashes must be preserved exactly without re-hashing or modification to avoid invalidating credentials for all 28 users.
- **JWT Architecture:**
  - Token Type: HMAC-SHA256 (`Keys.hmacShaKeyFor(secret.getBytes(UTF_8))`).
  - Secret: Configured via `PEERGEEK_AUTH_JWTSECRET` / `jwt.secret`.
  - Subject: `String.valueOf(user.getId())`.
  - Primary Claims: `role` (`USER` or `ADMIN`).
  - Enhanced Claims: In addition to `role`, `userId` is embedded as a long claim to ensure seamless backward and forward compatibility with all microservice JWT parsers.
  - Refresh Tokens: Stored in database with SHA-256 / UUID hashes, delivered to clients in an `HttpOnly`, `SameSite=Lax` cookie named `PEERGEEK_REFRESH`.

### 3.3 Identity API Endpoints Catalog

#### Authentication (`/api/v1/auth`)
- `POST /api/v1/auth/register` — Register new user with email, username, password, role (`STUDENT` / `INSTRUCTOR`).
- `POST /api/v1/auth/verify-email` — Verify email using 6-digit OTP.
- `POST /api/v1/auth/resend-verification-otp` — Resend verification email.
- `POST /api/v1/auth/login` — Authenticate with email/password; returns access token and sets refresh cookie.
- `POST /api/v1/auth/refresh` — Issue new access token from refresh token cookie.
- `POST /api/v1/auth/logout` — Revoke refresh token and clear cookie.
- `POST /api/v1/auth/forgot-password` — Generate and email 6-digit password reset OTP.
- `POST /api/v1/auth/verify-reset-otp` — Validate reset OTP.
- `POST /api/v1/auth/reset-password` — Set new password using verified token.

#### User Profile (`/api/v1/users`)
- `GET /api/v1/users/me` — Current authenticated user profile summary.
- `PUT /api/v1/users/me/profile` — Update bio, headline, website, GitHub, LinkedIn, Twitter, skills.
- `PATCH /api/v1/users/me/mode` — Toggle active mode (`STUDENT` / `INSTRUCTOR`).
- `GET /api/v1/users/{id}/public` — Public profile by user ID.
- `GET /api/v1/users/by-username/{username}` — Public profile summary by username.
- `GET /api/v1/users/{userId}/profile` — Detailed profile by user ID.
- `GET /api/v1/users/by-username/{username}/profile` — Detailed profile by username.
- `GET /api/v1/users/me/education` — List user's education history.
- `POST /api/v1/users/me/education` — Add education entry.
- `PUT /api/v1/users/me/education/{id}` — Update education entry.
- `DELETE /api/v1/users/me/education/{id}` — Delete education entry.
- `GET /api/v1/users/me/projects` — List user's portfolio projects.
- `POST /api/v1/users/me/projects` — Add portfolio project.
- `PUT /api/v1/users/me/projects/{id}` — Update portfolio project.
- `DELETE /api/v1/users/me/projects/{id}` — Delete portfolio project.
- `POST /api/v1/users/me/avatar` — Upload profile avatar image to MinIO.
- `GET /api/v1/users/{userId}/avatar` — Retrieve avatar or redirect to presigned/direct URL.

---

## 4. Monolith Doubt Session Bounded Context Audit

### 4.1 Historical Database Tables & Row Counts (`peergeek`)

An inspection of the original `peergeek` database schema identified the following doubt-session-related tables:

| Table Name | Description | Row Count | Migration Strategy |
|---|---|---|---|
| `course_doubt_session_settings` | Instructor course-specific doubt session availability, hourly rate in PGC, max duration | **1** | Idempotent INSERT IGNORE to `peergeek_doubt_session.course_doubt_session_settings` |
| `doubt_sessions` | Doubt clearing session lifecycle records, status, billable duration, billing state | **0** | Schema replicated; ready for new bookings |

### 4.2 Doubt Session Lifecycle & Billing Engine

1. **Duration Options:** 5, 10, 15, or 30 minutes.
2. **Pricing Structure:** Configured per course via `hourlyRatePgc` (default 600 PGC/hr = 10 PGC/min).
3. **Reservation on Booking:**
   - Maximum potential cost is computed: `maxCost = ceil(hourlyRate * (durationMinutes / 60.0))`.
   - Before confirming session request, the service calls `wallet-payment-service`:
     `POST /api/v1/wallet/reserve` with `referenceType="DOUBT_SESSION"`, `referenceId=sessionUid`, `amount=maxCost`.
   - If wallet balance is insufficient, reservation fails with `400 INSUFFICIENT_FUNDS`.
4. **Session Cancellation / Rejection / Expiration:**
   - If the instructor rejects the session, student cancels before start, or session times out without instructor response:
   - Full reserved amount is released back to student wallet:
     `POST /api/v1/wallet/release` with `referenceType="DOUBT_SESSION"`, `referenceId=sessionUid`.
5. **Real-Time LiveKit Session & Actual Call Tracking:**
   - Both instructor and student join a unique LiveKit room: `doubt-<sessionUid>`.
   - LiveKit webhook updates session join/leave timestamps.
   - Billable time is measured between first overlap of both participants and room departure.
6. **Settlement on Session Completion:**
   - If billable seconds <= 0: 100% of reservation is released.
   - If billable seconds > 0: Billable minutes are rounded up to next whole minute.
   - Final charge: `finalCharge = ceil(hourlyRate * (billableMinutes / 60.0))`.
   - The service calls `wallet-payment-service`:
     `POST /api/v1/wallet/settle` with `referenceType="DOUBT_SESSION"`, `referenceId=sessionUid`, `reservedAmount=maxCost`, `actualAmount=finalCharge`, `recipientUserId=instructorId`.
   - Any difference between reserved and actual is automatically refunded to student.

### 4.3 Doubt Session API Endpoints Catalog

#### Public Endpoints
- `GET /api/v1/public/courses/{courseId}/doubt-session-availability` — Check if instructor offers doubt sessions for a course and get hourly rate.

#### Student Endpoints (`/api/v1/doubt-sessions`)
- `POST /api/v1/doubt-sessions/request` — Request a doubt session for a course (reserves GeekCoins).
- `POST /api/v1/doubt-sessions/{sessionId}/cancel` — Cancel pending session (releases GeekCoins).
- `GET /api/v1/doubt-sessions/{sessionId}` — Get session status and details.
- `GET /api/v1/doubt-sessions/active` — Get current active session for student.
- `POST /api/v1/doubt-sessions/{sessionId}/token` — Generate LiveKit room token.
- `POST /api/v1/doubt-sessions/{sessionId}/end` — Manually terminate session.

#### Instructor Endpoints (`/api/v1/instructor/...`)
- `GET /api/v1/instructor/courses/{courseId}/doubt-session-settings` — Get course doubt session configuration.
- `PUT /api/v1/instructor/courses/{courseId}/doubt-session-settings` — Update course doubt session configuration (hourly rate, enabled flag).
- `GET /api/v1/instructor/doubt-sessions/availability` — Get instructor overall availability status.
- `PUT /api/v1/instructor/doubt-sessions/availability` — Toggle instructor online/offline availability.
- `GET /api/v1/instructor/doubt-sessions/active` — Get current active session for instructor.
- `POST /api/v1/instructor/doubt-sessions/{sessionId}/accept` — Accept student session request.
- `POST /api/v1/instructor/doubt-sessions/{sessionId}/reject` — Reject session request (releases GeekCoins).

#### Real-time Webhooks (`/api/v1/doubt-sessions/webhook`)
- `POST /api/v1/doubt-sessions/webhook` — LiveKit server webhook receiving participant join/leave and room finished events.

---

## 5. Architectural Boundary & Data Isolation Rules

1. **Independent Schema Ownership:**
   - `peergeek_identity` exclusively stores identity, tokens, OTPs, education, and portfolio records.
   - `peergeek_doubt_session` exclusively stores session settings, booking requests, states, and LiveKit session metadata.
   - Neither database contains foreign keys referencing tables in other microservice schemas.
2. **Synchronous REST Communication:**
   - `doubt-session-service` communicates with `wallet-payment-service` (port 8088) via authenticated REST client for reserve, settle, and release transactions.
   - Gateway forwards `/api/v1/auth/**` and `/api/v1/users/**` to `identity-service` (port 8091).
   - Gateway forwards `/api/v1/doubt-sessions/**` and `/api/v1/instructor/doubt-sessions/**` to `doubt-session-service` (port 8092).
3. **Asynchronous Messaging & Outbox Events:**
   - Both services implement the Transactional Outbox pattern (`outbox_events` table + relay scheduler).
   - `identity-service` publishes `peergeek.identity.user-registered.v1`.
   - `doubt-session-service` publishes `peergeek.doubtsession.session-booked.v1`.

---

## 6. Preflight Readiness Verdict

All prerequisites are satisfied:
- Infrastructure containers running and operational.
- Ports 8091 and 8092 verified available.
- Source code, domain models, algorithms, and business logic audited and mapped.
- Schema migration scripts and idempotent data transfer plans established.

Ready to proceed with **Workstream 1 & 2: Identity Service Creation & Migration**.

