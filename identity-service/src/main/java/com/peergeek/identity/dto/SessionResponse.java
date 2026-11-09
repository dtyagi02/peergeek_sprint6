package com.peergeek.identity.dto;

public record SessionResponse(
		String accessToken,
		long expiresIn,
		UserProfileDto user) {
}

