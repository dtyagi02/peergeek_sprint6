package com.peergeek.report;

import java.time.Instant;

public record ReportDto(
		Long id,
		Long courseId,
		Long reporterUserId,
		String reporterName,
		ReportCategory category,
		String details,
		ReportStatus status,
		String resolutionNotes,
		Long resolvedBy,
		Instant resolvedAt,
		Instant createdAt) {
}
