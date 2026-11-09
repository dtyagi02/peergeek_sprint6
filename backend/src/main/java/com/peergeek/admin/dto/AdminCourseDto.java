package com.peergeek.admin.dto;

import java.time.Instant;
import com.peergeek.course.CourseDifficulty;
import com.peergeek.course.CourseStatus;

public record AdminCourseDto(
		Long id,
		String title,
		Long instructorId,
		String instructorName,
		String instructorEmail,
		String categoryName,
		CourseDifficulty difficulty,
		CourseStatus status,
		long enrollmentCount,
		long openReportsCount,
		Instant publishedAt,
		Instant createdAt) {
}
