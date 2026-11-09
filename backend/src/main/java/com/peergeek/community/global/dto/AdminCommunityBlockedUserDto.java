package com.peergeek.community.global.dto;

import java.time.Instant;
import com.peergeek.user.UserRole;

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
