-- PeerGeek Community Service Schema (Phase 4 Microservices Extraction)

CREATE TABLE IF NOT EXISTS community_user_profiles (
    user_id BIGINT PRIMARY KEY,
    full_name VARCHAR(120) NOT NULL,
    username VARCHAR(100) NOT NULL UNIQUE,
    email VARCHAR(180) NOT NULL,
    role VARCHAR(50) NOT NULL DEFAULT 'LEARNER',
    mode VARCHAR(50) NOT NULL DEFAULT 'LEARNER',
    avatar_url VARCHAR(500),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at DATETIME(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS domain_communities (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    category_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    slug VARCHAR(120) NOT NULL UNIQUE,
    description TEXT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS domain_community_memberships (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    domain_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    joined_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_domain_membership (domain_id, user_id),
    KEY idx_domain_memberships_domain (domain_id),
    KEY idx_domain_memberships_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS domain_chat_messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    domain_id BIGINT NOT NULL,
    sender_id BIGINT,
    sender_name VARCHAR(120),
    sender_username VARCHAR(100),
    sender_avatar_url VARCHAR(500),
    message_type VARCHAR(30) NOT NULL DEFAULT 'CHAT',
    content TEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    KEY idx_domain_messages_domain_id (domain_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS global_community_messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    user_name VARCHAR(120),
    user_username VARCHAR(100),
    user_avatar_url VARCHAR(500),
    user_role VARCHAR(50) NOT NULL DEFAULT 'LEARNER',
    user_mode VARCHAR(50) NOT NULL DEFAULT 'LEARNER',
    content TEXT NOT NULL,
    deleted_at DATETIME(6),
    deleted_by BIGINT,
    deleted_by_name VARCHAR(120),
    delete_reason VARCHAR(500),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    KEY idx_global_messages_deleted_created (deleted_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS global_community_blocks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    user_name VARCHAR(120),
    user_email VARCHAR(180),
    user_username VARCHAR(100),
    user_role VARCHAR(50) NOT NULL DEFAULT 'LEARNER',
    blocked_by BIGINT NOT NULL,
    blocked_by_name VARCHAR(120),
    reason VARCHAR(500) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    unblocked_at DATETIME(6),
    unblocked_by BIGINT,
    unblock_reason VARCHAR(500),
    KEY idx_global_blocks_user (user_id, unblocked_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS community_posts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    author_id BIGINT NOT NULL,
    author_name VARCHAR(120),
    author_username VARCHAR(100),
    author_avatar_url VARCHAR(500),
    content TEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    KEY idx_community_posts_created (created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS community_comments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    post_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    author_name VARCHAR(120),
    author_username VARCHAR(100),
    author_avatar_url VARCHAR(500),
    parent_comment_id BIGINT,
    content TEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    KEY idx_community_comments_post (post_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS community_mentions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    post_id BIGINT,
    comment_id BIGINT,
    domain_id BIGINT,
    mentioned_user_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    KEY idx_mentions_lookup (post_id, comment_id, mentioned_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS outbox_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id VARCHAR(64) NOT NULL UNIQUE,
    event_type VARCHAR(128) NOT NULL,
    topic VARCHAR(128) NOT NULL,
    payload JSON NOT NULL,
    published BOOLEAN NOT NULL DEFAULT FALSE,
    retry_count INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    published_at DATETIME(6),
    error_message TEXT,
    KEY idx_outbox_pending (published, retry_count, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

