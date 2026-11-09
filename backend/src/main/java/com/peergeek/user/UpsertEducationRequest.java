package com.peergeek.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpsertEducationRequest(
		@NotBlank @Size(max = 200) String institution,
		@Size(max = 100) String degree,
		@Size(max = 100) String fieldOfStudy,
		Integer startYear,
		Integer endYear) {
}
