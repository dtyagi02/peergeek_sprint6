package com.peergeek.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
		@NotBlank(message = "Email is required")
		@Email(message = "Please enter a valid email address")
		String email,

		@NotBlank(message = "Password is required")
		String password) {
}

