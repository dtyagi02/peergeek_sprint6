package com.peergeek.community.global.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateGlobalMessageRequest(
		@NotBlank(message = "Message content must not be blank")
		@Size(max = 2000, message = "Message must not exceed 2000 characters")
		String content
) {
}

