CREATE TABLE IF NOT EXISTS domain_communities (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    category_id BIGINT NOT NULL,
    description VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_domain_communities_category UNIQUE (category_id),
    CONSTRAINT fk_domain_communities_category FOREIGN KEY (category_id) REFERENCES categories (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS domain_community_memberships (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    domain_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    joined_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_domain_memberships_domain_user UNIQUE (domain_id, user_id),
    CONSTRAINT fk_domain_memberships_domain FOREIGN KEY (domain_id) REFERENCES domain_communities (id) ON DELETE CASCADE,
    CONSTRAINT fk_domain_memberships_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_domain_memberships_user (user_id),
    INDEX idx_domain_memberships_domain (domain_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS domain_chat_messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    domain_id BIGINT NOT NULL,
    sender_id BIGINT NULL,
    message_type VARCHAR(20) NOT NULL DEFAULT 'CHAT',
    content TEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_domain_messages_domain FOREIGN KEY (domain_id) REFERENCES domain_communities (id) ON DELETE CASCADE,
    CONSTRAINT fk_domain_messages_sender FOREIGN KEY (sender_id) REFERENCES users (id) ON DELETE SET NULL,
    INDEX idx_domain_messages_domain_created (domain_id, created_at),
    INDEX idx_domain_messages_sender (sender_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Seed domain communities for all existing categories
INSERT INTO domain_communities (category_id, description, created_at, updated_at)
SELECT c.id, 
       CONCAT('Learn, discuss, and collaborate with fellow learners in ', c.name, '.'),
       UTC_TIMESTAMP(6),
       UTC_TIMESTAMP(6)
FROM categories c
WHERE NOT EXISTS (
    SELECT 1 FROM domain_communities dc WHERE dc.category_id = c.id
);
