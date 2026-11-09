package com.peergeek.courselearning.report;

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
        String resolverName,
        Instant resolvedAt,
        Instant createdAt,
        Instant updatedAt) {
}
