package com.peergeek.directmessage;

import com.peergeek.common.BaseEntity;
import com.peergeek.user.UserEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "direct_messages")
public class DirectMessageEntity extends BaseEntity {
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "conversation_id", nullable = false)
	private DirectConversationEntity conversation;
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "sender_id", nullable = false)
	private UserEntity sender;
	@Column(nullable = false, columnDefinition = "TEXT")
	private String content;
	protected DirectMessageEntity() {}
	public DirectMessageEntity(DirectConversationEntity conversation, UserEntity sender, String content) {
		this.conversation = conversation; this.sender = sender; this.content = content;
	}
	public DirectConversationEntity getConversation() { return conversation; }
	public UserEntity getSender() { return sender; }
	public String getContent() { return content; }
}
