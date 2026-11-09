package com.peergeek.community.global;

import java.time.Instant;
import com.peergeek.user.UserEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "global_community_blocks")
public class GlobalCommunityBlockEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private UserEntity user;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "blocked_by", nullable = false)
	private UserEntity blockedBy;

	@Column(name = "reason", length = 500)
	private String reason;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "unblocked_at")
	private Instant unblockedAt;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "unblocked_by")
	private UserEntity unblockedBy;

	@Column(name = "unblock_reason", length = 500)
	private String unblockReason;

	public GlobalCommunityBlockEntity() {
	}

	public GlobalCommunityBlockEntity(UserEntity user, UserEntity blockedBy, String reason) {
		this.user = user;
		this.blockedBy = blockedBy;
		this.reason = reason;
		this.createdAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public UserEntity getUser() {
		return user;
	}

	public void setUser(UserEntity user) {
		this.user = user;
	}

	public UserEntity getBlockedBy() {
		return blockedBy;
	}

	public void setBlockedBy(UserEntity blockedBy) {
		this.blockedBy = blockedBy;
	}

	public String getReason() {
		return reason;
	}

	public void setReason(String reason) {
		this.reason = reason;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUnblockedAt() {
		return unblockedAt;
	}

	public void setUnblockedAt(Instant unblockedAt) {
		this.unblockedAt = unblockedAt;
	}

	public UserEntity getUnblockedBy() {
		return unblockedBy;
	}

	public void setUnblockedBy(UserEntity unblockedBy) {
		this.unblockedBy = unblockedBy;
	}

	public String getUnblockReason() {
		return unblockReason;
	}

	public void setUnblockReason(String unblockReason) {
		this.unblockReason = unblockReason;
	}

	public boolean isActive() {
		return unblockedAt == null;
	}
}
