# Phase 4: Community Service Extraction Implementation Report

## 1. Executive Summary

In accordance with the PeerGeek Strangler Fig Microservices Migration Blueprint, **Phase 4: Community Service Extraction** has been successfully designed, extracted, implemented, verified, and operationalized.

The Community bounded context has been extracted from the monolithic backend into an autonomous, event-driven Spring Boot microservice (`community-service`) running on port `8089`. It operates with exclusive database ownership over a dedicated MySQL schema (`peergeek_community`), manages domain-based communities, real-time STOMP WebSocket chat channels, global discussion feeds, posts and threaded comments, `@username` mention detection, and administrative moderation controls. Domain events are persisted atomically via the **Transactional Outbox Pattern** and relayed asynchronously to **Apache Kafka**, triggering downstream notifications in the `notification-service`.

All existing community capabilities, frontend UI flows, and legacy database records have been preserved with **zero data loss and zero regressions**.

---

## 2. Target Architecture Overview

```mermaid
graph TD
    subgraph ClientTier ["Client Tier (Port 5173)"]
        Browser["React 19 SPA<br/>• DomainChatPage<br/>• GlobalCommunityPage<br/>• CommunityFeedPage (Posts/Comments)<br/>• STOMP WebSocket Client"]
    end

    subgraph EdgeTier ["Edge Tier: API Gateway (Port 8081)"]
        Gateway["Spring Cloud API Gateway<br/>• /api/v1/community/** -> Community Service<br/>• /api/v1/domains/** -> Community Service<br/>• /api/v1/admin/community/** -> Community Service<br/>• /api/v1/wallet/** -> Wallet Service<br/>• /api/v1/notifications/** -> Notification Service<br/>• /api/v1/** -> Monolith<br/>• Sanitizes external X-Gateway-Secret<br/>• Injects X-Correlation-Id"]
    end

    subgraph CommunityServiceTier ["Community Microservice (Port 8089)"]
        CommunityApp["Community Service (Spring Boot 3.4.3)<br/>• DomainCommunityController & Service<br/>• GlobalCommunityController & Service<br/>• AdminGlobalCommunityController<br/>• CommunityController (Posts & Comments)<br/>• CommunityWebSocketController (STOMP)<br/>• OutboxRelay (Kafka Publisher)<br/>• DataMigrationService (Zero-Downtime Data Migration)"]
        CommunityDB[("MySQL Schema: peergeek_community<br/>• domain_communities<br/>• domain_community_memberships<br/>• domain_chat_messages<br/>• global_community_messages<br/>• global_community_blocks<br/>• community_posts<br/>• community_comments<br/>• community_mentions<br/>• community_user_profiles<br/>• outbox_events")]
    end

    subgraph NotificationTier ["Notification Microservice (Port 8087)"]
        NotificationApp["Notification Service<br/>• NotificationKafkaConsumer<br/>• Listens to peergeek.community.mention-created.v1<br/>• Creates COMMUNITY_MENTION notification"]
        NotificationDB[("MySQL Schema: peergeek_notifications")]
    end

    subgraph MessagingBrokers ["Distributed Event & Realtime Infrastructure"]
        Kafka["Apache Kafka (Port 9092 / 29092)<br/>Topics:<br/>• peergeek.community.message-created.v1<br/>• peergeek.community.message-deleted.v1<br/>• peergeek.community.mention-created.v1<br/>• peergeek.community.member-joined.v1<br/>• peergeek.community.member-left.v1<br/>• peergeek.community.user-blocked.v1<br/>• peergeek.community.user-unblocked.v1"]
        STOMP["Spring STOMP Message Broker (/ws)<br/>Destinations:<br/>• /topic/community.domain.{slug}<br/>• /topic/community.global<br/>• /topic/admin.community"]
    end

    Browser -->|HTTP REST Requests| Gateway
    Browser -->|WebSocket STOMP Connection| CommunityApp
    Gateway -->|Forward Community Routes| CommunityApp
    CommunityApp --> CommunityDB
    CommunityApp -->|Transactional Outbox Relay| Kafka
    CommunityApp -->|Broadcast Realtime Events| STOMP
    Kafka -->|Consume Mention Events| NotificationApp
    NotificationApp --> NotificationDB
```

---

## 3. Implemented Components & Service Structure

### 3.1 Maven Project & Service Structure
- **Root Directory:** `community-service/`
- **Application Class:** `com.peergeek.community.CommunityServiceApplication`
- **Port:** `8089` (Configurable via `SERVER_PORT`)
- **Key Dependencies:**
  - Spring Boot 3.4.3 Starter Web
  - Spring Boot Starter WebSocket & STOMP messaging
  - Spring Boot Starter Data JPA & MySQL Connector/J
  - Spring Boot Starter Security & JJWT 0.12.6
  - Spring Boot Starter Validation
  - Spring Boot Starter Actuator (Liveness & Readiness probes)
  - Spring Kafka
  - Flyway MySQL Migration Engine
  - H2 Database & Spring Boot Test for automated testing

### 3.2 Database Ownership (`peergeek_community`)
The microservice owns the dedicated MySQL schema `peergeek_community`.
- **Flyway Migration Script:** `V1_001__create_community_tables.sql`
- **Tables Created:**
  1. `community_user_profiles`: Replicated read-model profile cache containing `user_id`, `email`, `username`, `display_name`, `avatar_url`, `role`, and `mode` to eliminate synchronous cross-service queries for author information.
  2. `domain_communities`: Stores course category domain communities (`category_id`, `name`, `slug`, `description`, timestamps).
  3. `domain_community_memberships`: Tracks user domain memberships (`domain_id`, `user_id`, `joined_at`).
  4. `domain_chat_messages`: Stores group chat messages within specific domains (`domain_id`, `sender_id`, `sender_name`, `sender_username`, `sender_avatar_url`, `message_type`, `content`, `created_at`).
  5. `global_community_messages`: Stores platform-wide global discussion messages (`user_id`, `user_name`, `user_username`, `user_avatar_url`, `user_role`, `user_mode`, `content`, `deleted`, `deleted_at`, `deleted_by_id`, `deleted_by_name`, `delete_reason`).
  6. `global_community_blocks`: Tracks blocked actors restricted from posting (`user_id`, `user_name`, `user_email`, `user_username`, `blocked_by_id`, `blocked_by_name`, `reason`, `created_at`).
  7. `community_posts`: Discussion posts created by users (`author_id`, `author_name`, `author_username`, `author_avatar_url`, `content`, `like_count`, `deleted`, timestamps).
  8. `community_comments`: Threaded comments and nested replies (`post_id`, `author_id`, `author_name`, `author_username`, `author_avatar_url`, `parent_comment_id`, `content`, `deleted`, timestamps).
  9. `community_mentions`: Extracted `@username` mentions associated with posts or comments (`post_id`, `comment_id`, `mentioned_user_id`, `created_at`, `updated_at`).
  10. `outbox_events`: Stores transactional outbox records (`event_id`, `event_type`, `topic`, `payload`, `published`, `retry_count`, timestamps).

---

## 4. Historical Data Migration & Zero-Loss Reconciliation

A self-healing, idempotent migration mechanism was implemented in `DataMigrationService.java` to copy historical data from legacy `peergeek` tables into `peergeek_community` on startup without modifying or dropping legacy records.

### 4.1 Reconciliation Matrix
| Entity / Table Name | Source Table (`peergeek`) | Target Table (`peergeek_community`) | Source Count | Target Count | Reconciliation Delta | Status |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Domain Communities** | `domain_communities` | `domain_communities` | 10 | 10 | 0 | **100% Reconciled** |
| **Domain Memberships** | `domain_community_memberships` | `domain_community_memberships` | 3 | 3 | 0 | **100% Reconciled** |
| **Domain Chat Messages** | `domain_chat_messages` | `domain_chat_messages` | 7 | 7 | 0 | **100% Reconciled** |
| **Global Community Messages** | `global_community_messages` | `global_community_messages` | 5 | 5 | 0 | **100% Reconciled** |
| **Global Community Blocks** | `global_community_blocks` | `global_community_blocks` | 0 | 0 | 0 | **100% Reconciled** |
| **Community Posts** | `community_posts` | `community_posts` | 0 | 0 | 0 | **100% Reconciled** |
| **Community Comments** | `community_comments` | `community_comments` | 0 | 0 | 0 | **100% Reconciled** |
| **Community Mentions** | `community_mentions` | `community_mentions` | 0 | 0 | 0 | **100% Reconciled** |
| **User Profiles (Read-Model)** | `users` | `community_user_profiles` | 28 | 28 | 0 | **100% Reconciled** |

Zero historical records were lost during the migration. Legacy tables in `peergeek` remain unaltered for safe rollback.

---

## 5. API Gateway Route Configuration & Security

The API Gateway (`api-gateway/src/main/resources/application.yml`) was updated to route all community traffic to `community-service`:

```yaml
- id: community-service
  uri: ${COMMUNITY_SERVICE_URL:http://localhost:8089}
  predicates:
    - Path=/api/v1/community/**, /api/v1/domains/**, /api/v1/admin/community/**
```

### Security Enforcement:
- **Client Verification:** All requests through the API Gateway carry standard JWT `Authorization: Bearer <token>` headers, cryptographically verified by `JwtService` and `JwtAuthenticationFilter`.
- **Header Sanitization:** `CorrelationIdFilter` strips untrusted incoming `X-Gateway-Secret` headers to prevent identity spoofing from client browsers.
- **Service-to-Service Trust:** Direct internal communication between microservices is permitted when authenticated with the shared internal secret `X-Gateway-Secret: pg-internal-gateway-secret-2026`.
- **Role-Based Access Control:** Admin moderation endpoints under `/api/v1/admin/community/**` strictly require `ROLE_ADMIN`.

---

## 6. Real-Time STOMP WebSocket Messaging

The service exposes an independent WebSocket STOMP endpoint (`/ws`) with an in-memory broker configured in `WebSocketConfig.java`:

- **Endpoint:** `/ws` (with SockJS fallback support).
- **Application Destination Prefix:** `/app`
- **Broker Destinations:** `/topic`
- **Active Channel Subscriptions:**
  - `/topic/community.domain.{slug}`: Real-time broadcast for domain group chat.
  - `/topic/community.global`: Real-time broadcast for global discussion messages.
  - `/topic/admin.community`: Moderation broadcast stream for administrators.
- **Authentication:** `WebSocketAuthChannelInterceptor` intercepts `STOMP CONNECT` frames, extracts the Bearer token or gateway header, validates credentials, and attaches the `CurrentUser` principal to the session.

---

## 7. Transactional Outbox Pattern & Kafka Event Catalog

To ensure reliability, message creation, deletion, membership changes, and user blocks write both the business entity and an outbox event within the same database transaction. `OutboxRelay` continuously polls unpublished events and publishes them to Apache Kafka.

### Event Catalog:
| Event Type | Kafka Topic | Triggering Action |
| :--- | :--- | :--- |
| `DomainMessageCreated` | `peergeek.community.message-created.v1` | User sends a message in a domain community chat |
| `GlobalMessageCreated` | `peergeek.community.message-created.v1` | User posts a message to global community chat |
| `GlobalMessageDeleted` | `peergeek.community.message-deleted.v1` | User or Admin deletes a message from global feed |
| `CommunityMentionCreated` | `peergeek.community.mention-created.v1` | User mentions `@username` in a chat, post, or comment |
| `DomainMemberJoined` | `peergeek.community.member-joined.v1` | User joins a domain community |
| `DomainMemberLeft` | `peergeek.community.member-left.v1` | User leaves a domain community |
| `CommunityUserBlocked` | `peergeek.community.user-blocked.v1` | Admin blocks a user from community participation |
| `CommunityUserUnblocked` | `peergeek.community.user-unblocked.v1` | Admin lifts a community block |

### Notification Service Integration:
`notification-service` listens to `peergeek.community.mention-created.v1`. When received, `NotificationKafkaConsumer` parses the mention event and inserts a notification of type `COMMUNITY_MENTION` into `peergeek_notifications.notifications` for the mentioned user.

---

## 8. Verification & Test Execution Evidence

### 8.1 Backend Unit & Integration Tests (100% Success)
Ran via Maven test execution inside Eclipse Temurin 21 container:
- **`CommunityControllerIntegrationTest`**: 4/4 passed (Public routes, JWT authentication, Gateway routing, Post feed pagination).
- **`DomainCommunityServiceTest`**: 4/4 passed (Domain listing, membership joins, blocked user rejection, message sending).
- **`GlobalCommunityServiceTest`**: 4/4 passed (Global message retrieval, posting, soft-deletes, user blocking).
- **`CommunityPostServiceTest`**: 4/4 passed (Post creation, blank content validation, comment threading, outbox publishing).
- **Total Tests:** 16 passed, 0 failures, 0 errors.

### 8.2 Frontend Vitest Suite (100% Success)
Executed against React frontend components (`npx vitest run src/features/community/`):
- **`domainChat.test.jsx`**: 10/10 passed (Domain grouping, entry buttons, group chat experience, message delivery).
- **`globalCommunity.test.jsx`**: 9/9 passed (Global community UI, dynamic WebSocket indicator, feed updates).
- **`community.test.jsx`**: 9/9 passed (Post feeds, empty states, post creation, nested comment replies).
- **Total Tests:** 28 passed, 0 failures across 3 test suites.

### 8.3 Live End-to-End API Gateway Verification
Verified via `http://localhost:8081` with JWT tokens and internal headers:
1. `GET /api/v1/domains/communities` -> Returned 10 domain communities (`Business`, `Web Development`, etc.).
2. `GET /api/v1/domains/web-development/community/messages` -> Returned historical domain chat messages with sender profile details.
3. `GET /api/v1/community/global/messages` -> Returned historical global chat messages with author metadata.
4. `GET /api/v1/community/users/mention-suggestions?q=dev` -> Returned matching user profiles (`devansh`, `Dev Malhotra`, etc.).
5. `POST /api/v1/community/global/messages` -> Returned `HTTP 201 Created` with new message ID 6.
6. `SELECT * FROM outbox_events` -> Confirmed outbox record was written atomically and relayed to Kafka topic `peergeek.community.message-created.v1` with `published = 1`.
7. `DELETE /api/v1/community/global/messages/6` -> Returned `HTTP 204 No Content`. Outbox event `peergeek.community.message-deleted.v1` published and message soft-deleted in database.
8. `GET /api/v1/admin/community/messages` -> Confirmed message 6 appears marked with `deleted: true`, `deleteReason: "Deleted by author"`.

---

## 9. Docker Compose Orchestration

The `community-service` is containerized and declared in `docker-compose.yml`:

```yaml
  community-service:
    build:
      context: ./community-service
      dockerfile: Dockerfile
    container_name: peergeek-community-service
    ports:
      - "8089:8089"
    environment:
      - SERVER_PORT=8089
      - DB_HOST=mysql
      - DB_PORT=3306
      - MYSQL_DATABASE=peergeek_community
      - MYSQL_USER=root
      - MYSQL_PASSWORD=achintyaC%02
      - KAFKA_BOOTSTRAP_SERVERS=kafka:29092
      - PEERGEEK_AUTH_JWTSECRET=dev_jwt_secret_must_be_at_least_32_characters_long_for_hmac
      - PEERGEEK_GATEWAY_SHARED_SECRET=pg-internal-gateway-secret-2026
      - AUTO_MIGRATE_COMMUNITY_DATA=true
    depends_on:
      mysql:
        condition: service_healthy
      kafka:
        condition: service_started
    healthcheck:
      test: ["CMD-SHELL", "wget -q -O - http://localhost:8089/actuator/health | grep UP || exit 1"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 30s
    networks:
      - default
```

---

## 10. Operational Runbook & Fallback Safeguards

1. **Service Independence:** If `community-service` is restarted or undergoes maintenance, core monolith features (courses, auth, wallet payments, video calls) continue operating without interruption.
2. **Zero-Risk Rollback:** Legacy tables in `peergeek` (`domain_communities`, `domain_chat_messages`, `global_community_messages`, `community_posts`) were not dropped or altered. In an emergency rollback, re-pointing API Gateway route `/api/v1/community/**` to `monolith-api` restores the legacy monolithic flow immediately.
3. **Kafka Fault Tolerance:** If Kafka is temporarily unreachable, outbox events accumulate safely in `peergeek_community.outbox_events` with `published = 0`. Once Kafka reconnects, `OutboxRelay` resumes publishing without data loss.

