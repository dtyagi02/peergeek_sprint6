package com.peergeek.identity.dto;

import java.time.Instant;

public record TeachingCourseCardDto(
		Long id,
		String title,
		String description,
		String difficulty,
		String status,
		String thumbnailUrl,
		long enrolledCount,
		double averageRating,
		int reviewCount,
		Instant publishedAt) {

	public static TeachingCourseCardDto from(PublishedCourseSummaryDto dto) {
		return new TeachingCourseCardDto(
				dto.id(),
				dto.title(),
				dto.description(),
				dto.difficulty(),
				"PUBLISHED",
				dto.id() != null ? "/api/v1/public/courses/" + dto.id() + "/thumbnail" : null,
				0,
				0.0,
				0,
				dto.publishedAt());
	}
}

