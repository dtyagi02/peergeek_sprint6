package com.peergeek.analytics.dto;

import java.util.List;

public record InstructorCourseAnalyticsDto(
		long courseId,
		String courseTitle,
		long totalEnrollments,
		long activeLearners,
		double completionRate,
		Double averageAssessmentScore,
		Double averageRating,
		long ratingCount,
		double assignmentSubmissionRate,
		long pendingSubmissions,
		List<LessonDropOffDto> lessonDropOff,
		List<AssignmentPerformanceDto> assignmentStats,
		List<ScoreBucketDto> scoreDistribution) {

	public InstructorCourseAnalyticsDto {
		lessonDropOff = lessonDropOff != null ? List.copyOf(lessonDropOff) : List.of();
		assignmentStats = assignmentStats != null ? List.copyOf(assignmentStats) : List.of();
		scoreDistribution = scoreDistribution != null ? List.copyOf(scoreDistribution) : List.of();
	}
}
