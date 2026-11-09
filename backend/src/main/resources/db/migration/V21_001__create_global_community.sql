CREATE TABLE IF NOT EXISTS global_community_messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    deleted_at DATETIME(6) NULL,
    deleted_by BIGINT NULL,
    delete_reason VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_global_comm_msgs_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_global_comm_msgs_deleted_by FOREIGN KEY (deleted_by) REFERENCES users (id) ON DELETE SET NULL,
    INDEX idx_global_comm_msgs_created (created_at),
    INDEX idx_global_comm_msgs_user (user_id),
    INDEX idx_global_comm_msgs_active_id (deleted_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS global_community_blocks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    blocked_by BIGINT NOT NULL,
    reason VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL,
    unblocked_at DATETIME(6) NULL,
    unblocked_by BIGINT NULL,
    unblock_reason VARCHAR(500) NULL,
    CONSTRAINT fk_global_comm_blocks_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_global_comm_blocks_blocked_by FOREIGN KEY (blocked_by) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_global_comm_blocks_unblocked_by FOREIGN KEY (unblocked_by) REFERENCES users (id) ON DELETE SET NULL,
    INDEX idx_global_comm_blocks_user_active (user_id, unblocked_at),
    INDEX idx_global_comm_blocks_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
