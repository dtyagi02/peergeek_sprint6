-- V15_001: Add mandatory unique username to users table

-- 1. Add username column as NULL initially to support existing data
ALTER TABLE users ADD COLUMN username VARCHAR(30) NULL;

-- 2. Deterministically backfill existing users with a valid, unique, non-reserved username
-- Format: 'user_' + id satisfies length (6-12 chars), starts with letter 'u', alphanumeric + underscore, lowercase, not reserved
UPDATE users SET username = CONCAT('user_', id) WHERE username IS NULL;

-- 3. Enforce NOT NULL and unique constraint on username
ALTER TABLE users MODIFY COLUMN username VARCHAR(30) NOT NULL;
ALTER TABLE users ADD CONSTRAINT uk_users_username UNIQUE (username);
