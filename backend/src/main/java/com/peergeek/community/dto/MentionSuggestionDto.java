package com.peergeek.community.dto;

public record MentionSuggestionDto(
		Long id,
		String username,
		String displayName,
		String avatarUrl
) {
}
