package com.peergeek.community.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendDomainMessageRequest(
		@NotBlank(message = "Message content cannot be blank")
		@Size(max = 2000, message = "Message content cannot exceed 2000 characters")
		String content
) {
}

