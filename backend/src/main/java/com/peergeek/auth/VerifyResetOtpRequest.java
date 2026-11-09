package com.peergeek.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Locale;

public record VerifyResetOtpRequest(
		@NotBlank @Email @Size(max = 254) String email,
		@NotBlank @Pattern(regexp = "\\d{6}", message = "Enter the six-digit code") String otp) {
	public VerifyResetOtpRequest {
		email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
	}
}
