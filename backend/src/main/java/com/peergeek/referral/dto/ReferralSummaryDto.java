package com.peergeek.referral.dto;

import java.math.BigDecimal;

public record ReferralSummaryDto(
		long totalInvitations,
		long pendingReferrals,
		long successfulReferrals,
		BigDecimal totalCoinsEarned,
		BigDecimal inviterRewardAmount,
		BigDecimal newUserRewardAmount) {
}

