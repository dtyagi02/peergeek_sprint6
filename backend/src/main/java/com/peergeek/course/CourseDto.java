package com.peergeek.course;

import java.time.Instant;
import java.util.List;

public record CourseDto(
		Long id,
		Long ownerId,
		String title,
		String description,
		CategoryDto category,
		CourseDifficulty difficulty,
		CourseStatus status,
		List<String> topics,
		long moduleCount,
		long lessonCount,
		long totalWordCount,
		Instant contentUpdatedAt,
		Instant createdAt,
		Instant updatedAt,
		long version) {
	public CourseDto {
		topics = List.copyOf(topics);
	}
}
