package com.peergeek.community.global.dto;

import java.time.Instant;

public record CommunityUserBlockStatusDto(
		boolean blocked,
		String reason,
		Instant blockedAt
) {
}
