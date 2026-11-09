package com.peergeek.certificate.dto;

import java.util.List;

public record ReconciliationSummaryDto(
		int totalExamined,
		int eligible,
		int alreadyIssued,
		int newlyIssued,
		int failed,
		int ineligible,
		List<String> details
) {
	public static ReconciliationSummaryDto empty() {
		return new ReconciliationSummaryDto(0, 0, 0, 0, 0, 0, List.of());
	}
}

