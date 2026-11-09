package com.peergeek.identity.dto;

public record StudentLearningStatsDto(
		long totalEnrolledCourses,
		long coursesCompleted,
		long coursesInProgress,
		long lessonsCompleted) {
}

