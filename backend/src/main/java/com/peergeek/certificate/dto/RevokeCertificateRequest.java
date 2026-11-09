package com.peergeek.certificate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RevokeCertificateRequest(
		@NotBlank(message = "Revocation reason is required")
		@Size(max = 500, message = "Reason cannot exceed 500 characters")
		String reason
) {}

