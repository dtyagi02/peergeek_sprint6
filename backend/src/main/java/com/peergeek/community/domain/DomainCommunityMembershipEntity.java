package com.peergeek.community.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "domain_community_memberships")
public class DomainCommunityMembershipEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "domain_id", nullable = false)
	private Long domainId;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "joined_at", nullable = false)
	private Instant joinedAt;

	protected DomainCommunityMembershipEntity() {
	}

	public DomainCommunityMembershipEntity(Long domainId, Long userId, Instant joinedAt) {
		this.domainId = domainId;
		this.userId = userId;
		this.joinedAt = joinedAt;
	}

	public Long getId() {
		return id;
	}

	public Long getDomainId() {
		return domainId;
	}

	public Long getUserId() {
		return userId;
	}

	public Instant getJoinedAt() {
		return joinedAt;
	}
}
