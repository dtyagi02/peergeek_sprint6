package com.peergeek.admin.dto;

import com.peergeek.course.CourseStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateCourseStatusRequest(
		@NotNull(message = "Course status is required")
		CourseStatus status,

		@Size(max = 500, message = "Reason cannot exceed 500 characters")
		String reason,

		Long reportId) {
}
