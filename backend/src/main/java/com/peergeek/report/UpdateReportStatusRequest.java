package com.peergeek.report;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateReportStatusRequest(
		@NotNull(message = "Report status is required")
		ReportStatus status,

		@Size(max = 2000, message = "Resolution notes cannot exceed 2000 characters")
		String resolutionNotes) {
}
