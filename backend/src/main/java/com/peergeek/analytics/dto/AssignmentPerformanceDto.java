package com.peergeek.analytics.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record AssignmentPerformanceDto(
		long assignmentId,
		String title,
		String status,
		Instant dueAt,
		BigDecimal maxPoints,
		long submittedCount,
		long gradedCount,
		double submissionRate,
		Double averageScore) {
}
