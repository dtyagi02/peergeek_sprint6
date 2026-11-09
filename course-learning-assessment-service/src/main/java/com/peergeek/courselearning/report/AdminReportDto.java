package com.peergeek.courselearning.report;

import java.time.Instant;

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
        Instant createdAt) {}

