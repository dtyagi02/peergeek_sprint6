package com.peergeek.community.posts.dto;

public record CommunityAuthorDto(
		Long id,
		String displayName,
		String username,
		String avatarUrl
) {
}

