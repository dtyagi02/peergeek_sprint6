# Phase 6 — Identity & Doubt Session Data Reconciliation Report

**Document Version:** 1.0.0  
**Audit Date:** 2026-11-08  
**Source Monolith Database:** `peergeek`  
**Target Microservice Databases:** `peergeek_identity`, `peergeek_doubt_session`  
**Audit Status:** **PASS — 100% RECONCILED & CONVERGENT**

---

## 1. Executive Summary

As part of the **PeerGeek Phase 6 Closure Audit**, a full database reconciliation was performed to verify historical data migration, entity integrity, BCrypt password hash preservation, and schema correctness for the extracted Identity and Doubt Session bounded contexts.

Every record from the monolith database (`peergeek`) was verified against the new dedicated databases (`peergeek_identity` and `peergeek_doubt_session`). Zero record loss, zero field truncation, and zero role/permission mismatches were detected.

---

## 2. Table-by-Table Data Reconciliation Matrix

| Domain | Table Name | Monolith (`peergeek`) | Microservice Database | Microservice Count | Reconciliation Status |
|---|---|---|---|---|---|
| **Identity** | `users` | 28 | `peergeek_identity.users` | 28 | **100% Match (0 missing)** |
| **Identity** | `refresh_tokens` | 302 | `peergeek_identity.refresh_tokens` | 306* | **100% Historical Match (+4 new audit session tokens)** |
| **Identity** | `auth_otps` | 14 | `peergeek_identity.auth_otps` | 14 | **100% Match** |
| **Identity** | `password_reset_otps` | 6 | `peergeek_identity.password_reset_otps` | 6 | **100% Match** |
| **Identity** | `user_educations` | 0 | `peergeek_identity.user_educations` | 0 | **100% Match (Schema verified)** |
| **Identity** | `user_projects` | 0 | `peergeek_identity.user_projects` | 0 | **100% Match (Schema verified)** |
| **Doubt Session**| `course_doubt_session_settings` | 1 | `peergeek_doubt_session.course_doubt_session_settings` | 1 | **100% Match** |
| **Doubt Session**| `doubt_sessions` | 0 | `peergeek_doubt_session.doubt_sessions` | 0 | **100% Match** |

*\*Note: 302 historical refresh tokens migrated from monolith plus 4 newly issued tokens generated during live audit gateway integration tests.*

---

## 3. Identity Domain Detailed Verification

### A. User Accounts, Roles & Statuses
A full aggregation query was executed comparing user role distributions between `peergeek.users` and `peergeek_identity.users`:

```sql
SELECT role, mode, status, enabled, email_verified, count(*) 
FROM users 
GROUP BY role, mode, status, enabled, email_verified;
```

**Reconciliation Result:**
- `ROLE = ADMIN, MODE = INSTRUCTOR, STATUS = ACTIVE, ENABLED = 1, VERIFIED = 1`: **1 user** (User ID 19 / `admin@peergeek.local`)
- `ROLE = USER, MODE = STUDENT, STATUS = ACTIVE, ENABLED = 1, VERIFIED = 1`: **27 users**
- **Total Users:** Monolith = **28**, `peergeek_identity` = **28**
- **Variance:** **0**

### B. BCrypt Password Compatibility
- All password hashes migrated directly with BCrypt cost factors ($2a$10$ and $2a$12$).
- Verified password hashes were not re-hashed, truncated, or modified.
- Live test login via API Gateway using `admin@peergeek.local` successfully authenticated through `identity-service`, verifying full password compatibility without requiring user password resets.

### C. Refresh Token Family Replay Protection
- Refresh tokens utilize family-based tracking (`family_id`, `token_hash`, `revoked`, `replaced_by_token`, `expires_at`).
- All 302 historical tokens maintained their original family relationships and revocation states.
- Pessimistic write locks (`PESSIMISTIC_WRITE`) are enforced during token rotation to guarantee atomic token advancement and prevent replay races.

### D. OTP Reconciliation
- Registration OTPs (`auth_otps`): 14 records verified with identical timestamps, expiration times, and status flags.
- Password Reset OTPs (`password_reset_otps`): 6 records verified with identical email addresses and expiry windows.

---

## 4. Doubt Session Domain Detailed Verification

### Course Doubt Session Settings
Query executed against `peergeek_doubt_session.course_doubt_session_settings`:

| ID | Course ID | Instructor ID | Enabled | Price / Min (PGC) | Max Duration (Min) | Active |
|---|---|---|---|---|---|---|
| 1 | 1 | 19 | 1 | 5.00 | 60 | 1 |

Exact match with monolith record. Availability endpoint `/api/v1/public/courses/1/doubt-session-availability` correctly returns instructor availability and pricing.

---

## 5. Schema & Foreign Key Integrity

1. **Schema Isolation:** `peergeek_identity` and `peergeek_doubt_session` schemas are completely decoupled from `peergeek`. No cross-database joins or cross-database foreign key constraints exist.
2. **Flyway Migrations:** All tables are managed by versioned Flyway migrations (`V1_001__create_identity_tables.sql`, `V1_001__create_doubt_session_tables.sql`).
3. **Data Types & Enums:** All varchar lengths, timestamps (UTC with microsecond precision `timestamp(6)`), and decimal representations (`DECIMAL(19,2)`) are aligned.

