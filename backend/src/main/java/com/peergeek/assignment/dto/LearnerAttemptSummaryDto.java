package com.peergeek.assignment.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record LearnerAttemptSummaryDto(
		long submissionId,
		String status,
		Instant startedAt,
		Instant deadlineAt,
		Instant submittedAt,
		boolean late,
		boolean autoSubmitted,
		boolean autoGraded,
		BigDecimal totalPoints
) {}
