package com.peergeek.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Locale;

public record RegisterRequest(
		@NotBlank(message = "Email is required")
		@Email(message = "Enter a valid email address")
		@Size(max = 254)
		String email,

		@NotBlank(message = "Password is required")
		@Size(min = 8, max = 72, message = "Password must be at least 8 characters")
		String password,

		@NotBlank(message = "Display name is required")
		@Size(max = 100, message = "Display name must be at most 100 characters")
		String displayName,

		@NotBlank(message = "Username is required")
		@Size(min = 3, max = 30, message = "Username must be between 3 and 30 characters")
		@Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9_]{2,29}$", message = "Username must start with a letter and contain only letters, numbers, and underscores")
		String username,

		@Size(max = 64)
		String referralCode) {

	public RegisterRequest {
		email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
		username = username == null ? null : username.trim().toLowerCase(Locale.ROOT);
		referralCode = referralCode == null || referralCode.isBlank() ? null : referralCode.trim().toUpperCase(Locale.ROOT);
	}

	public RegisterRequest(String email, String password, String displayName) {
		this(email, password, displayName, null, null);
	}

	public RegisterRequest(String email, String password, String displayName, String username) {
		this(email, password, displayName, username, null);
	}
}
