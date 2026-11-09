package com.peergeek.community.global.dto;

import java.time.Instant;
import com.peergeek.user.UserMode;
import com.peergeek.user.UserRole;

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
