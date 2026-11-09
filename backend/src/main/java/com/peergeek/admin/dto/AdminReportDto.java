package com.peergeek.admin.dto;

import java.time.Instant;
import com.peergeek.report.ReportCategory;
import com.peergeek.report.ReportStatus;

public record AdminReportDto(
		Long id,
		Long courseId,
		String courseTitle,
		Long reporterUserId,
		String reporterName,
		String reporterEmail,
		ReportCategory category,
		String details,
		ReportStatus status,
		String resolutionNotes,
		Long resolvedBy,
		String resolverName,
		Instant resolvedAt,
		Instant createdAt) {
}
