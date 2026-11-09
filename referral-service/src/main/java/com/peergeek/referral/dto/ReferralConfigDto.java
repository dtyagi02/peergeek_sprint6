package com.peergeek.referral.dto;

import java.math.BigDecimal;

public record ReferralConfigDto(
		BigDecimal inviterRewardAmount,
		BigDecimal newUserRewardAmount,
		int expiryDays,
		int maxInvitationsPerDay) {
}

