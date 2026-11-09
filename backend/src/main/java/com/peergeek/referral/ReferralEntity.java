package com.peergeek.referral;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
import com.peergeek.user.UserEntity;

@Entity
@Table(name = "referrals")
public class ReferralEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "inviter_id", nullable = false)
	private UserEntity inviter;

	@Column(name = "invited_email", nullable = false, length = 254)
	private String invitedEmail;

	@Column(name = "referral_code", nullable = false, unique = true, length = 32)
	private String referralCode;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 32)
	private ReferralStatus status;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "referred_user_id")
	private UserEntity referredUser;

	@Column(name = "inviter_reward_amount", nullable = false, precision = 19, scale = 2)
	private BigDecimal inviterRewardAmount;

	@Column(name = "new_user_reward_amount", nullable = false, precision = 19, scale = 2)
	private BigDecimal newUserRewardAmount;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "registered_at")
	private Instant registeredAt;

	@Column(name = "rewarded_at")
	private Instant rewardedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected ReferralEntity() {
	}

	public ReferralEntity(
			UserEntity inviter,
			String invitedEmail,
			String referralCode,
			BigDecimal inviterRewardAmount,
			BigDecimal newUserRewardAmount,
			int expiryDays) {
		this.inviter = inviter;
		this.invitedEmail = invitedEmail;
		this.referralCode = referralCode;
		this.status = ReferralStatus.PENDING;
		this.inviterRewardAmount = inviterRewardAmount;
		this.newUserRewardAmount = newUserRewardAmount;
		Instant now = Instant.now();
		this.expiresAt = now.plus(expiryDays, ChronoUnit.DAYS);
		this.createdAt = now;
		this.updatedAt = now;
	}

	public boolean isExpired() {
		return Instant.now().isAfter(expiresAt);
	}

	public void markRegistered(UserEntity referredUser, Instant now) {
		this.referredUser = referredUser;
		this.status = ReferralStatus.REGISTERED;
		this.registeredAt = now;
		this.updatedAt = now;
	}

	public void markRewarded(Instant now) {
		this.status = ReferralStatus.REWARDED;
		this.rewardedAt = now;
		this.updatedAt = now;
	}

	public void markExpired(Instant now) {
		this.status = ReferralStatus.EXPIRED;
		this.updatedAt = now;
	}

	public Long getId() {
		return id;
	}

	public UserEntity getInviter() {
		return inviter;
	}

	public String getInvitedEmail() {
		return invitedEmail;
	}

	public String getReferralCode() {
		return referralCode;
	}

	public ReferralStatus getStatus() {
		return status;
	}

	public void setStatus(ReferralStatus status) {
		this.status = status;
	}

	public UserEntity getReferredUser() {
		return referredUser;
	}

	public void setReferredUser(UserEntity referredUser) {
		this.referredUser = referredUser;
	}

	public BigDecimal getInviterRewardAmount() {
		return inviterRewardAmount;
	}

	public BigDecimal getNewUserRewardAmount() {
		return newUserRewardAmount;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public Instant getRegisteredAt() {
		return registeredAt;
	}

	public void setRegisteredAt(Instant registeredAt) {
		this.registeredAt = registeredAt;
	}

	public Instant getRewardedAt() {
		return rewardedAt;
	}

	public void setRewardedAt(Instant rewardedAt) {
		this.rewardedAt = rewardedAt;
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

