package com.peergeek.report;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateReportRequest(
		@NotNull(message = "Report category is required")
		ReportCategory category,

		@NotBlank(message = "Report details are required")
		@Size(max = 2000, message = "Details cannot exceed 2000 characters")
		String details) {
}
