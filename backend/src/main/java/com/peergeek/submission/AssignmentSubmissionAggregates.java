package com.peergeek.submission;

public record AssignmentSubmissionAggregates(
		long assignmentId,
		long totalAttempts,
		long submittedCount,
		long gradedCount,
		Double averageScore,
		Integer maxScore,
		Integer minScore
) {}
