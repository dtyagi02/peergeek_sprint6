# PeerGeek — Phase 6: Identity Service Extraction Documentation

## 1. Executive Summary & Extraction Overview

As part of Phase 6 of the PeerGeek microservices migration, the Identity bounded context has been extracted from the monolith into an independent, production-grade Spring Boot microservice located in `identity-service/`.

The service owns user identities, authentication workflows, BCrypt password verification, JWT issuance and refresh token rotation, avatar storage, and user profile management.

### Service Profile

| Property | Value |
|---|---|
| Service Name | `identity-service` |
| Directory | `identity-service/` |
| Dedicated Database Schema | `peergeek_identity` |
| Port | `8091` |
| API Gateway Route | `/api/v1/auth/**`, `/api/v1/users/**` |
| Message Broker | Apache Kafka (`kafka:29092` / `localhost:9092`) |
| Outbox Topic | `peergeek.identity.user-registered.v1` |
| Object Storage | MinIO/S3 (`STORAGE_BUCKET=peergeek`) |
| Packaging | Spring Boot 3.4.3 Executable JAR (`target/identity-service-0.0.1-SNAPSHOT.jar`) |
| Container Image | `peergeek-identity-service:latest` |

---

## 2. Dedicated Database Schema & Data Migration

The service maintains schema sovereignty over MySQL schema `peergeek_identity`. Database migrations are managed via Flyway.

### Tables Owned

1. **`users`**: User account credentials, roles (`USER`, `ADMIN`), modes (`STUDENT`, `INSTRUCTOR`), display metadata, bio, avatar key, skills, and privacy preferences.
2. **`refresh_tokens`**: Hashed refresh tokens (`CHAR(64)`), token family tracking (`CHAR(36)`), expiration, and revocation audit timestamps.
3. **`auth_otps`**: Email verification and password reset one-time passwords with SHA-256 hashing and expiration.
4. **`password_reset_otps`**: Dedicated reset OTP tracking table.
5. **`user_educations`**: Degree, institution, and educational history.
6. **`user_projects`**: Portfolio projects showcasing learner/instructor repositories.
7. **`outbox_events`**: Transactional outbox table for reliable Kafka event dispatching.

### Historical Data Migration Reconciliation

Data was safely migrated from the monolith schema `peergeek` to `peergeek_identity` using non-destructive data synchronization:

```sql
INSERT IGNORE INTO peergeek_identity.users SELECT * FROM peergeek.users;
SET FOREIGN_KEY_CHECKS=0;
INSERT IGNORE INTO peergeek_identity.refresh_tokens SELECT * FROM peergeek.refresh_tokens;
SET FOREIGN_KEY_CHECKS=1;
INSERT IGNORE INTO peergeek_identity.auth_otps SELECT * FROM peergeek.auth_otps;
INSERT IGNORE INTO peergeek_identity.password_reset_otps SELECT * FROM peergeek.password_reset_otps;
```

#### Row Count Verification

| Table | Monolith `peergeek` | Target `peergeek_identity` | Reconciliation Status |
|---|---|---|---|
| `users` | 28 | 28 | **100% MATCH** |
| `refresh_tokens` | 302 | 302 | **100% MATCH** |
| `auth_otps` | 14 | 14 | **100% MATCH** |
| `password_reset_otps` | 6 | 6 | **100% MATCH** |
| `user_educations` | 0 | 0 | **100% MATCH** |
| `user_projects` | 0 | 0 | **100% MATCH** |

---

## 3. Security, Authentication & JWT Architecture

The `identity-service` preserves 100% backward compatibility with existing credentials, passwords, and security tokens.

### 1. Password Hashing
- Algorithm: `BCryptPasswordEncoder` with strength factor `10`.
- Verified against historical user hashes in `peergeek_identity.users` with zero credential resets required.

### 2. JWT Generation & Validation
- Standard: Signed JWT using HMAC-SHA256 (`Keys.hmacShaKeyFor`).
- Secret: Configurable via `PEERGEEK_AUTH_JWTSECRET`, defaulting to the shared platform secret.
- Claims:
  - `sub`: User ID (`String.valueOf(user.getId())`)
  - `email`: User email
  - `role`: Role enum string (`USER`, `ADMIN`)
  - `mode`: Active mode (`STUDENT`, `INSTRUCTOR`)
  - `username`: Unique username
- Access Token Expiration: 15 minutes (`900,000 ms`).
- Refresh Token Expiration: 7 days (`604,800,000 ms`).

### 3. Refresh Token Family & Replay Detection
- Each login generates a new `family_id` UUID.
- Refreshing exchanges an existing valid refresh token for a newly rotated token under the same `family_id`.
- Replaying a previously revoked or replaced token automatically invalidates the entire token family, defending against token theft.

### 4. Service-to-Service Trust
- Internal inter-service requests validate the `X-Gateway-Secret` header matching `INTERNAL_GATEWAY_SECRET`.

---

## 4. Transactional Outbox & Event Reliability

To prevent distributed data anomalies when new users register, `identity-service` utilizes the Transactional Outbox pattern.

### Event Flow
1. **User Registration Transaction**:
   - `users` record is inserted.
   - `outbox_events` record is inserted in the exact same database transaction.
   - DB commit ensures either both are committed or both rollback.
2. **Outbox Relay**:
   - `OutboxRelay` scheduler scans for unpublished records (`published = 0`).
   - Publishes JSON payload to Kafka topic `peergeek.identity.user-registered.v1`.
   - On Kafka ACK, marks the outbox event as `published = 1` with timestamp.
   - On Kafka outage, records remain persisted in `outbox_events` with retry backoff.

### Event Contract: `peergeek.identity.user-registered.v1`
```json
{
  "userId": 29,
  "email": "learner@peergeek.com",
  "displayName": "Alex Turner",
  "username": "alexturner",
  "role": "USER",
  "registeredAt": "2026-11-08T01:30:00Z"
}
```
Consumed downstream by `notification-service` to send the welcome email and notification bell greeting.

---

## 5. API Surface

All endpoints are exposed under `/api/v1/auth` and `/api/v1/users`:

### Authentication (`/api/v1/auth/**`)
- `POST /api/v1/auth/register`: Register new user account with outbox event dispatch.
- `POST /api/v1/auth/login`: Authenticate email and password, returning access token and refresh cookie.
- `POST /api/v1/auth/refresh`: Rotate refresh token and issue fresh access token.
- `POST /api/v1/auth/logout`: Revoke active refresh token family.
- `POST /api/v1/auth/verify-email/request-otp`: Request 6-digit email confirmation OTP.
- `POST /api/v1/auth/verify-email/confirm`: Validate OTP and activate email verification.
- `POST /api/v1/auth/forgot-password`: Request password reset OTP.
- `POST /api/v1/auth/reset-password`: Submit reset OTP and set new password.

### Users & Profiles (`/api/v1/users/**`)
- `GET /api/v1/users/me`: Retrieve currently authenticated user profile.
- `PUT /api/v1/users/me`: Update profile details (headline, bio, social links, privacy flags).
- `POST /api/v1/users/mode`: Switch active user mode (`STUDENT` / `INSTRUCTOR`).
- `GET /api/v1/users/by-username/{username}`: Public lookup for lightweight profile card.
- `GET /api/v1/users/profile/{username}`: Full profile including showcase projects and education.
- `GET /api/v1/users/{id}/public`: Lookup public user metadata by user ID.
- `POST /api/v1/users/{userId}/avatar`: Upload user avatar image to MinIO object storage.
- `GET /api/v1/users/{userId}/avatar`: Stream user avatar image.
- `POST /api/v1/users/me/education`: Add education entry.
- `PUT /api/v1/users/me/education/{id}`: Update education entry.
- `DELETE /api/v1/users/me/education/{id}`: Remove education entry.
- `POST /api/v1/users/me/projects`: Add showcased project.
- `PUT /api/v1/users/me/projects/{id}`: Update showcased project.
- `DELETE /api/v1/users/me/projects/{id}`: Remove showcased project.

---

## 6. API Gateway Routing & Infrastructure Integration

### Gateway Configuration (`api-gateway/src/main/resources/application.yml`)
```yaml
- id: identity-service
  uri: ${IDENTITY_SERVICE_URL:http://localhost:8091}
  predicates:
    - Path=/api/v1/auth/**, /api/v1/users/**
```

### Docker Compose Service Definition (`docker-compose.yml`)
```yaml
identity-service:
  build:
    context: ./identity-service
    dockerfile: Dockerfile
  container_name: peergeek-identity-service
  environment:
    SERVER_PORT: 8091
    DB_HOST: mysql
    DB_PORT: 3306
    MYSQL_DATABASE: peergeek_identity
    MYSQL_USER: root
    MYSQL_PASSWORD: ${MYSQL_ROOT_PASSWORD:-peergeek}
    KAFKA_BOOTSTRAP_SERVERS: kafka:29092
    STORAGE_ENDPOINT: http://minio:9000
    STORAGE_ACCESS_KEY: ${STORAGE_ACCESS_KEY:-minioadmin}
    STORAGE_SECRET_KEY: ${STORAGE_SECRET_KEY:-minioadmin}
    STORAGE_BUCKET: ${STORAGE_BUCKET:-peergeek}
    PEERGEEK_AUTH_JWTSECRET: ${PEERGEEK_AUTH_JWTSECRET}
    INTERNAL_GATEWAY_SECRET: ${INTERNAL_GATEWAY_SECRET:-pg-internal-gateway-secret-2026}
  ports:
    - "8091:8091"
  depends_on:
    mysql:
      condition: service_healthy
    kafka:
      condition: service_healthy
  healthcheck:
    test: ["CMD", "wget", "-q", "-O", "-", "http://localhost:8091/actuator/health"]
    interval: 15s
    timeout: 3s
    retries: 3
    start_period: 30s
```

---

## 7. Verification Evidence & Test Results

### 1. Spring Boot Integration Tests
Executed via Maven Surefire:
```bash
mvnw.cmd test
```
**Results:** `Tests run: 6, Failures: 0, Errors: 0, Skipped: 0`
- `contextLoads`: Context initialization and Flyway migration verification.
- `testPasswordHashingBCryptCompatibility`: Verified BCrypt matching against existing hashes.
- `testJwtIssuanceAndParsing`: Verified JWT token issuance, HMAC verification, and claims extraction.
- `testUserRegistrationAndOutboxEvent`: Verified user registration, DB insertion, and transactional outbox event creation.
- `testEmailVerificationAndLoginFlow`: Verified email verification OTP generation, consumption, and login token issuance.
- `testPasswordResetFlow`: Verified 2-step forgot-password and reset-password workflow.

### 2. Actuator Health Verification
```bash
curl.exe -s http://localhost:8091/actuator/health
```
**Output:**
```json
{
  "status": "UP",
  "groups": ["liveness", "readiness"],
  "components": {
    "db": { "status": "UP", "details": { "database": "MySQL", "validationQuery": "isValid()" } },
    "diskSpace": { "status": "UP" },
    "livenessState": { "status": "UP" },
    "mail": { "status": "UP", "details": { "location": "mailpit:1025" } },
    "ping": { "status": "UP" },
    "readinessState": { "status": "UP" }
  }
}
```

### 3. API Gateway End-to-End Routing Verification
```bash
curl.exe -s http://localhost:8081/api/v1/users/by-username/devansh
```
**Output:**
```json
{
  "id": 1,
  "username": "devansh",
  "displayName": "DEVANSH",
  "headline": "A FULL PACE LEANER",
  "bio": "an instructor still learning and exploring"
}
```
Identity Service successfully routed through API Gateway on port 8081 and verified against production schema.

