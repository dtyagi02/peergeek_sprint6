package com.peergeek.identity.dto;

import java.time.Instant;

public record CompletedCourseAchievementDto(
		Long courseId,
		String courseTitle,
		Instant completedAt) {
}

