package com.peergeek.auth;

import java.time.Instant;
import java.util.UUID;
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
@Table(name = "refresh_tokens")
public class RefreshTokenEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private UserEntity user;

	@Column(name = "token_hash", nullable = false, unique = true, length = 64)
	private String tokenHash;

	@Column(name = "family_id", nullable = false, length = 36)
	private String familyId;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "revoked_at")
	private Instant revokedAt;

	@Column(name = "replaced_by")
	private Long replacedBy;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "user_agent", length = 512)
	private String userAgent;

	@Column(length = 45)
	private String ip;

	protected RefreshTokenEntity() {
	}

	public RefreshTokenEntity(
			UserEntity user, String tokenHash, String familyId, Instant expiresAt,
			String userAgent, String ip) {
		this.user = user;
		this.tokenHash = tokenHash;
		this.familyId = familyId;
		this.expiresAt = expiresAt;
		this.createdAt = Instant.now();
		this.userAgent = truncate(userAgent, 512);
		this.ip = ip;
	}

	public Long getId() {
		return id;
	}

	public UserEntity getUser() {
		return user;
	}

	public String getTokenHash() {
		return tokenHash;
	}

	public String getFamilyId() {
		return familyId;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public Instant getRevokedAt() {
		return revokedAt;
	}

	public void revoke(Instant when) {
		if (revokedAt == null) revokedAt = when;
	}

	public void replaceWith(Long replacementId, Instant when) {
		replacedBy = replacementId;
		revoke(when);
	}

	private static String truncate(String value, int length) {
		return value == null || value.length() <= length ? value : value.substring(0, length);
	}

	public static String newFamilyId() {
		return UUID.randomUUID().toString();
	}
}
