package com.peergeek.community.global.dto;

import com.peergeek.community.common.UserMode;
import com.peergeek.community.common.UserRole;
import java.time.Instant;

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

