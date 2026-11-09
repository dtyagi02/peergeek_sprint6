package com.peergeek.community;

import com.peergeek.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "community_comments")
public class CommunityCommentEntity extends BaseEntity {

	@Column(name = "post_id", nullable = false)
	private Long postId;

	@Column(name = "author_id", nullable = false)
	private Long authorId;

	@Column(name = "parent_comment_id")
	private Long parentCommentId;

	@Column(nullable = false, length = 1000)
	private String content;

	protected CommunityCommentEntity() {
	}

	public CommunityCommentEntity(Long postId, Long authorId, Long parentCommentId, String content) {
		this.postId = postId;
		this.authorId = authorId;
		this.parentCommentId = parentCommentId;
		this.content = content;
	}

	public Long getPostId() {
		return postId;
	}

	public Long getAuthorId() {
		return authorId;
	}

	public Long getParentCommentId() {
		return parentCommentId;
	}

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}
}
