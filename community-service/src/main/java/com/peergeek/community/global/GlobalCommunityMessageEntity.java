package com.peergeek.community.global;

import com.peergeek.community.common.UserMode;
import com.peergeek.community.common.UserRole;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "global_community_messages")
public class GlobalCommunityMessageEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "user_name", length = 120)
	private String userName;

	@Column(name = "user_username", length = 100)
	private String userUsername;

	@Column(name = "user_avatar_url", length = 500)
	private String userAvatarUrl;

	@Enumerated(EnumType.STRING)
	@Column(name = "user_role", nullable = false, length = 50)
	private UserRole userRole = UserRole.LEARNER;

	@Enumerated(EnumType.STRING)
	@Column(name = "user_mode", nullable = false, length = 50)
	private UserMode userMode = UserMode.LEARNER;

	@Column(name = "content", nullable = false, columnDefinition = "TEXT")
	private String content;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	@Column(name = "deleted_by")
	private Long deletedBy;

	@Column(name = "deleted_by_name", length = 120)
	private String deletedByName;

	@Column(name = "delete_reason", length = 500)
	private String deleteReason;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt = Instant.now();

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt = Instant.now();

	protected GlobalCommunityMessageEntity() {}

	public GlobalCommunityMessageEntity(
			Long userId,
			String userName,
			String userUsername,
			String userAvatarUrl,
			UserRole userRole,
			UserMode userMode,
			String content) {
		this.userId = userId;
		this.userName = userName;
		this.userUsername = userUsername;
		this.userAvatarUrl = userAvatarUrl;
		this.userRole = userRole != null ? userRole : UserRole.LEARNER;
		this.userMode = userMode != null ? userMode : UserMode.LEARNER;
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

	public Long getUserId() {
		return userId;
	}

	public String getUserName() {
		return userName;
	}

	public String getUserDisplayName() {
		return userName != null && !userName.isBlank() ? userName : userUsername;
	}

	public String getUserUsername() {
		return userUsername;
	}

	public String getUserAvatarUrl() {
		return userAvatarUrl;
	}

	public UserRole getUserRole() {
		return userRole;
	}

	public UserMode getUserMode() {
		return userMode;
	}

	public String getContent() {
		return content;
	}

	public boolean isDeleted() {
		return deletedAt != null;
	}

	public Instant getDeletedAt() {
		return deletedAt;
	}

	public void setDeletedAt(Instant deletedAt) {
		this.deletedAt = deletedAt;
	}

	public Long getDeletedBy() {
		return deletedBy;
	}

	public void setDeletedBy(Long deletedBy) {
		this.deletedBy = deletedBy;
	}

	public String getDeletedByName() {
		return deletedByName;
	}

	public void setDeletedByName(String deletedByName) {
		this.deletedByName = deletedByName;
	}

	public String getDeleteReason() {
		return deleteReason;
	}

	public void setDeleteReason(String deleteReason) {
		this.deleteReason = deleteReason;
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
}

