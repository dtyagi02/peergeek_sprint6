package com.peergeek.community.domain.dto;

public record DomainCommunityDto(
		Long id,
		Long categoryId,
		String name,
		String slug,
		String description,
		long memberCount,
		boolean isMember
) {
}

