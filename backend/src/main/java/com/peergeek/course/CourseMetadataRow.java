package com.peergeek.course;

public record CourseMetadataRow(
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
		String topicName,
		Long thumbnailResourceId) {
}
