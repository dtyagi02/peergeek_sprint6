package com.peergeek.community.global.dto;

import java.time.Instant;
import com.peergeek.user.UserMode;
import com.peergeek.user.UserRole;

public record AdminGlobalCommunityMessageDto(
		Long id,
		Long authorId,
		String authorDisplayName,
		String authorEmail,
		String authorUsername,
		UserRole authorRole,
		UserMode authorMode,
		String content,
		Instant createdAt,
		Instant updatedAt,
		boolean deleted,
		Instant deletedAt,
		Long deletedById,
		String deletedByName,
		String deleteReason
) {
}
