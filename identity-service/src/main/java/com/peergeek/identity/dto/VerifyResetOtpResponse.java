package com.peergeek.identity.dto;

public record VerifyResetOtpResponse(
		String resetToken,
		String message) {
}

