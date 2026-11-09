# PeerGeek authentication, profiles, and audit

Phase 1 adds a single account model. `USER` and `ADMIN` are authorization roles;
`STUDENT` and `INSTRUCTOR` are only the user's saved interface mode. A user can
switch modes without changing permissions.

## Session lifecycle

- Registration creates a `USER`, stores a BCrypt password hash (strength 12),
  and returns an access JWT plus a `PEERGEEK_REFRESH` cookie.
- Passwords must be 8–72 characters and at most 72 UTF-8 bytes (BCrypt's
  maximum input size), and cannot be the normalized email.
- Emails are trimmed and lower-cased with `Locale.ROOT` before persistence.
- Username is mandatory during registration and represents the user's public
  identity across PeerGeek (profile, author tags, and future @mentions and
  community discussions). Email and password remain the sole credentials used
  for login.
- Usernames must be 3–30 characters, begin with an English letter (`a-z`,
  `A-Z`), and contain only letters, numbers, and underscores (`_`). Whitespace is
  trimmed and usernames are stored canonically in lowercase (`Locale.ROOT`).
  Uniqueness is enforced case-insensitively at both the application level and
  via database unique constraint (`uk_users_username`). Reserved usernames
  (`admin`, `administrator`, `support`, `system`, `api`, `root`) are rejected.
- Access JWTs last 900 seconds by default and contain the numeric user ID in
  `sub`, the current role, `iat`, and `exp`. A request re-reads the user from
  MySQL and rejects deleted or disabled accounts.
- Refresh values are generated from 256 random bits. Only their SHA-256 hashes
  are stored in `refresh_tokens`. Each use rotates the value. Reuse of a
  revoked/replaced token revokes every active row in that token family.
- The raw refresh value is sent only as an HttpOnly cookie. The cookie is
  scoped to `/api/v1/auth`, uses `SameSite=Lax`, and is `Secure=false` in the
  local `dev` configuration. Production requires
  `PEERGEEK_AUTH_REFRESHCOOKIESECURE=true`.
- The browser stores the access JWT only in the in-memory Zustand store. No
  token is written to localStorage or sessionStorage. App startup makes one
  refresh request before rendering the shell; concurrent 401 responses share a
  single refresh and retry each original request once.

The signing key is provided by `PEERGEEK_AUTH_JWTSECRET` and must contain at
least 32 UTF-8 bytes. The development fallback is not accepted in `prod`.
Refresh/access/OTP lifetimes and the cookie flag are typed under
`com.peergeek.config.AuthSettings`.

### Example registration request

```http
POST /api/v1/auth/register HTTP/1.1
Content-Type: application/json

{
  "displayName": "Devansh Tyagi",
  "username": "devansh_tyagi",
  "email": "devansh@example.com",
  "password": "CorrectHorseBatteryStaple123!"
}
```

## Endpoints

| Method | Path | Access |
| --- | --- | --- |
| `POST` | `/api/v1/auth/register` | Public |
| `POST` | `/api/v1/auth/login` | Public |
| `POST` | `/api/v1/auth/refresh` | Public; HttpOnly refresh cookie required |
| `POST` | `/api/v1/auth/logout` | Authenticated |
| `POST` | `/api/v1/auth/forgot-password` | Public; always returns the same 202 body |
| `POST` | `/api/v1/auth/reset-password` | Public |
| `GET` | `/api/v1/users/me` | Authenticated |
| `PATCH` | `/api/v1/users/me` | Authenticated |
| `PATCH` | `/api/v1/users/me/mode` | Authenticated |
| `GET` | `/api/v1/users/{id}/public` | Public |
| `GET` | `/api/v1/users/by-username/{username}` | Public |

The production public endpoint allowlist has one source of truth:
`com.peergeek.security.PublicEndpoints`. Swagger/OpenAPI is public only in the
`dev` profile. Actuator health and info are public. The `test` profile also
permits only the test controllers and exact unmatched-route regression
fixture; these matchers are not enabled in `dev` or `prod`.

## Password reset and anti-enumeration

Forgot-password requests always return HTTP 202 and the same generic JSON
message, whether the email exists, is disabled, or is under the 60-second
resend cooldown. Unknown and cooldown-limited requests perform a dummy BCrypt
hash, and email delivery is queued on a bounded background executor, so SMTP
latency does not distinguish known accounts in the response. The email delivery
is not described in the response. The OTP is a random six-digit code, is
BCrypt-hashed before storage, expires after ten minutes, and allows at most
five failed attempts. The fifth failed attempt burns it. A successful reset
consumes the code and revokes every refresh token for that user.

Mail is sent through the `MailSender` abstraction. Compose connects it to
Mailpit at <http://localhost:8025>. The code is present only in the email body;
neither the API response nor application logs contain it.

Login responds with the same generic 401 error for a missing account, disabled
account, or incorrect password. Missing accounts still perform a dummy BCrypt
comparison. Failed and successful login attempts are audited without storing
the email or password in audit details.

## Errors and security notes

Authentication failures use the frozen standard error object with code
`UNAUTHORIZED`; authorization failures use `FORBIDDEN`. Validation errors use
`VALIDATION_FAILED`, duplicate accounts use `CONFLICT`, and invalid/expired/
burned OTPs share one generic validation response.

Spring Security sessions are stateless and CSRF is disabled because API
authorization uses explicit bearer headers rather than ambient session
cookies. The refresh cookie is the only credential cookie; it is scoped to the
auth path, HttpOnly, and `SameSite=Lax`. Credentialed CORS is restricted to
configured exact origins. CORS is not itself treated as CSRF protection.
Production must use HTTPS and the Secure cookie flag. This foundation does not
add an IP-based login rate limiter; downstream deployments should apply edge
rate controls without changing the generic login/reset responses.

Never log or return passwords, hashes, OTPs, refresh values, or token hashes.
Request identifiers are safe to log; audit details contain only stable action
and entity metadata.

## Protecting a later-phase endpoint

Keep the controller thin and make the authorization decision in the service.
Use `CurrentUserProvider` rather than importing Spring Security into a feature
controller or service:

```java
@RestController
@RequestMapping("/api/v1/example")
class ExampleController {
    private final CurrentUserProvider currentUser;
    private final ExampleService service;

    @GetMapping("/{id}")
    ExampleDto get(@PathVariable long id) {
        return service.get(id, currentUser.requireCurrentUser().id());
    }
}
```

```java
@Service
class ExampleService {
    private final OwnershipGuard ownership;

    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    ExampleDto get(long id, long currentUserId) {
        Example entity = load(id);
        ownership.requireOwner(entity.getOwnerUserId(), currentUserId);
        return ExampleDto.from(entity);
    }
}
```

Use `requireOwner` when a non-owner should receive `403 FORBIDDEN`. Use
`requireOwnerOrNotFound` when revealing whether a resource exists would expose
sensitive information. Frontend route visibility and navigation filtering are
UX only; neither replaces service authorization.

## Audit contract

Call `AuditService.record(new AuditEvent(actorId, action, entityType, entityId,
details))` for security-sensitive changes. Details must be small and must not
contain user-provided secrets, credentials, hashes, or token values. The
service captures the request IP and user agent. Audit rows are append-only:
there is no audit repository or update/delete path. A failed security audit
write propagates and prevents that operation from reporting success. Phase 1
does not create read-style audit events; future read events must use a
best-effort write path and must not fail the read.

The optional bootstrap administrator is created at startup only when both
`PEERGEEK_BOOTSTRAP_ADMIN_EMAIL` and `PEERGEEK_BOOTSTRAP_ADMIN_PASSWORD` are
configured and there is no existing `ADMIN`. If the email belongs to an
existing user, the initializer promotes that account and sets the configured
bootstrap password. Bootstrap values are never logged.
