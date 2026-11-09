package com.peergeek.identity.dto;

import java.util.List;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
		@NotBlank @Size(max = 100) String displayName,
		@Size(max = 30) String username,
		@Size(max = 160) String headline,
		@Size(max = 2000) String bio,
		List<String> skills,
		List<String> interests,
		@Size(max = 1000) String teachingExpertise,
		@Size(max = 255) String githubUrl,
		@Size(max = 255) String linkedinUrl,
		@Size(max = 255) String portfolioUrl,
		@Size(max = 255) String websiteUrl,
		Boolean showEducation,
		Boolean showSocialLinks,
		Boolean showLearningHistory) {

	public UpdateProfileRequest(String displayName, String headline, String bio) {
		this(displayName, null, headline, bio, null, null, null, null, null, null, null, null, null, null);
	}
}

