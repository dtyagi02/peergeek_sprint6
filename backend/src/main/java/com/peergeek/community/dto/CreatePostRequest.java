package com.peergeek.community.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreatePostRequest(
		@NotBlank(message = "Post content cannot be empty")
		@Size(max = 2000, message = "Post content cannot exceed 2000 characters")
		String content
) {
}
