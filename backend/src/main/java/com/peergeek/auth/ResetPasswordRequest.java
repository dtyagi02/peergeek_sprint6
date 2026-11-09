package com.peergeek.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Locale;

public record ResetPasswordRequest(
		@NotBlank @Email @Size(max = 254) String email,
		String resetToken,
		String otp,
		@NotBlank @Size(min = 8, max = 72) String newPassword) {
	public ResetPasswordRequest {
		email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
	}

	public ResetPasswordRequest(String email, String otp, String newPassword) {
		this(email, null, otp, newPassword);
	}
}
