package com.peergeek.community;

import com.peergeek.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "community_posts")
public class CommunityPostEntity extends BaseEntity {

	@Column(name = "author_id", nullable = false)
	private Long authorId;

	@Column(nullable = false, length = 2000)
	private String content;

	protected CommunityPostEntity() {
	}

	public CommunityPostEntity(Long authorId, String content) {
		this.authorId = authorId;
		this.content = content;
	}

	public Long getAuthorId() {
		return authorId;
	}

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}
}
