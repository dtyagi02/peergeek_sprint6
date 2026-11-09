package com.peergeek.community.directmessage;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "direct_participant_read_state")
public class DirectParticipantReadStateEntity {

	@EmbeddedId
	private Id id;

	@Column(name = "last_read_message_id")
	private Long lastReadMessageId;

	@Column(name = "last_read_at")
	private Instant lastReadAt;

	protected DirectParticipantReadStateEntity() {}

	public DirectParticipantReadStateEntity(Long conversationId, Long userId) {
		this.id = new Id(conversationId, userId);
	}

	public void markRead(Long messageId, Instant at) {
		this.lastReadMessageId = messageId;
		this.lastReadAt = at;
	}

	public Id getId() {
		return id;
	}

	public Long getLastReadMessageId() {
		return lastReadMessageId;
	}

	public Instant getLastReadAt() {
		return lastReadAt;
	}

	@Embeddable
	public static class Id implements Serializable {
		@Column(name = "conversation_id")
		private Long conversationId;

		@Column(name = "user_id")
		private Long userId;

		protected Id() {}

		public Id(Long conversationId, Long userId) {
			this.conversationId = conversationId;
			this.userId = userId;
		}

		public Long getConversationId() {
			return conversationId;
		}

		public Long getUserId() {
			return userId;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (!(o instanceof Id that)) return false;
			return Objects.equals(conversationId, that.conversationId) && Objects.equals(userId, that.userId);
		}

		@Override
		public int hashCode() {
			return Objects.hash(conversationId, userId);
		}
	}
}

