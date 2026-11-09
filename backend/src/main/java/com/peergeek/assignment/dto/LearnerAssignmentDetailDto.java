package com.peergeek.assignment.dto;

import java.math.BigDecimal;
import java.time.Instant;
import com.peergeek.assignment.AssignmentKind;

import com.fasterxml.jackson.annotation.JsonProperty;

public record LearnerAssignmentDetailDto(
		long id,
		long courseId,
		String title,
		String instructions,
		AssignmentKind kind,
		BigDecimal maxPoints,
		Instant opensAt,
		Instant dueAt,
		Integer timeLimitMinutes,
		boolean allowLate,
		boolean allowText,
		boolean allowFiles,
		boolean showCorrectAnswers,
		Long moduleId,
		Long lessonId,
		LearnerAttemptSummaryDto attempt
) {
	@JsonProperty("hasAttempt")
	public boolean hasAttempt() {
		return attempt != null;
	}

	@JsonProperty("attemptStatus")
	public String attemptStatus() {
		return attempt != null ? attempt.status() : null;
	}
}

