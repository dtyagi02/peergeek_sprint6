package com.peergeek.enrollment;

public record LearnerLessonSummaryDto(
		Long id,
		String title,
		String summary,
		int position,
		boolean completed) {
}
