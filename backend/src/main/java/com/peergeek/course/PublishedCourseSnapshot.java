package com.peergeek.course;

import java.time.Instant;
import java.util.List;

public record PublishedCourseSnapshot(
		long courseId,
		String title,
		String description,
		Long categoryId,
		String categoryName,
		String categorySlug,
		CourseDifficulty difficulty,
		List<String> topics,
		long ownerId,
		Long thumbnailResourceId,
		Instant publishedAt,
		int moduleCount,
		int lessonCount,
		List<PublishedModuleOutline> outline) {

	public PublishedCourseSnapshot {
		topics = topics == null ? List.of() : List.copyOf(topics);
		outline = outline == null ? List.of() : List.copyOf(outline);
	}
}
