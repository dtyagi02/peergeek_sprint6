package com.peergeek.enrollment;

import java.util.List;
import com.fasterxml.jackson.databind.JsonNode;

public record LearnerLessonDto(
		LearnerLessonDetailDto lesson,
		JsonNode contentDocument,
		List<LearnerResourceDto> resources,
		boolean completed,
		Long previousLessonId,
		Long nextLessonId) {
	public LearnerLessonDto {
		resources = List.copyOf(resources);
	}
}
