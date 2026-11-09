package com.peergeek.assignment;

import java.math.BigDecimal;
import java.time.Instant;

public record AssignmentDeadlineSummary(
		long assignmentId,
		long courseId,
		String title,
		Instant dueAt,
		BigDecimal maxPoints
) {}
