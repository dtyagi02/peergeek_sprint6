package com.peergeek.readiness;

import java.util.List;
import com.peergeek.course.CourseMetadataSnapshot;
import com.peergeek.course.CourseStructureQueryService;
import com.peergeek.file.LessonResourceQueryService;

public record ReadinessContext(
		CourseMetadataSnapshot metadata,
		CourseStructureQueryService.CourseStructure structure,
		List<LessonResourceQueryService.CourseLessonResourceState> resources) {
	public ReadinessContext {
		resources = List.copyOf(resources);
	}
}
