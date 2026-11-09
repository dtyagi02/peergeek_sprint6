-- Direct Messaging tables migration
CREATE TABLE IF NOT EXISTS direct_conversations (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_one_id BIGINT NOT NULL,
  user_two_id BIGINT NOT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  UNIQUE KEY uk_direct_conversation_users (user_one_id, user_two_id),
  KEY fk_direct_conversation_user_two (user_two_id),
  CONSTRAINT ck_direct_conversation_distinct_users CHECK (user_one_id < user_two_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS direct_messages (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  conversation_id BIGINT NOT NULL,
  sender_id BIGINT NOT NULL,
  content TEXT NOT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  KEY fk_direct_message_sender (sender_id),
  KEY idx_direct_messages_conversation_created (conversation_id, created_at, id),
  CONSTRAINT fk_direct_message_conversation FOREIGN KEY (conversation_id) REFERENCES direct_conversations (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS direct_participant_read_state (
  conversation_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  last_read_message_id BIGINT DEFAULT NULL,
  last_read_at DATETIME(6) DEFAULT NULL,
  PRIMARY KEY (conversation_id, user_id),
  KEY fk_direct_read_user (user_id),
  KEY fk_direct_read_message (last_read_message_id),
  CONSTRAINT fk_direct_read_conversation FOREIGN KEY (conversation_id) REFERENCES direct_conversations (id),
  CONSTRAINT fk_direct_read_message FOREIGN KEY (last_read_message_id) REFERENCES direct_messages (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

