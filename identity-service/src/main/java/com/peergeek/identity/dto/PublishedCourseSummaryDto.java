package com.peergeek.identity.dto;

import java.time.Instant;

public record PublishedCourseSummaryDto(
		Long id,
		String title,
		String description,
		String difficulty,
		Instant publishedAt) {
}

