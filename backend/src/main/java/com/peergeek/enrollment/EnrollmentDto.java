package com.peergeek.enrollment;

import java.time.Instant;

public record EnrollmentDto(
		Long courseId,
		EnrollmentStatus status,
		Instant enrolledAt,
		CourseProgressDto progress) {
}
