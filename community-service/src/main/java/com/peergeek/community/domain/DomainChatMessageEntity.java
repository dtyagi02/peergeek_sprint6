package com.peergeek.community.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "domain_chat_messages")
public class DomainChatMessageEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "domain_id", nullable = false)
	private Long domainId;

	@Column(name = "sender_id")
	private Long senderId;

	@Column(name = "sender_name", length = 120)
	private String senderName;

	@Column(name = "sender_username", length = 100)
	private String senderUsername;

	@Column(name = "sender_avatar_url", length = 500)
	private String senderAvatarUrl;

	@Enumerated(EnumType.STRING)
	@Column(name = "message_type", nullable = false, length = 30)
	private MessageType messageType = MessageType.CHAT;

	@Column(name = "content", nullable = false, columnDefinition = "TEXT")
	private String content;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt = Instant.now();

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt = Instant.now();

	protected DomainChatMessageEntity() {}

	public DomainChatMessageEntity(
			Long domainId,
			Long senderId,
			String senderName,
			String senderUsername,
			String senderAvatarUrl,
			MessageType messageType,
			String content) {
		this.domainId = domainId;
		this.senderId = senderId;
		this.senderName = senderName;
		this.senderUsername = senderUsername;
		this.senderAvatarUrl = senderAvatarUrl;
		this.messageType = messageType != null ? messageType : MessageType.CHAT;
		this.content = content;
		this.createdAt = Instant.now();
		this.updatedAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Long getDomainId() {
		return domainId;
	}

	public Long getSenderId() {
		return senderId;
	}

	public String getSenderName() {
		return senderName;
	}

	public String getSenderUsername() {
		return senderUsername;
	}

	public String getSenderAvatarUrl() {
		return senderAvatarUrl;
	}

	public MessageType getMessageType() {
		return messageType;
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

