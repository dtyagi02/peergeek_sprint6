-- Step 1: Add reserved_balance column to wallets table
ALTER TABLE wallets
    ADD COLUMN reserved_balance DECIMAL(19, 2) NOT NULL DEFAULT 0.00;

-- Step 2: Create wallet_transactions audit ledger
CREATE TABLE IF NOT EXISTS wallet_transactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    wallet_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    type VARCHAR(32) NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    balance_after DECIMAL(19, 2) NOT NULL,
    reserved_after DECIMAL(19, 2) NOT NULL,
    reference_type VARCHAR(64) NULL,
    reference_id VARCHAR(64) NULL,
    description VARCHAR(255) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_wallet_tx_wallet FOREIGN KEY (wallet_id) REFERENCES wallets (id) ON DELETE CASCADE,
    CONSTRAINT fk_wallet_tx_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_wallet_tx_user (user_id, created_at),
    INDEX idx_wallet_tx_ref (reference_type, reference_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Step 3: Create course_doubt_session_settings for instructor per-course configuration
CREATE TABLE IF NOT EXISTS course_doubt_session_settings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    course_id BIGINT NOT NULL,
    instructor_id BIGINT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    available BOOLEAN NOT NULL DEFAULT FALSE,
    hourly_rate_pgc INT NOT NULL DEFAULT 600,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uq_session_settings_course UNIQUE (course_id),
    CONSTRAINT fk_session_settings_course FOREIGN KEY (course_id) REFERENCES courses (id) ON DELETE CASCADE,
    CONSTRAINT fk_session_settings_instructor FOREIGN KEY (instructor_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_session_settings_instructor (instructor_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Step 4: Create doubt_sessions table for 1-to-1 live doubt-clearing sessions
CREATE TABLE IF NOT EXISTS doubt_sessions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_uid VARCHAR(36) NOT NULL,
    course_id BIGINT NOT NULL,
    instructor_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'REQUESTED',
    hourly_rate_pgc INT NOT NULL,
    max_duration_minutes INT NOT NULL,
    reserved_amount DECIMAL(19, 2) NOT NULL,
    final_charge DECIMAL(19, 2) NULL,
    refunded_amount DECIMAL(19, 2) NULL,
    billable_seconds INT NOT NULL DEFAULT 0,
    requested_at TIMESTAMP(6) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    accepted_at TIMESTAMP(6) NULL,
    connect_deadline_at TIMESTAMP(6) NULL,
    started_at TIMESTAMP(6) NULL,
    ended_at TIMESTAMP(6) NULL,
    livekit_room_name VARCHAR(128) NOT NULL,
    student_connected BOOLEAN NOT NULL DEFAULT FALSE,
    instructor_connected BOOLEAN NOT NULL DEFAULT FALSE,
    last_both_connected_at TIMESTAMP(6) NULL,
    last_disconnected_at TIMESTAMP(6) NULL,
    reconnect_deadline_at TIMESTAMP(6) NULL,
    end_reason VARCHAR(64) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uq_doubt_sessions_uid UNIQUE (session_uid),
    CONSTRAINT fk_doubt_sessions_course FOREIGN KEY (course_id) REFERENCES courses (id) ON DELETE CASCADE,
    CONSTRAINT fk_doubt_sessions_instructor FOREIGN KEY (instructor_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_doubt_sessions_student FOREIGN KEY (student_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_doubt_sessions_instructor_status (instructor_id, status),
    INDEX idx_doubt_sessions_student_status (student_id, status),
    INDEX idx_doubt_sessions_course (course_id),
    INDEX idx_doubt_sessions_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
