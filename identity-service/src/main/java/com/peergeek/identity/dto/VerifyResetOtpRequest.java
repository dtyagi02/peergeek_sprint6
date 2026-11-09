package com.peergeek.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VerifyResetOtpRequest(
		@NotBlank(message = "Email is required")
		@Email(message = "Please enter a valid email address")
		String email,

		@NotBlank(message = "OTP is required")
		@Pattern(regexp = "^[0-9]{6}$", message = "Verification code must be 6 digits")
		String otp) {
}

