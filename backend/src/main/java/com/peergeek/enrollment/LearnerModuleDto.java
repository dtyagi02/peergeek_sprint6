package com.peergeek.enrollment;

import java.util.List;

public record LearnerModuleDto(
		Long id,
		String title,
		String description,
		int position,
		List<LearnerLessonSummaryDto> lessons) {
	public LearnerModuleDto {
		lessons = List.copyOf(lessons);
	}
}
