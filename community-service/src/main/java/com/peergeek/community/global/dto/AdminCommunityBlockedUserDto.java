package com.peergeek.community.global.dto;

import com.peergeek.community.common.UserRole;
import java.time.Instant;

public record AdminCommunityBlockedUserDto(
		Long id,
		Long userId,
		String userDisplayName,
		String userEmail,
		String userUsername,
		UserRole userRole,
		Long blockedByUserId,
		String blockedByName,
		String reason,
		Instant blockedAt
) {
}

