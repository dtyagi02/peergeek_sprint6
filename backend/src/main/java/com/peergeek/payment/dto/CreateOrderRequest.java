package com.peergeek.payment.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateOrderRequest(
		@NotBlank(message = "Package ID is required")
		String packageId
) {}

