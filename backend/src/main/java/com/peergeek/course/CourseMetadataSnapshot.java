package com.peergeek.course;

import java.util.List;

public record CourseMetadataSnapshot(
		Long courseId,
		Long ownerId,
		CourseStatus status,
		boolean deleted,
		long version,
		String title,
		String description,
		Long categoryId,
		String categoryName,
		boolean categoryActive,
		CourseDifficulty difficulty,
		List<String> topics,
		Long thumbnailResourceId) {
	public CourseMetadataSnapshot {
		topics = List.copyOf(topics);
	}
}
