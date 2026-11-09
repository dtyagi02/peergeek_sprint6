package com.peergeek.submission.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record InstructorSubmissionSummaryDto(
		long submissionId,
		long assignmentId,
		String assignmentTitle,
		LearnerSummary learner,
		String status,
		Instant submittedAt,
		boolean late,
		boolean autoSubmitted,
		boolean autoGraded,
		BigDecimal totalPoints,
		BigDecimal maxPoints
) {
	public record LearnerSummary(
			long id,
			String displayName
	) {}
}
