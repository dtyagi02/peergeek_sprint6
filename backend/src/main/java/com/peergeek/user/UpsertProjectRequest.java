package com.peergeek.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpsertProjectRequest(
		@NotBlank @Size(max = 150) String title,
		@Size(max = 2000) String description,
		@Size(max = 500) String technologies,
		@Size(max = 500) String projectUrl,
		@Size(max = 500) String repoUrl) {
}
