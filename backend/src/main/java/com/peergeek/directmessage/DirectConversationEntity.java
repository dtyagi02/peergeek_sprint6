package com.peergeek.directmessage;

import com.peergeek.common.BaseEntity;
import com.peergeek.user.UserEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "direct_conversations", uniqueConstraints = @UniqueConstraint(columnNames = {"user_one_id", "user_two_id"}))
public class DirectConversationEntity extends BaseEntity {
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_one_id", nullable = false)
	private UserEntity userOne;
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_two_id", nullable = false)
	private UserEntity userTwo;
	protected DirectConversationEntity() {}
	public DirectConversationEntity(UserEntity userOne, UserEntity userTwo) {
		this.userOne = userOne; this.userTwo = userTwo;
	}
	public UserEntity getUserOne() { return userOne; }
	public UserEntity getUserTwo() { return userTwo; }
	public boolean hasParticipant(Long id) { return userOne.getId().equals(id) || userTwo.getId().equals(id); }
	public UserEntity other(Long id) { return userOne.getId().equals(id) ? userTwo : userOne; }
}
