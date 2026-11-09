package com.peergeek.identity.dto;

import java.util.List;

public record InstructorStatsDto(
		long totalPublishedCourses,
		long totalStudents,
		double averageRating,
		int totalReviews,
		List<TeachingCourseCardDto> publishedCourses) {
}

