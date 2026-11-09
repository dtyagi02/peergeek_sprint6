package com.peergeek.assignment.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record UpdateAssignmentRequest(
		String title,
		String instructions,
		Long moduleId,
		Long lessonId,
		Instant opensAt,
		Instant dueAt,
		Integer timeLimitMinutes,
		Boolean allowLate,
		Boolean allowText,
		Boolean allowFiles,
		Boolean showCorrectAnswers,
		BigDecimal maxPoints,
		Long version) {
}
