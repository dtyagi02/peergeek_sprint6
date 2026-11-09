package com.peergeek.referral.dto;

import java.math.BigDecimal;

public record ReferralValidateResponse(
		boolean valid,
		String inviterName,
		BigDecimal newUserRewardAmount,
		BigDecimal inviterRewardAmount,
		String message) {
}

