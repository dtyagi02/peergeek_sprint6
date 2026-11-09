package com.peergeek.community;

import com.peergeek.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "community_mentions")
public class CommunityMentionEntity extends BaseEntity {

	@Column(name = "post_id", nullable = false)
	private Long postId;

	@Column(name = "comment_id")
	private Long commentId;

	@Column(name = "mentioned_user_id", nullable = false)
	private Long mentionedUserId;

	protected CommunityMentionEntity() {
	}

	public CommunityMentionEntity(Long postId, Long commentId, Long mentionedUserId) {
		this.postId = postId;
		this.commentId = commentId;
		this.mentionedUserId = mentionedUserId;
	}

	public Long getPostId() {
		return postId;
	}

	public Long getCommentId() {
		return commentId;
	}

	public Long getMentionedUserId() {
		return mentionedUserId;
	}
}
