package com.peergeek.community.posts;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "community_comments")
public class CommunityCommentEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "post_id", nullable = false)
	private Long postId;

	@Column(name = "author_id", nullable = false)
	private Long authorId;

	@Column(name = "author_name", length = 120)
	private String authorName;

	@Column(name = "author_username", length = 100)
	private String authorUsername;

	@Column(name = "author_avatar_url", length = 500)
	private String authorAvatarUrl;

	@Column(name = "parent_comment_id")
	private Long parentCommentId;

	@Column(name = "content", nullable = false, columnDefinition = "TEXT")
	private String content;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt = Instant.now();

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt = Instant.now();

	protected CommunityCommentEntity() {}

	public CommunityCommentEntity(
			Long postId,
			Long authorId,
			String authorName,
			String authorUsername,
			String authorAvatarUrl,
			Long parentCommentId,
			String content) {
		this.postId = postId;
		this.authorId = authorId;
		this.authorName = authorName;
		this.authorUsername = authorUsername;
		this.authorAvatarUrl = authorAvatarUrl;
		this.parentCommentId = parentCommentId;
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

	public Long getPostId() {
		return postId;
	}

	public Long getAuthorId() {
		return authorId;
	}

	public String getAuthorName() {
		return authorName;
	}

	public String getAuthorDisplayName() {
		return authorName != null && !authorName.isBlank() ? authorName : authorUsername;
	}

	public String getAuthorUsername() {
		return authorUsername;
	}

	public String getAuthorAvatarUrl() {
		return authorAvatarUrl;
	}

	public Long getParentCommentId() {
		return parentCommentId;
	}

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
		this.updatedAt = Instant.now();
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}

