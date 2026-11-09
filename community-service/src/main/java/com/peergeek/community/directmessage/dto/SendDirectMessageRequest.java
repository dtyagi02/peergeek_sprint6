package com.peergeek.community.directmessage.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendDirectMessageRequest(
		@NotBlank(message = "Message content is required")
		@Size(max = 2000, message = "Message cannot exceed 2000 characters")
		String content) {
}

