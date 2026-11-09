package com.peergeek.community.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCommentRequest(
		@NotBlank(message = "Comment content cannot be empty")
		@Size(max = 1000, message = "Comment content cannot exceed 1000 characters")
		String content,
		Long parentCommentId
) {
}
