package com.peergeek.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
		@NotBlank(message = "Reset token is required")
		String resetToken,

		@NotBlank(message = "Password is required")
		@Size(min = 8, message = "Password must be at least 8 characters")
		String newPassword) {
}

