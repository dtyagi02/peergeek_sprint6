package com.peergeek.readiness;

import java.util.List;

public interface ReadinessRule {
	String code();
	RulePhase phase();
	List<ReadinessIssue> evaluate(ReadinessContext context);

	enum RulePhase {
		FAST,
		DEEP
	}
}
