package com.peergeek.identity.dto;

import java.time.Instant;

public record EnrolledCourseCardDto(
		Long courseId,
		String title,
		String description,
		String thumbnailUrl,
		String instructorName,
		String instructorUsername,
		String difficulty,
		String enrollmentStatus,
		int progressPercent,
		int completedLessons,
		int totalLessons,
		double courseRating,
		int reviewCount,
		boolean completed,
		Instant completedAt,
		Long continueLessonId) {
}

