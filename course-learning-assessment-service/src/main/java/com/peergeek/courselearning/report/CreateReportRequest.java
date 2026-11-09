package com.peergeek.courselearning.report;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateReportRequest(
        @NotNull(message = "Category is required")
        ReportCategory category,

        @NotBlank(message = "Details cannot be blank")
        @Size(max = 2000, message = "Details cannot exceed 2000 characters")
        String details) {
}
