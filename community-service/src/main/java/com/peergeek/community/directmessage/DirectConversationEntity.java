package com.peergeek.community.directmessage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(name = "direct_conversations", uniqueConstraints = @UniqueConstraint(columnNames = {"user_one_id", "user_two_id"}))
public class DirectConversationEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_one_id", nullable = false)
	private Long userOneId;

	@Column(name = "user_two_id", nullable = false)
	private Long userTwoId;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt = Instant.now();

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt = Instant.now();

	protected DirectConversationEntity() {}

	public DirectConversationEntity(Long userOneId, Long userTwoId) {
		this.userOneId = userOneId;
		this.userTwoId = userTwoId;
		this.createdAt = Instant.now();
		this.updatedAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public Long getUserOneId() {
		return userOneId;
	}

	public Long getUserTwoId() {
		return userTwoId;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(Instant updatedAt) {
		this.updatedAt = updatedAt;
	}

	public boolean hasParticipant(Long id) {
		return (userOneId != null && userOneId.equals(id)) || (userTwoId != null && userTwoId.equals(id));
	}

	public Long other(Long id) {
		return (userOneId != null && userOneId.equals(id)) ? userTwoId : userOneId;
	}
}

