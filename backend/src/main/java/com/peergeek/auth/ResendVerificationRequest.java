package com.peergeek.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Locale;

public record ResendVerificationRequest(
		@NotBlank @Email @Size(max = 254) String email) {
	public ResendVerificationRequest {
		email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
	}
}
