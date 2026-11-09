package com.peergeek.referral.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReferralInviteRequest(
		@NotBlank(message = "Email is required")
		@Email(message = "Enter a valid email address")
		@Size(max = 254, message = "Email must be at most 254 characters")
		String email) {
}

