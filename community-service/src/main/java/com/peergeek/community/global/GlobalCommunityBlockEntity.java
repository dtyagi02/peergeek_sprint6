package com.peergeek.community.global;

import com.peergeek.community.common.UserRole;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "global_community_blocks")
public class GlobalCommunityBlockEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "user_name", length = 120)
	private String userName;

	@Column(name = "user_email", length = 180)
	private String userEmail;

	@Column(name = "user_username", length = 100)
	private String userUsername;

	@Enumerated(EnumType.STRING)
	@Column(name = "user_role", nullable = false, length = 50)
	private UserRole userRole = UserRole.LEARNER;

	@Column(name = "blocked_by", nullable = false)
	private Long blockedBy;

	@Column(name = "blocked_by_name", length = 120)
	private String blockedByName;

	@Column(name = "reason", nullable = false, length = 500)
	private String reason;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt = Instant.now();

	@Column(name = "unblocked_at")
	private Instant unblockedAt;

	@Column(name = "unblocked_by")
	private Long unblockedBy;

	@Column(name = "unblock_reason", length = 500)
	private String unblockReason;

	protected GlobalCommunityBlockEntity() {}

	public GlobalCommunityBlockEntity(
			Long userId,
			String userName,
			String userEmail,
			String userUsername,
			UserRole userRole,
			Long blockedBy,
			String blockedByName,
			String reason) {
		this.userId = userId;
		this.userName = userName;
		this.userEmail = userEmail;
		this.userUsername = userUsername;
		this.userRole = userRole != null ? userRole : UserRole.LEARNER;
		this.blockedBy = blockedBy;
		this.blockedByName = blockedByName;
		this.reason = reason;
		this.createdAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public Long getUserId() {
		return userId;
	}

	public String getUserName() {
		return userName;
	}

	public String getUserEmail() {
		return userEmail;
	}

	public String getUserUsername() {
		return userUsername;
	}

	public UserRole getUserRole() {
		return userRole;
	}

	public Long getBlockedBy() {
		return blockedBy;
	}

	public String getBlockedByName() {
		return blockedByName;
	}

	public String getReason() {
		return reason;
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

	public Long getUnblockedBy() {
		return unblockedBy;
	}

	public void setUnblockedBy(Long unblockedBy) {
		this.unblockedBy = unblockedBy;
	}

	public String getUnblockReason() {
		return unblockReason;
	}

	public void setUnblockReason(String unblockReason) {
		this.unblockReason = unblockReason;
	}
}

