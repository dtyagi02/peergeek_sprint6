package com.peergeek.directmessage;

import com.peergeek.user.UserEntity;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "direct_participant_read_state")
public class DirectParticipantReadStateEntity {
	@EmbeddedId private Id id;
	@ManyToOne(fetch = FetchType.LAZY) @MapsId("conversationId")
	@JoinColumn(name = "conversation_id") private DirectConversationEntity conversation;
	@ManyToOne(fetch = FetchType.LAZY) @MapsId("userId")
	@JoinColumn(name = "user_id") private UserEntity user;
	@Column(name = "last_read_message_id") private Long lastReadMessageId;
	@Column(name = "last_read_at") private Instant lastReadAt;
	protected DirectParticipantReadStateEntity() {}
	public DirectParticipantReadStateEntity(DirectConversationEntity c, UserEntity u) {
		conversation = c; user = u; id = new Id(c.getId(), u.getId());
	}
	public void markRead(Long messageId, Instant at) { lastReadMessageId = messageId; lastReadAt = at; }
	public Long getLastReadMessageId() { return lastReadMessageId; }
	@Embeddable public static class Id implements java.io.Serializable {
		private Long conversationId; private Long userId;
		protected Id() {} public Id(Long c, Long u) { conversationId=c; userId=u; }
		@Override public boolean equals(Object o) { return o instanceof Id x && java.util.Objects.equals(conversationId,x.conversationId) && java.util.Objects.equals(userId,x.userId); }
		@Override public int hashCode() { return java.util.Objects.hash(conversationId,userId); }
	}
}
