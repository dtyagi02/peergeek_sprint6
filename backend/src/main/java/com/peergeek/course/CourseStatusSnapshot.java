package com.peergeek.course;

public record CourseStatusSnapshot(
		Long courseId,
		Long ownerId,
		CourseStatus status,
		boolean deleted,
		long version,
		String title) {
}
