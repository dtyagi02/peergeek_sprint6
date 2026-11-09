package com.peergeek.community.dto;

import java.time.Instant;

public record CommunityPostDto(
		Long id,
		CommunityAuthorDto author,
		String content,
		Instant createdAt,
		Instant updatedAt,
		long commentCount,
		boolean isAuthor,
		boolean canDelete
) {
}
