package com.peergeek.user;

import java.time.Instant;

public record CompletedCourseAchievementDto(
		Long courseId,
		String courseTitle,
		Instant completedAt) {
}
