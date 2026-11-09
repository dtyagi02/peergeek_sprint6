package com.peergeek;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.DockerClientFactory;

public class DockerAvailabilityCondition implements ExecutionCondition {
	private static final String REQUIRE_DOCKER_PROPERTY = "peergeek.require-docker";

	@Override
	public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
		if (Boolean.getBoolean(REQUIRE_DOCKER_PROPERTY)) {
			return ConditionEvaluationResult.enabled(
					"Docker is required because -D" + REQUIRE_DOCKER_PROPERTY + "=true");
		}
		if (DockerClientFactory.instance().isDockerAvailable()) {
			return ConditionEvaluationResult.enabled("Docker is available");
		}
		return ConditionEvaluationResult.disabled(
				"Docker is unavailable; pass -D" + REQUIRE_DOCKER_PROPERTY
						+ "=true to make Docker unavailability fail the test run");
	}
}
