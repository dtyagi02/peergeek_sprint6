# ==============================================================================
# PeerGeek — Phase 12 Operational Drill: Backup & Restore Verification
# ==============================================================================
# This drill executes an isolated, non-destructive backup and restore verification
# against an ephemeral MySQL 8.4 container, validating schema integrity, record
# preservation, and recovery procedures without touching developer working data.
# ==============================================================================

$ErrorActionPreference = "Continue"
$drillDate = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss UTC")
$timestamp = (Get-Date).ToString("yyyyMMdd_HHmmss")
$logPath = "docs/backup_restore_drill.log"
$backupFile = "docs/peergeek_drill_backup_${timestamp}.sql"
$containerName = "peergeek-drill-mysql-${timestamp}"

function Log-Message($msg) {
    $entry = "[$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')] $msg"
    Write-Host $entry
    Add-Content -Path $logPath -Value $entry
}

# Ensure docs directory exists
if (-not (Test-Path "docs")) {
    New-Item -ItemType Directory -Path "docs" | Out-Null
}

Log-Message "================================================================="
Log-Message "PeerGeek Production Backup & Restore Drill Starting: $drillDate"
Log-Message "================================================================="

try {
    # 1. Start ephemeral MySQL container
    Log-Message "Step 1: Starting isolated disposable MySQL 8.4 container: $containerName..."
    $containerId = (docker run -d --name $containerName -e MYSQL_DATABASE=peergeek -e MYSQL_ROOT_PASSWORD=drillroot -e MYSQL_USER=peergeek -e MYSQL_PASSWORD=drillpass -p 3307:3306 mysql:8.4.5).Trim()
    Log-Message "Container launched ($containerId). Waiting for MySQL health..."

    # Wait for MySQL to become ready
    $ready = $false
    for ($i = 0; $i -lt 30; $i++) {
        Start-Sleep -Seconds 2
        $ping = docker exec $containerName mysqladmin ping -h 127.0.0.1 -uroot -pdrillroot --silent 2>$null
        if ($LASTEXITCODE -eq 0) {
            $ready = $true
            break
        }
    }
    if (-not $ready) {
        throw "MySQL container failed to start in 60s"
    }
    Log-Message "MySQL is ready for drill operations."

    # 2. Seed baseline schema and records into primary drill database
    Log-Message "Step 2: Populating primary database 'peergeek' with baseline schema..."
    
    # Run flyway migration SQL files in order or seed baseline
    $migrationSql = @"
CREATE TABLE flyway_schema_history (
    installed_rank INT NOT NULL,
    version VARCHAR(50),
    description VARCHAR(200) NOT NULL,
    type VARCHAR(20) NOT NULL,
    script VARCHAR(1000) NOT NULL,
    checksum INT,
    installed_by VARCHAR(100) NOT NULL,
    installed_on TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    execution_time INT NOT NULL,
    success BOOLEAN NOT NULL,
    PRIMARY KEY (installed_rank)
);
INSERT INTO flyway_schema_history VALUES (1, '12.001', 'performance indexes', 'SQL', 'V12_001__performance_indexes.sql', 12345, 'peergeek', CURRENT_TIMESTAMP, 15, 1);

CREATE TABLE categories (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    slug VARCHAR(100) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
INSERT INTO categories (name, slug) VALUES ('Software Engineering', 'software-engineering'), ('System Design', 'system-design');

CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    role VARCHAR(30) NOT NULL DEFAULT 'LEARNER',
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
INSERT INTO users (email, password_hash, display_name, role) VALUES 
('instructor@drill.local', '`$2a`$10`$hash1', 'Drill Instructor', 'INSTRUCTOR'),
('learner@drill.local', '`$2a`$10`$hash2', 'Drill Learner', 'LEARNER');

CREATE TABLE courses (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    instructor_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    slug VARCHAR(200) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PUBLISHED',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
INSERT INTO courses (instructor_id, category_id, title, slug, status) VALUES 
(1, 1, 'Production Hardening Mastery', 'production-hardening-mastery', 'PUBLISHED');
"@

    docker exec -i $containerName mysql -uroot -pdrillroot peergeek -e "$migrationSql" 2>$null
    Log-Message "Baseline schema and test data successfully created."

    # 3. Execute mysqldump backup
    Log-Message "Step 3: Creating backup artifact using mysqldump..."
    docker exec $containerName mysqldump -uroot -pdrillroot --single-transaction --routines --triggers --events peergeek 2>$null > $backupFile
    
    if (-not (Test-Path $backupFile) -or (Get-Item $backupFile).Length -lt 100) {
        throw "Backup artifact was not created or is suspiciously small"
    }
    $fileSize = (Get-Item $backupFile).Length
    Log-Message "Backup artifact created successfully ($fileSize bytes) at $backupFile."

    # 4. Create isolated disposable recovery database
    Log-Message "Step 4: Provisioning isolated recovery database 'peergeek_drill_restore'..."
    docker exec $containerName mysql -uroot -pdrillroot -e "CREATE DATABASE peergeek_drill_restore CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;" 2>$null

    # 5. Restore backup artifact into recovery database
    Log-Message "Step 5: Restoring backup artifact into 'peergeek_drill_restore'..."
    Get-Content $backupFile | docker exec -i $containerName mysql -uroot -pdrillroot peergeek_drill_restore 2>$null
    Log-Message "Restoration command completed."

    # 6. Verification queries
    Log-Message "Step 6: Verifying restored schema and data integrity..."
    
    # Check tables
    $tableCount = (docker exec $containerName mysql -uroot -pdrillroot peergeek_drill_restore -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'peergeek_drill_restore';" 2>$null).Trim()
    Log-Message "Restored table count: $tableCount"
    if ([int]$tableCount -lt 4) {
        throw "Table count mismatch in restored database: expected at least 4 tables, got $tableCount"
    }

    # Check categories
    $catCount = (docker exec $containerName mysql -uroot -pdrillroot peergeek_drill_restore -N -e "SELECT COUNT(*) FROM categories;" 2>$null).Trim()
    Log-Message "Restored categories: $catCount (Expected: 2)"
    if ([int]$catCount -ne 2) {
        throw "Category count mismatch: expected 2, got $catCount"
    }

    # Check users
    $userCount = (docker exec $containerName mysql -uroot -pdrillroot peergeek_drill_restore -N -e "SELECT COUNT(*) FROM users;" 2>$null).Trim()
    Log-Message "Restored users: $userCount (Expected: 2)"
    if ([int]$userCount -ne 2) {
        throw "User count mismatch: expected 2, got $userCount"
    }

    # Check courses
    $courseTitle = (docker exec $containerName mysql -uroot -pdrillroot peergeek_drill_restore -N -e "SELECT title FROM courses LIMIT 1;" 2>$null).Trim()
    Log-Message "Restored course title: '$courseTitle'"
    if ($courseTitle -ne "Production Hardening Mastery") {
        throw "Course title mismatch in restored data: got '$courseTitle'"
    }

    Log-Message "================================================================="
    Log-Message "DRILL RESULT: SUCCESS"
    Log-Message "All recovery verifications passed without data loss or corruption."
    Log-Message "================================================================="

} catch {
    Log-Message "DRILL ERROR: $_"
    throw $_
} finally {
    # 7. Cleanup disposable container
    Log-Message "Step 7: Tearing down isolated drill container..."
    docker rm -f $containerName 2>$null | Out-Null
    Log-Message "Ephemeral container removed. Drill complete."
}
