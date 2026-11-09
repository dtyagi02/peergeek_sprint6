package com.peergeek.readiness;

import java.time.Instant;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;

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
		List<ReadinessIssue> issues) {
	public ReadinessRunView {
		issues = List.copyOf(issues);
	}

	@JsonProperty("ready")
	public boolean isReady() {
		return "COMPLETED".equals(status) && blockingCount == 0;
	}
}
