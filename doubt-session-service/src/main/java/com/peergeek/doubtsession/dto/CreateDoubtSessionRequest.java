package com.peergeek.doubtsession.dto;

public record CreateDoubtSessionRequest(
		Long courseId,
		int maxDurationMinutes
) {
}

