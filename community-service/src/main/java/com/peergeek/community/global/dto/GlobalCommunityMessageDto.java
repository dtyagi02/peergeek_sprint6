package com.peergeek.community.global.dto;

import com.peergeek.community.common.UserMode;
import com.peergeek.community.common.UserRole;
import java.time.Instant;

public record GlobalCommunityMessageDto(
		Long id,
		Long authorId,
		String authorDisplayName,
		String authorUsername,
		UserRole authorRole,
		UserMode authorMode,
		String content,
		Instant createdAt,
		boolean isOwnMessage,
		boolean canDelete
) {
}

