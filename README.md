# PeerGeek — Microservices Architecture (Phase 7 Monolith Retirement Release)

PeerGeek is a high-availability, distributed EdTech and collaborative learning platform powered by an independently deployable microservices architecture.

Originally structured as a modular monolith, the system underwent a phased extraction (Phases 1 through 7) and now operates 100% via autonomous Spring Boot microservices behind a unified Spring Cloud API Gateway.

---

## 1. System Architecture & Services

The platform consists of the following independently deployable services communicating over Docker networks and Apache Kafka:

| Microservice | Directory | Ingress Port | Database Schema | Primary Responsibilities |
|---|---|---:|---|---|
| **API Gateway** | `api-gateway/` | **8081** | None | Public ingress routing, CORS enforcement, edge header sanitization, correlation ID injection. |
| **Identity Service** | `identity-service/` | **8091** | `peergeek_identity` | User authentication, registration, JWT issuance, refresh tokens, profiles, avatars, admin users. |
| **Course, Learning & Assessment Service** | `course-learning-assessment-service/` | **8090** | `peergeek_learning` | Course creation, publishing, catalog discovery, progress, assignments, grading, certificates, reviews, reports, analytics, admin dashboard. |
| **Community Service** | `community-service/` | **8089** | `peergeek_community` | Domain community chat, global discussions, moderation, posts & comments, 1-to-1 direct messaging, WebSocket STOMP broker. |
| **Doubt Session Service** | `doubt-session-service/` | **8092** | `peergeek_doubt_session` | 1-on-1 doubt session booking, billing reservations, LiveKit token issuance, room webhooks, session dispute resolution. |
| **Wallet & Payment Service** | `wallet-payment-service/` | **8088** | `peergeek_wallet` | GeekCoins double-entry ledger, balances, holds, Razorpay payment processing, webhooks, internal rewards. |
| **Notification Service** | `notification-service/` | **8087** | `peergeek_notification` | In-app notification center, unread counters, read acknowledgments, email queue management. |
| **Referral Service** | `referral-service/` | **8093** | `peergeek_referral` | Referral invitation codes, invitation dispatch, code validation, reward summary, and history. |
| **Frontend Web App** | `frontend/` | **5173** | LocalStorage / Memory | React SPA with Vite, TailwindCSS, TipTap editor, Lucide icons, Vitest test suite. |

---

## 2. Infrastructure & Messaging

- **API Gateway:** Spring Cloud Gateway on Netty (`8081`).
- **Relational Databases:** MySQL 8.0/8.4 with dedicated per-service schemas (`peergeek_identity`, `peergeek_learning`, `peergeek_community`, `peergeek_doubt_session`, `peergeek_wallet`, `peergeek_notification`, `peergeek_referral`).
- **Event Streaming:** Apache Kafka (`kafka:29092` internal, `localhost:9092` host) with Transactional Outbox pattern across services.
- **Message Broker:** RabbitMQ (`5672` / `15672`) for asynchronous email queuing.
- **Object Storage:** MinIO / AWS S3 compatible (`9000` / `9001`) for lesson attachments, course thumbnails, user avatars, and certificate PDFs.
- **Cache:** Redis (`6379`) for fast rate limiting and ephemeral session caching.
- **Mail Testing:** Mailpit (`1025` SMTP, `8025` Web UI) for local email inspection.

---

## 3. Running the Stack

### Quickstart with Docker Compose

```powershell
# 1. Copy environment template
Copy-Item .env.example .env

# 2. Start infrastructure and all microservices
docker compose up -d
```

### Published Ports & Endpoints

| Service | Host Port | Health Endpoint |
|---|---|---|
| **API Gateway (Ingress)** | `8081` | http://localhost:8081/actuator/health |
| **Frontend** | `5173` | http://localhost:5173 |
| **Notification Service** | `8087` | http://localhost:8087/actuator/health |
| **Wallet & Payment Service** | `8088` | http://localhost:8088/actuator/health |
| **Community Service** | `8089` | http://localhost:8089/actuator/health |
| **Course & Learning Service** | `8090` | http://localhost:8090/actuator/health |
| **Identity Service** | `8091` | http://localhost:8091/actuator/health |
| **Doubt Session Service** | `8092` | http://localhost:8092/actuator/health |
| **Referral Service** | `8093` | http://localhost:8093/actuator/health |
| **MinIO Console** | `9001` | http://localhost:9001 |
| **Mailpit Inbox** | `8025` | http://localhost:8025 |
| **RabbitMQ Management** | `15672` | http://localhost:15672 |

---

## 4. Monolith Retirement & Legacy Status

The original monolith (`backend/`, port `8080`) has been **fully decommissioned**:
- Gateway routes `monolith-api` (`/api/v1/**`) and `monolith-ws` (`/ws/**`) have been permanently removed.
- Monolith container is stopped and assigned to Docker Compose profile `monolith-legacy`.
- Legacy database `peergeek` has been reconciled 100% against target microservices and backed up to `backup/peergeek_monolith_final_backup.sql`.
- Monolith source code in `backend/` is preserved for historical reference and break-glass disaster recovery.

---

## 5. Verification & Testing

### Frontend Regression Suite (Vitest)
```powershell
cd frontend
npm ci
npm test -- --run
```
*Current test suite status: 30 test files passed, 242 tests passed, 0 failures.*

### Building Microservices
Each microservice includes its own Maven build wrapper:
```powershell
# Example: Build Course Service
cd course-learning-assessment-service
mvnw.cmd package -DskipTests
```

---

## 6. Migration Documentation

- [Phase 7 Pre-Cutover Inventory Audit](./PHASE_7_PRE_CUTOVER_AUDIT.md)
- [Phase 7 API Gateway Route Ownership](./PHASE_7_API_ROUTE_OWNERSHIP.md)
- [Phase 7 Database Ownership & Retirement](./PHASE_7_DATABASE_OWNERSHIP_AND_RETIREMENT.md)
- [Phase 7 Monolith Retirement & Final Cutover Report](./PHASE_7_MONOLITH_RETIREMENT_AND_FINAL_CUTOVER.md)
