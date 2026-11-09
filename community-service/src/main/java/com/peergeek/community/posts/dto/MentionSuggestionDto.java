package com.peergeek.community.posts.dto;

public record MentionSuggestionDto(
		Long id,
		String username,
		String displayName,
		String avatarUrl
) {
}

