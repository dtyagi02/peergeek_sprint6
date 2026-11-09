package com.peergeek.community.domain;

import com.peergeek.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "domain_chat_messages")
public class DomainChatMessageEntity extends BaseEntity {

	@Column(name = "domain_id", nullable = false)
	private Long domainId;

	@Column(name = "sender_id")
	private Long senderId;

	@Enumerated(EnumType.STRING)
	@Column(name = "message_type", nullable = false, length = 20)
	private MessageType messageType = MessageType.CHAT;

	@Column(columnDefinition = "TEXT", nullable = false)
	private String content;

	protected DomainChatMessageEntity() {
	}

	public DomainChatMessageEntity(Long domainId, Long senderId, MessageType messageType, String content) {
		this.domainId = domainId;
		this.senderId = senderId;
		this.messageType = messageType;
		this.content = content;
	}

	public Long getDomainId() {
		return domainId;
	}

	public Long getSenderId() {
		return senderId;
	}

	public MessageType getMessageType() {
		return messageType;
	}

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}
}
