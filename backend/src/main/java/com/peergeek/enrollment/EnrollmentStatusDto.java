package com.peergeek.enrollment;

public record EnrollmentStatusDto(
		boolean enrolled,
		EnrollmentStatus status,
		boolean isOwner,
		boolean canEnroll,
		CourseProgressDto progress) {
}
