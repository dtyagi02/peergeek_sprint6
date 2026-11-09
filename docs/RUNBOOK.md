# PeerGeek Operations Runbook

## 1. Overview & Service Architecture

PeerGeek is built as a production-grade, modular monolith using Spring Boot 4 / Java 21, React 19 / Vite, MySQL 8.4 (authoritative database), Redis 7.4 (disposable cache with in-memory degradation), and MinIO / AWS S3 (object storage).

```
                      +-------------------+
                      |   Client Browser  |
                      +---------+---------+
                                |
             (HTTPS / 443 via Reverse Proxy / Nginx)
                                |
         +----------------------+----------------------+
         |                                             |
+--------v--------+                           +--------v--------+
| React Frontend  |                           | Spring Boot App |
| (Nginx Runtime) |                           | (Modular Core)  |
+-----------------+                           +----+----+---+---+
                                                   |    |   |
                   +-------------------------------+    |   +-------------------+
                   |                                    |                       |
          +--------v--------+                  +--------v--------+     +--------v--------+
          |  MySQL 8.4 DB   |                  |  Redis 7.4      |     |  MinIO / AWS S3 |
          | (Authoritative) |                  | (Ephemeral Cache|     | (Object Storage)|
          +-----------------+                  +-----------------+     +-----------------+
```

---

## 2. Startup & Health Verification

### 2.1 Starting Services

```bash
# Start all infrastructure and application services via Compose
docker compose up -d

# Verify container statuses
docker compose ps
```

### 2.2 Health Probes

PeerGeek exposes structured Spring Boot Actuator endpoints for container orchestrators and monitoring probes:

- **Liveness Probe:** `GET /actuator/health/liveness`
  - Returns `{"status": "UP"}`
  - Probes process health without placing load on external dependencies.
- **Readiness Probe:** `GET /actuator/health/readiness`
  - Validates `db` and `objectStorage`.
  - Redis degradation does not fail readiness, allowing continuous service if cache restarts.
- **Composite Health:** `GET /actuator/health`
  - Detailed component status.
- **Application Info:** `GET /actuator/info`
  - Application version, git commit, and phase metadata.

---

## 3. Environment & Secrets Configuration

All configuration is driven by 12-factor environment variables. **Never commit real credentials to Git.**

| Variable | Description | Default / Example | Notes |
| :--- | :--- | :--- | :--- |
| `SPRING_PROFILES_ACTIVE` | Active Spring profile | `prod` / `dev` | Must be `prod` in production |
| `SERVER_PORT` | HTTP port | `8080` | Container port |
| `CORS_ALLOWED_ORIGINS` | Trusted origin allowlist | `https://peergeek.example.com` | Strict CORS policy |
| `PEERGEEK_AUTH_JWTSECRET` | JWT Signing Secret | Minimum 32-byte secret | Rotatable via secret manager |
| `MYSQL_DATABASE` | Primary database name | `peergeek` | Authoritative persistence |
| `MYSQL_USER` / `MYSQL_PASSWORD` | App database credentials | - | Least-privilege DB user |
| `MYSQL_ROOT_PASSWORD` | Root DB password | - | For migrations and admin |
| `REDIS_HOST` / `REDIS_PORT` | Cache cluster address | `redis:6379` | Disposable cache |
| `STORAGE_ENDPOINT` | S3 / MinIO endpoint | `http://minio:9000` | S3 compatible |
| `STORAGE_ACCESS_KEY` / `STORAGE_SECRET_KEY` | Storage credentials | - | S3 IAM user credentials |
| `STORAGE_BUCKET` | Main object storage bucket | `peergeek` | Isolated resource container |
| `STORAGE_PATH_STYLE` | S3 path style access | `true` (MinIO) / `false` (AWS) | Enable for MinIO |

---

## 4. Database Migrations & Rollback Procedures

### 4.1 Applying Migrations

Flyway applies additive migrations automatically at backend startup (`spring.flyway.enabled=true`).

```bash
# Manual check of schema version
SELECT version, description, success, installed_on 
FROM flyway_schema_history 
ORDER BY installed_rank DESC LIMIT 5;
```

### 4.2 Migration Rules

1. **Additive Only:** Never modify an applied migration file (`V1_001` through `V12_001`).
2. **Backward Compatible:** New columns must be `NULL` or have sensible defaults.
3. **No Destructive Drops:** Do not drop tables or columns in production without multi-phase deprecation.

### 4.3 Recovery from Failed Migration

1. Check Flyway history for `success = 0`:
   ```sql
   SELECT * FROM flyway_schema_history WHERE success = 0;
   ```
2. Manually rollback the partial DDL operations executed in that migration script.
3. Repair Flyway schema history:
   ```bash
   ./mvnw flyway:repair
   ```
4. Address the root cause in the script before restarting the backend container.

---

## 5. Backup & Disaster Recovery Procedures

### 5.1 Automated Backup Drill Script

PeerGeek includes an automated, non-destructive backup and restore drill script:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/backup-restore-drill.ps1
```

### 5.2 Manual Backup Procedure (MySQL)

```bash
# Perform non-locking consistent backup
mysqldump -h $DB_HOST -u root -p$MYSQL_ROOT_PASSWORD \
  --single-transaction \
  --quick \
  --routines \
  --triggers \
  --events \
  --default-character-set=utf8mb4 \
  peergeek > backup_peergeek_$(date +%Y%m%d_%H%M%S).sql
```

### 5.3 Manual Restore Procedure

```bash
# 1. Create clean target database
mysql -h $DB_HOST -u root -p$MYSQL_ROOT_PASSWORD -e "CREATE DATABASE peergeek_restore CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# 2. Restore data
mysql -h $DB_HOST -u root -p$MYSQL_ROOT_PASSWORD peergeek_restore < backup_peergeek_YYYYMMDD_HHMMSS.sql

# 3. Verify record counts
mysql -h $DB_HOST -u root -p$MYSQL_ROOT_PASSWORD peergeek_restore -e "SELECT count(*) FROM users; SELECT count(*) FROM courses;"
```

### 5.4 Object Storage Recovery

- **MinIO / AWS S3:** Enable S3 Bucket Versioning and S3 Lifecycle policies.
- **Ransomware / Deletion Protection:** Use S3 Object Lock in compliance/governance mode where supported.

---

## 6. S3 / MinIO Storage Troubleshooting

### Common Storage Issues

1. **Bucket Missing (`NoSuchBucket`):**
   - Ensure the initialization script or operator created the bucket before starting the app.
   - Run MinIO init client: `mc mb local/peergeek`.
2. **Presigned URL 403 Forbidden:**
   - Verify server clock synchronization (NTP). Clock drift > 15 minutes invalidates AWS presigned signatures.
   - Ensure `STORAGE_PUBLIC_ENDPOINT` matches the browser-accessible URL (e.g. `http://localhost:9000` or `https://cdn.peergeek.com`).
3. **Large File Uploads Failing:**
   - Check `PEERGEEK_COURSE_MAXIMUMCONTENTJSONBYTES` and S3 multipart size configs.
   - Nginx proxy buffer must allow headers up to 16KB (`client_max_body_size` and `large_client_header_buffers`).

---

## 7. Logging, Metrics & Error Investigation

### 7.1 Structured Log Format

Console output utilizes structured logs with correlation identifiers:
```
2026-10-21T04:12:18.936+05:30 INFO [main] [req-abc-123] c.p.security.RateLimitingFilter - Rate limit check passed for ip=...
```

- **Correlation ID:** Every request receives an `X-Request-Id` header and MDC tag `requestId`.
- **Sensitive Data Masking:** Passwords, refresh tokens, auth headers, and credit card numbers are strictly omitted from log statements.

### 7.2 Diagnosing Outages

1. **Inspect backend logs for unhandled exceptions:**
   ```bash
   docker logs peergeek-backend --tail 200 -f
   ```
2. **Check rate-limiting events:**
   - Filter logs for `Rate limit exceeded`. If legitimate users are throttled, adjust `peergeek.security.rate-limit.*` settings.
3. **Database connection pool exhaustion:**
   - Check HikariCP connection metrics: `HikariPool-1 - Connection is not available, request timed out`.
   - Solution: Increase `spring.datasource.hikari.maximum-pool-size` (default: 10) or inspect slow queries.

---

## 8. Deployment & Zero-Downtime Rollback

### 8.1 Production Deployment Steps

1. **Run CI/CD Release Pipeline:**
   - Verify frontend lint, test, format, build.
   - Verify backend unit tests, integration tests, and authorization matrix.
2. **Build and Tag Production Images:**
   - `peergeek-backend:<version>`
   - `peergeek-frontend:<version>`
3. **Execute Pre-flight DB Migrations:**
   - Migrations apply automatically upon boot.
4. **Deploy Containers with Health Checks:**
   - Rolling update or blue/green switchover once `/actuator/health/readiness` reports `UP`.

### 8.2 Rollback Runbook

If critical regression occurs post-deployment:

1. **Frontend Rollback:** Revert Nginx container image tag to previous stable build. (Zero schema impact).
2. **Backend Rollback:** Revert backend container image tag to previous stable build. Because all DB migrations are strictly additive, previous backend versions remain compatible with the additive schema.
3. **Cache Flush:** If corrupted payload was cached in Redis:
   ```bash
   docker exec -it peergeek-redis redis-cli FLUSHDB
   ```
