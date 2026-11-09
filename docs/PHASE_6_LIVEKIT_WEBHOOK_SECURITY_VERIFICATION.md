# Phase 6 — LiveKit Webhook Security & Event Ordering Verification Report

**Document Version:** 1.0.0  
**Audit Date:** 2026-11-08  
**Audited Service:** `doubt-session-service` (`peergeek_doubt_session`, Port `8092`)  
**Audit Status:** **PASS — FULLY HARDENED & VERIFIED**

---

## 1. Executive Summary

As part of the **PeerGeek Phase 6 Microservices Extraction Closure Audit**, a focused verification of real-time video infrastructure security and webhook reliability was performed.

The audit verified:
1. Cryptographic HMAC-SHA256 signature verification on all incoming LiveKit webhooks.
2. Support for both standard (`/api/v1/doubt-sessions/webhooks/livekit`) and legacy (`/api/v1/doubt-sessions/webhook`) ingress paths.
3. Strict rejection (HTTP 401 Unauthorized) of unauthenticated, forged, or malformed webhook payloads.
4. Participant identity authentication preventing rogue participants from manipulating session billing.
5. Out-of-order and duplicate webhook resilience ensuring non-destructive billable duration calculation.

---

## 2. Ingress Endpoints & Security Architecture

LiveKit Server delivers room lifecycle and participant presence events via HTTP POST webhooks with an `application/webhook+json` payload.

| Route | Method | Security Policy | Implementation Controller |
|---|---|---|---|
| `/api/v1/doubt-sessions/webhooks/livekit` | `POST` | Public in `SecurityConfig`, authenticated by LiveKit SDK HMAC signature | `LiveKitWebhookController.java` |
| `/api/v1/doubt-sessions/webhook` | `POST` | Public in `SecurityConfig` (legacy compatibility alias), authenticated by LiveKit SDK HMAC signature | `LiveKitWebhookController.java` |

### Security Filter & Signature Validation Pipeline

1. **Spring Security Bypass:** The webhook routes are explicitly registered with `.permitAll()` in `SecurityConfig.java` so that LiveKit servers are not blocked by the default Bearer JWT filter.
2. **Gateway Header Isolation:** In `JwtAuthenticationFilter.java`, untrusted internal identity headers (`X-User-Id`) cannot be spoofed to gain elevated access.
3. **LiveKit Webhook Receiver:** `LiveKitService.receiveWebhook(rawBody, authHeader)` invokes `com.livekit.server.WebhookReceiver`:
   - Validates the `Authorization` header containing an Auth0 JWT signed with `LIVEKIT_API_SECRET`.
   - Computes the SHA-256 digest of `rawBody` and compares it to the `sha256` claim inside the token payload.
   - Throws an exception if the signature or payload digest does not match.
4. **401 Unauthorized Rejection:** If signature validation fails or the header is missing, `LiveKitWebhookController` catches the failure and immediately returns `401 Unauthorized`.

---

## 3. Webhook Security Test Suite & Results

The live webhook endpoints were probed using automated scripts (`scratch/test_livekit_webhook_security.ps1`):

| Test Case | Target Path | Authorization Header | Payload | Expected Status | Actual Status | Result |
|---|---|---|---|---|---|---|
| **Missing Header** | `/webhooks/livekit` | None | Valid JSON event | `401 Unauthorized` | `401 Unauthorized` | **PASS** |
| **Forged Signature** | `/webhooks/livekit` | `Bearer forged.fake.token` | Valid JSON event | `401 Unauthorized` | `401 Unauthorized` | **PASS** |
| **Missing Header (Legacy)** | `/webhook` | None | Valid JSON event | `401 Unauthorized` | `401 Unauthorized` | **PASS** |
| **Forged Signature (Legacy)** | `/webhook` | `Bearer forged.fake.token` | Valid JSON event | `401 Unauthorized` | `401 Unauthorized` | **PASS** |

Both endpoints strictly reject unauthenticated requests. No unauthorized webhook can trigger business logic or mutate session records.

---

## 4. Participant Identity Authorization Hardening

In multi-tenant video sessions, stray or malicious participants must not be allowed to corrupt room state or trigger unearned billing.

In `DoubtSessionService.java`:
- Participant identity strings are generated with predictable prefixes: `student_{studentId}` and `instructor_{instructorId}`.
- Webhook handlers (`handleParticipantJoined`, `handleParticipantLeft`) verify:
  ```java
  boolean isStudent = identity != null && (identity.equals("student_" + session.getStudentUserId()) || identity.equals(String.valueOf(session.getStudentUserId())));
  boolean isInstructor = identity != null && (identity.equals("instructor_" + session.getInstructorUserId()) || identity.equals(String.valueOf(session.getInstructorUserId())));

  if (!isStudent && !isInstructor) {
      log.warn("Ignoring webhook participant event: identity={} does not match session student={} or instructor={}",
              identity, session.getStudentUserId(), session.getInstructorUserId());
      return;
  }
  ```
- Unauthorized participant joins/leaves are immediately logged and discarded without modifying session duration or timestamps.

---

## 5. Resilience to Out-of-Order & Duplicate Events

LiveKit webhooks operate over asynchronous HTTP and can be duplicated or delivered out of sequence. `DoubtSessionService` applies defensive state-machine guarantees:

### 1. Duplicate `participant_joined` Events
- **Risk:** If an instructor or student experiences packet loss, LiveKit may re-send `participant_joined`. If the service simply overwrote `sessionStartedAt = Instant.now()`, already accrued billable time would be erased.
- **Hardening:**
  ```java
  if (session.getStudentJoinedAt() != null && session.getInstructorJoinedAt() != null) {
      // Both already connected; preserve accrued billable duration monotonically
      if (session.getSessionStartedAt() != null) {
          long elapsed = Duration.between(session.getSessionStartedAt(), now).getSeconds();
          if (elapsed > 0) {
              session.setBillableDurationSeconds(session.getBillableDurationSeconds() + (int) elapsed);
              session.setSessionStartedAt(now);
          }
      }
  }
  ```
- **Outcome:** Monotonic duration accrual without time loss.

### 2. Duplicate `participant_left` Events
- **Risk:** LiveKit retrying a `participant_left` webhook could cause duplicate duration calculations or premature finalization.
- **Hardening:** If a participant has already left (`getStudentJoinedAt() == null`), the duplicate event is detected as a no-op and ignored.

### 3. Idempotent `room_finished` Finalization
- **Risk:** Duplicate room termination signals could cause duplicate wallet settlements.
- **Hardening:**
  ```java
  if (session.getStatus() == DoubtSessionStatus.COMPLETED || session.getStatus() == DoubtSessionStatus.CANCELLED) {
      log.info("Doubt session {} already finalized ({}), ignoring room_finished webhook",
              session.getSessionUid(), session.getStatus());
      return;
  }
  ```
- **Outcome:** The session is finalized exactly once. Transactional outbox events (`DoubtSessionFinalized`) and wallet settlements are dispatched once per session.

---

## 6. Service Test Verification

All automated tests in `doubt-session-service` verifying the full lifecycle and event processing passed:
- `DoubtSessionServiceApplicationTests`: **6/6 passed (100%)**
- Unit & integration verification for room token generation, availability calculation, session booking, cancellation, and settlement: **PASS**

