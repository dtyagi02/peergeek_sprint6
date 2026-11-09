package com.peergeek.course;

import java.util.List;

public record CourseCreateRequest(
		String title,
		String description,
		Long categoryId,
		CourseDifficulty difficulty,
		List<String> topics) {
}
