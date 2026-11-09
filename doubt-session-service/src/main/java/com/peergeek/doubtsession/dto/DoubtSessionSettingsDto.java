package com.peergeek.doubtsession.dto;

public record DoubtSessionSettingsDto(
		Long courseId,
		boolean enabled,
		boolean available,
		int hourlyRatePgc
) {
}

