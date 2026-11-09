package com.peergeek.admin.dto;

import com.peergeek.report.ReportStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateAdminReportRequest(
		@NotNull(message = "Report status is required")
		ReportStatus status,
		@Size(max = 2000, message = "Resolution notes must not exceed 2000 characters")
		String resolutionNotes) {
}
