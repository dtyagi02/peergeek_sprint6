package com.peergeek.community.dto;

import java.time.Instant;
import java.util.List;

public record CommunityCommentDto(
		Long id,
		Long postId,
		Long parentCommentId,
		CommunityAuthorDto author,
		String content,
		Instant createdAt,
		Instant updatedAt,
		boolean isAuthor,
		boolean canDelete,
		List<CommunityCommentDto> replies
) {
}
