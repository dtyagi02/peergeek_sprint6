package com.peergeek.referral.dto;

import java.time.Instant;

public record ReferralInviteResponse(
		String message,
		String referralCode,
		String invitedEmail,
		Instant expiresAt) {
}

