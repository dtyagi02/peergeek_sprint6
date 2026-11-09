package com.peergeek.readiness;

import java.util.Map;
import com.fasterxml.jackson.annotation.JsonProperty;

public record ReadinessIssue(
		String ruleCode,
		Severity severity,
		Scope scope,
		Long moduleId,
		Long lessonId,
		Long resourceId,
		String moduleTitle,
		String lessonTitle,
		String resourceTitle,
		String message,
		String fixHint,
		Map<String, Object> details) {
	public ReadinessIssue {
		details = details == null ? Map.of() : Map.copyOf(details);
	}

	@JsonProperty("code")
	public String code() {
		return ruleCode;
	}

	public enum Severity {
		BLOCKING_ERROR,
		WARNING
	}

	public enum Scope {
		COURSE,
		MODULE,
		LESSON,
		RESOURCE
	}
}
