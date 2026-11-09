package com.peergeek.community.directmessage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "direct_messages")
public class DirectMessageEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "conversation_id", nullable = false)
	private Long conversationId;

	@Column(name = "sender_id", nullable = false)
	private Long senderId;

	@Column(name = "content", nullable = false, columnDefinition = "TEXT")
	private String content;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt = Instant.now();

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt = Instant.now();

	protected DirectMessageEntity() {}

	public DirectMessageEntity(Long conversationId, Long senderId, String content) {
		this.conversationId = conversationId;
		this.senderId = senderId;
		this.content = content;
		this.createdAt = Instant.now();
		this.updatedAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public Long getConversationId() {
		return conversationId;
	}

	public Long getSenderId() {
		return senderId;
	}

	public String getContent() {
		return content;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}

