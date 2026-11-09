package com.peergeek.enrollment;

public record ContinueLessonDto(
		Long lessonId,
		ContinueReason reason) {
}
