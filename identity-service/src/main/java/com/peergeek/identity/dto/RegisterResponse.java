package com.peergeek.identity.dto;

public record RegisterResponse(
		String message,
		String email,
		boolean requiresVerification) {
}

