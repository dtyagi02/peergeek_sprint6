package com.peergeek.enrollment;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;

public record LearnerCourseOutlineDto(
		LearnerCourseSummaryDto course,
		List<LearnerModuleDto> modules,
		CourseProgressDto progress,
		@JsonProperty("continue") ContinueLessonDto continueLesson) {
	public LearnerCourseOutlineDto {
		modules = List.copyOf(modules);
	}
}
