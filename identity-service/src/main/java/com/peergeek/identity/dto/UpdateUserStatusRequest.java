package com.peergeek.identity.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateUserStatusRequest(
		@NotNull(message = "Enabled flag is required")
		Boolean enabled,

		@Size(max = 500, message = "Reason cannot exceed 500 characters")
		String reason) {
}

