package com.peergeek.assignment.dto;

import java.math.BigDecimal;
import java.time.Instant;
import com.peergeek.assignment.AssignmentKind;

public record CreateAssignmentRequest(
		AssignmentKind kind,
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
		BigDecimal maxPoints) {
}
