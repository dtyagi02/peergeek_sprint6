package com.peergeek.bookmark.dto;

import com.peergeek.bookmark.BookmarkTargetType;
import java.time.Instant;

public record BookmarkDto(
		long id,
		BookmarkTargetType targetType,
		long courseId,
		String courseTitle,
		String courseDifficulty,
		Long lessonId,
		String lessonTitle,
		String moduleTitle,
		Instant createdAt) {
}
