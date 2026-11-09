package com.peergeek.assignment;

import java.math.BigDecimal;
import java.time.Instant;

public record AssignmentSummaryDto(
		long id,
		long courseId,
		String title,
		AssignmentKind kind,
		AssignmentStatus status,
		Instant dueAt,
		BigDecimal maxPoints
) {}
