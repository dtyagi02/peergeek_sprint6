package com.peergeek.community.global.dto;

import jakarta.validation.constraints.Size;

public record DeleteGlobalMessageRequest(
		@Size(max = 500, message = "Reason cannot exceed 500 characters")
		String reason
) {
}
