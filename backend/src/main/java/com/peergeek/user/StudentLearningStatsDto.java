package com.peergeek.user;

public record StudentLearningStatsDto(
		long totalEnrolledCourses,
		long coursesCompleted,
		long coursesInProgress,
		long lessonsCompleted) {
}
