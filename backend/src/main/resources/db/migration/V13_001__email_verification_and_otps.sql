-- V13_001: Add email verification status and unified auth OTP challenges table

-- 1. Add email_verified column to users (default TRUE to preserve existing accounts)
ALTER TABLE users ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT TRUE;

-- 2. Create unified auth_otps table for registration verification and password reset challenges
CREATE TABLE auth_otps (
    id BIGINT NOT NULL AUTO_INCREMENT,
    email VARCHAR(254) NOT NULL,
    user_id BIGINT NULL,
    purpose VARCHAR(32) NOT NULL,
    otp_hash VARCHAR(100) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    consumed_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    reset_token_hash VARCHAR(100) NULL,
    reset_token_expires_at TIMESTAMP(6) NULL,
    verified_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_auth_otps_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_auth_otps_email_purpose ON auth_otps (email, purpose, created_at);
CREATE INDEX idx_auth_otps_reset_token ON auth_otps (reset_token_hash);
