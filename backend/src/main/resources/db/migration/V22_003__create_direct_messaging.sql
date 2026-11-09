CREATE TABLE direct_conversations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_one_id BIGINT NOT NULL,
    user_two_id BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_direct_conversation_users UNIQUE (user_one_id, user_two_id),
    CONSTRAINT fk_direct_conversation_user_one FOREIGN KEY (user_one_id) REFERENCES users(id),
    CONSTRAINT fk_direct_conversation_user_two FOREIGN KEY (user_two_id) REFERENCES users(id),
    CONSTRAINT ck_direct_conversation_distinct_users CHECK (user_one_id < user_two_id)
);

CREATE TABLE direct_messages (
    id BIGINT NOT NULL AUTO_INCREMENT,
    conversation_id BIGINT NOT NULL,
    sender_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_direct_message_conversation FOREIGN KEY (conversation_id) REFERENCES direct_conversations(id),
    CONSTRAINT fk_direct_message_sender FOREIGN KEY (sender_id) REFERENCES users(id),
    INDEX idx_direct_messages_conversation_created (conversation_id, created_at, id)
);

CREATE TABLE direct_participant_read_state (
    conversation_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    last_read_message_id BIGINT NULL,
    last_read_at TIMESTAMP(6) NULL,
    PRIMARY KEY (conversation_id, user_id),
    CONSTRAINT fk_direct_read_conversation FOREIGN KEY (conversation_id) REFERENCES direct_conversations(id),
    CONSTRAINT fk_direct_read_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_direct_read_message FOREIGN KEY (last_read_message_id) REFERENCES direct_messages(id)
);
