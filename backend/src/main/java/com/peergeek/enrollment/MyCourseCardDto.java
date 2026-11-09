package com.peergeek.enrollment;

import java.time.Instant;
import com.peergeek.course.CategoryDto;
import com.peergeek.course.CourseDifficulty;

public record MyCourseCardDto(
		Long courseId,
		String title,
		String thumbnailUrl,
		InstructorSummaryDto instructor,
		CourseDifficulty difficulty,
		String courseStatus,
		CourseProgressDto progress,
		Instant lastAccessedAt,
		Long continueLessonId,
		CategoryDto category) {
}
