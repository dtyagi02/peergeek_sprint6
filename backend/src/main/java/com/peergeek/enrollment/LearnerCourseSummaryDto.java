package com.peergeek.enrollment;

import java.util.List;
import com.peergeek.course.CourseDifficulty;
import com.peergeek.user.PublicProfileDto;

public record LearnerCourseSummaryDto(
		Long id,
		String title,
		String description,
		String category,
		CourseDifficulty difficulty,
		List<String> topics,
		PublicProfileDto instructor,
		String courseStatus) {
	public LearnerCourseSummaryDto {
		topics = topics == null ? List.of() : List.copyOf(topics);
	}
}
