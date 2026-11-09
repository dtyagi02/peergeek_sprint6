package com.peergeek.community.domain.dto;

import java.time.Instant;

public record DomainMemberDto(
		Long id,
		String username,
		String displayName,
		String avatarUrl,
		Instant joinedAt
) {
}

