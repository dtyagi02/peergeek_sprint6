package com.peergeek.course;

public record LessonLocation(
		Long lessonId, Long moduleId, Long courseId, Long ownerId, boolean courseDeleted) {
}
