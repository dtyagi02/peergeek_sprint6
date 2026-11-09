package com.peergeek.note.dto;

import java.time.Instant;

public record LessonNoteDto(
		Long id,
		Long userId,
		Long courseId,
		Long lessonId,
		String content,
		Instant createdAt,
		Instant updatedAt
) {}
