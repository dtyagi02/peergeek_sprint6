package com.peergeek.doubtsession.dto;

public record CourseDoubtSessionAvailabilityDto(
		Long courseId,
		boolean enabled,
		boolean available,
		int hourlyRatePgc,
		String instructorDisplayName,
		boolean instructorHasActiveSession,
		boolean isEnrolled,
		Long instructorId,
		boolean isOwner
) {
}

