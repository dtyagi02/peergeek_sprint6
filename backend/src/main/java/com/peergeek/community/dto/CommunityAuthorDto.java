package com.peergeek.community.dto;

public record CommunityAuthorDto(
		Long id,
		String displayName,
		String username,
		String avatarUrl
) {
}
