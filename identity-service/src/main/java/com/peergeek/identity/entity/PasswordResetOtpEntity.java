package com.peergeek.identity.entity;

import java.time.Instant;
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
@Table(name = "password_reset_otps")
public class PasswordResetOtpEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private UserEntity user;

	@Column(name = "otp_hash", nullable = false, length = 100)
	private String otpHash;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(nullable = false)
	private int attempts;

	@Column(name = "consumed_at")
	private Instant consumedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	public PasswordResetOtpEntity() {
	}

	public PasswordResetOtpEntity(UserEntity user, String otpHash, Instant expiresAt) {
		this.user = user;
		this.otpHash = otpHash;
		this.expiresAt = expiresAt;
		this.createdAt = Instant.now();
		this.attempts = 0;
	}

	public boolean isUsable(Instant now) {
		return consumedAt == null && expiresAt.isAfter(now);
	}

	public void incrementAttempts() {
		this.attempts++;
	}

	public void consume(Instant now) {
		this.consumedAt = now;
	}

	public Long getId() {
		return id;
	}

	public UserEntity getUser() {
		return user;
	}

	public String getOtpHash() {
		return otpHash;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public int getAttempts() {
		return attempts;
	}

	public Instant getConsumedAt() {
		return consumedAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}

