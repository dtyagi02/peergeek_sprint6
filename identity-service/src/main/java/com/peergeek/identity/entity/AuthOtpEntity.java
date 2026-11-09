package com.peergeek.identity.entity;

import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "auth_otps")
public class AuthOtpEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 254)
	private String email;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id")
	private UserEntity user;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private OtpPurpose purpose;

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

	@Column(name = "reset_token_hash", length = 100)
	private String resetTokenHash;

	@Column(name = "reset_token_expires_at")
	private Instant resetTokenExpiresAt;

	@Column(name = "verified_at")
	private Instant verifiedAt;

	public AuthOtpEntity() {
	}

	public AuthOtpEntity(
			String email, UserEntity user, OtpPurpose purpose, String otpHash, Instant expiresAt) {
		this.email = email;
		this.user = user;
		this.purpose = purpose;
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

	public void markVerified(Instant now) {
		this.verifiedAt = now;
	}

	public void authorizeReset(String resetTokenHash, Instant resetTokenExpiresAt, Instant now) {
		this.verifiedAt = now;
		this.resetTokenHash = resetTokenHash;
		this.resetTokenExpiresAt = resetTokenExpiresAt;
	}

	public void markVerified(Instant now, String resetTokenHash, Instant resetTokenExpiresAt) {
		authorizeReset(resetTokenHash, resetTokenExpiresAt, now);
	}

	public boolean isResetTokenValid(Instant now) {
		return consumedAt == null && resetTokenHash != null && resetTokenExpiresAt != null && resetTokenExpiresAt.isAfter(now);
	}

	public Long getId() {
		return id;
	}

	public String getEmail() {
		return email;
	}

	public UserEntity getUser() {
		return user;
	}

	public OtpPurpose getPurpose() {
		return purpose;
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

	public String getResetTokenHash() {
		return resetTokenHash;
	}

	public Instant getResetTokenExpiresAt() {
		return resetTokenExpiresAt;
	}

	public Instant getVerifiedAt() {
		return verifiedAt;
	}
}
