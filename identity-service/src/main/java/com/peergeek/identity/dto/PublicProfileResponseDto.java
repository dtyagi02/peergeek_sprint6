package com.peergeek.identity.dto;

public record PublicProfileResponseDto(
		Long id,
		String username,
		String displayName,
		String headline,
		String bio) {

	public static PublicProfileResponseDto from(PublicProfileDto profile) {
		return new PublicProfileResponseDto(
				profile.id(),
				profile.username(),
				profile.displayName(),
				profile.headline(),
				profile.bio());
	}
}

