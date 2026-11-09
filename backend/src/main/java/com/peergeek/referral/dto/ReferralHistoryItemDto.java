package com.peergeek.referral.dto;

import java.math.BigDecimal;
import java.time.Instant;
import com.peergeek.referral.ReferralStatus;

public record ReferralHistoryItemDto(
		Long id,
		String invitedEmail,
		String referralCode,
		ReferralStatus status,
		BigDecimal inviterRewardAmount,
		BigDecimal newUserRewardAmount,
		Instant createdAt,
		Instant registeredAt,
		Instant rewardedAt,
		Instant expiresAt) {
}

