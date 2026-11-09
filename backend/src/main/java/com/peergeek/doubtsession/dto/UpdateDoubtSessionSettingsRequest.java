package com.peergeek.doubtsession.dto;

import jakarta.validation.constraints.Min;

public record UpdateDoubtSessionSettingsRequest(
		Boolean enabled,
		Boolean available,
		@Min(value = 1, message = "Hourly rate must be at least 1 GeekCoin")
		Integer hourlyRatePgc
) {
}
