package com.peergeek.courselearning.report;

import java.time.Instant;

public record ReportCreatedEvent(
        long reportId,
        long courseId,
        long reporterUserId,
        long instructorId,
        ReportCategory category,
        String details,
        Instant createdAt) {
}
