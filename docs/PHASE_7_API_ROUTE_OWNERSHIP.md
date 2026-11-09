# Phase 7 — API Gateway Route Ownership & Ingress Specification

**Document Version:** 1.0.0  
**Cutover Date:** 2026-11-09  
**Gateway Configuration:** `api-gateway/src/main/resources/application.yml`  
**Gateway Ingress Port:** `8081`  
**Monolith Fallback Status:** **PERMANENTLY REMOVED** (`monolith-api` and `monolith-ws` deleted)

---

## 1. Executive Summary

As part of Phase 7 (Monolith Retirement & Final Cutover), the Spring Cloud API Gateway (`8081`) has been reconfigured as the authoritative ingress for PeerGeek. 

**Zero application traffic is routed to the legacy monolith (`backend:8080`).** 
100% of ingress requests are now resolved by 7 independent Spring Boot microservices. 

The fallback route:
```yaml
# DELETED PERMANENTLY:
# - id: monolith-api
#   uri: http://localhost:8080
#   predicates:
#     - Path=/api/v1/**
# - id: monolith-ws
#   uri: ws://localhost:8080
#   predicates:
#     - Path=/ws/**
```
has been eliminated. Requests matching unregistered paths receive a standardized `404 Not Found` response directly from the Gateway rather than leaking to a legacy backend.

---

## 2. Definitive Route Ownership Matrix

| Route ID | Ingress Path Predicate | HTTP Methods | Target Microservice Container | Target Port | Database Schema | Primary Responsibilities |
|---|---|---|---|---:|---|---|
| `identity-service` | `/api/v1/auth/**`<br>`/api/v1/users/**`<br>`/api/v1/admin/users/**` | `GET`, `POST`, `PUT`, `PATCH`, `DELETE` | `peergeek-identity-test` | `8091` | `peergeek_identity` | User authentication, registration, OTPs, profile management, avatar upload/serving, admin user management. |
| `course-learning-service` | `/api/v1/categories/**`<br>`/api/v1/courses/**`<br>`/api/v1/learning/**`<br>`/api/v1/instructor/**`<br>`/api/v1/certificates/**`<br>`/api/v1/admin/certificates/**`<br>`/api/v1/admin/courses/**`<br>`/api/v1/bookmarks/**`<br>`/api/v1/public/courses/**`<br>`/api/v1/public/categories/**`<br>`/api/v1/admin/reports/**`<br>`/api/v1/admin/dashboard/**`<br>`/api/v1/admin/discovery/**` | `GET`, `POST`, `PUT`, `PATCH`, `DELETE` | `peergeek-course-learning-test` | `8090` | `peergeek_learning` | Course creation, publishing, catalog discovery, progress tracking, assignments, grading, certificates, lesson notes, bookmarks, reviews, issue reports, instructor analytics, and admin dashboard. |
| `doubt-session-service` | `/api/v1/doubt-sessions/**`<br>`/api/v1/instructor/doubt-sessions/**`<br>`/api/v1/instructor/courses/*/doubt-session-settings`<br>`/api/v1/public/courses/*/doubt-session-availability` | `GET`, `POST`, `PUT`, `DELETE` | `peergeek-doubt-session-test` | `8092` | `peergeek_doubt_session` | 1-on-1 doubt session scheduling, LiveKit token issuance, room webhooks, session completion, and dispute settlement. |
| `community-service` | `/api/v1/community/**`<br>`/api/v1/domains/**`<br>`/api/v1/admin/community/**`<br>`/api/v1/direct-messages/**` | `GET`, `POST`, `PUT`, `PATCH`, `DELETE` | `peergeek-community-test` | `8089` | `peergeek_community` | Domain community chat, global community feed, moderation blocking, community posts & comments, and 1-to-1 direct messaging. |
| `community-ws` | `/ws/**` | `WebSocket` (Upgrade) | `peergeek-community-test` | `8089` | `peergeek_community` | Full-duplex WebSocket STOMP messaging for domain chat, global chat, and direct messaging read states. |
| `wallet-service` | `/api/v1/wallet/**` | `GET`, `POST` | `peergeek-wallet-payment-test` | `8088` | `peergeek_wallet` | GeekCoins ledger, balances, transactions, reservations, holds, releases, and recharges. |
| `payment-service` | `/api/v1/payments/**` | `POST` | `peergeek-wallet-payment-test` | `8088` | `peergeek_wallet` | Razorpay order generation, signature verification, webhooks, and ledger credits. |
| `notification-service` | `/api/v1/notifications/**` | `GET`, `POST`, `PATCH`, `DELETE` | `peergeek-notification-test` | `8087` | `peergeek_notification` | In-app notification center, unread counts, mark-as-read, and email dispatch queue. |
| `referral-service` | `/api/v1/referrals/**` | `GET`, `POST` | `peergeek-referral-test` | `8093` | `peergeek_referral` | Referral invitation codes, invitation dispatch, code validation, reward summary, and history. |

---

## 3. WebSocket Routing Architecture

WebSocket ingress traffic (`/ws/**`) was previously mapped to the monolith fallback `ws://backend:8080`.
Under Phase 7, WebSocket ingress is routed directly to `community-service`:
```yaml
- id: community-ws
  uri: ${COMMUNITY_WS_URL:ws://peergeek-community-test:8089}
  predicates:
    - Path=/ws/**
```
`community-service` hosts the STOMP message broker endpoint at `/ws` with SockJS fallback support, handling:
- `/topic/domains/{domainId}` — Domain chat realtime messages.
- `/topic/global` — Global community discussion messages.
- `/user/queue/direct-messages/{conversationId}` — Private 1-to-1 direct messages.

---

## 4. Security & Inter-Service Trust Architecture

1. **Edge Ingress Sanitization:**
   - The Gateway's `CorrelationIdFilter` inspects all incoming requests and strips any external `X-Gateway-Secret` header before routing downstream:
     ```java
     ServerHttpRequest mutatedRequest = request.mutate()
             .header(CORRELATION_ID_HEADER, finalCorrelationId)
             .headers(httpHeaders -> httpHeaders.remove("X-Gateway-Secret"))
             .build();
     ```
   - This ensures that clients cannot spoof internal trust headers.

2. **JWT Authentication & Forwarding:**
   - External clients authenticate via `POST /api/v1/auth/login` and receive a signed HMAC-SHA256 JWT access token.
   - The `Authorization: Bearer <token>` header is passed intact through the Gateway to destination microservices.
   - Each microservice verifies token authenticity and claims (`userId`, `role`) using the configured `PEERGEEK_AUTH_JWTSECRET`.

3. **Internal Service-to-Service Trust:**
   - Internal RPC requests (such as `referral-service` -> `wallet-payment-service` reward payouts or `doubt-session-service` -> `wallet-payment-service` balance holds) communicate directly over the private Docker network `peergeek20_default`.
   - Internal calls supply the header `X-Gateway-Secret: pg-internal-gateway-secret-2026`.
   - Microservice security filters validate `X-Gateway-Secret` before honoring internal service endpoints under `/api/v1/internal/**`.

---

## 5. Verification Evidence

Direct verification against API Gateway (`8081`) confirmed proper routing and exclusion of monolith:
- `GET http://localhost:8081/api/v1/public/courses/popular` -> `200 OK` (Served by `course-learning-assessment-service:8090`).
- `GET http://localhost:8081/api/v1/categories` -> `200 OK` (Served by `course-learning-assessment-service:8090`).
- `GET http://localhost:8081/api/v1/referrals/config` -> `200 OK` (Served by `referral-service:8093`).
- `GET http://localhost:8081/api/v1/users/by-username/vishnusharma` -> `200 OK` (Served by `identity-service:8091`).
- `GET http://localhost:8081/api/v1/direct-messages/conversations` -> `200 OK` (Served by `community-service:8089`).
- `POST http://localhost:8081/api/v1/auth/login` -> Handled by `identity-service:8091`.
- `GET http://localhost:8080/` -> **Connection Refused** (Monolith completely offline).

