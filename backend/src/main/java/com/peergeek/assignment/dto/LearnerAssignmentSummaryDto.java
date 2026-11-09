package com.peergeek.assignment.dto;

import java.math.BigDecimal;
import java.time.Instant;
import com.peergeek.assignment.AssignmentKind;

public record LearnerAssignmentSummaryDto(
		long id,
		long courseId,
		String courseTitle,
		AssignmentKind kind,
		String title,
		Long moduleId,
		Long lessonId,
		Instant opensAt,
		Instant dueAt,
		Integer timeLimitMinutes,
		BigDecimal maxPoints,
		String status,
		boolean late,
		BigDecimal totalPoints
) {}
