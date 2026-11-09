package com.peergeek.user;

public record PublicProfileDto(Long id, String username, String displayName, String headline, String bio, String avatarUrl) {

	public PublicProfileDto(Long id, String displayName, String headline, String bio) {
		this(id, null, displayName, headline, bio, null);
	}

	public PublicProfileDto(Long id, String username, String displayName, String headline, String bio) {
		this(id, username, displayName, headline, bio, null);
	}

	public static PublicProfileDto from(UserEntity user) {
		return new PublicProfileDto(user.getId(), user.getUsername(), user.getDisplayName(), user.getHeadline(), user.getBio(), user.getAvatarUrl());
	}
}
