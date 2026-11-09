package com.peergeek.community.posts;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "community_mentions")
public class CommunityMentionEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "post_id")
	private Long postId;

	@Column(name = "comment_id")
	private Long commentId;

	@Column(name = "domain_id")
	private Long domainId;

	@Column(name = "mentioned_user_id", nullable = false)
	private Long mentionedUserId;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt = Instant.now();

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt = Instant.now();

	protected CommunityMentionEntity() {}

	public CommunityMentionEntity(Long postId, Long commentId, Long mentionedUserId) {
		this(postId, commentId, null, mentionedUserId);
	}

	public CommunityMentionEntity(Long postId, Long commentId, Long domainId, Long mentionedUserId) {
		this.postId = postId;
		this.commentId = commentId;
		this.domainId = domainId;
		this.mentionedUserId = mentionedUserId;
		this.createdAt = Instant.now();
		this.updatedAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public Long getPostId() {
		return postId;
	}

	public Long getCommentId() {
		return commentId;
	}

	public Long getDomainId() {
		return domainId;
	}

	public Long getMentionedUserId() {
		return mentionedUserId;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}

