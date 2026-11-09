package com.peergeek.referral.dto;

import java.math.BigDecimal;

public record ReferralConfigDto(
		BigDecimal inviterReward,
		BigDecimal newUserReward,
		int expiryDays) {
}

