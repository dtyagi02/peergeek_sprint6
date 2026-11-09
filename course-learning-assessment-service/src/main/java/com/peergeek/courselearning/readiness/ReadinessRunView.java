package com.peergeek.courselearning.readiness;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

public record ReadinessRunView(
        long id,
        long courseId,
        String status,
        String fingerprint,
        int blockingCount,
        int warningCount,
        String failureReason,
        Instant startedAt,
        Instant finishedAt,
        List<ReadinessIssue> issues
) {
    public ReadinessRunView {
        issues = issues == null ? List.of() : List.copyOf(issues);
    }

    @JsonProperty("ready")
    public boolean isReady() {
        return "COMPLETED".equals(status) && blockingCount == 0;
    }
}

