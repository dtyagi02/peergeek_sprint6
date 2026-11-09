package com.peergeek.identity.dto;

import java.time.Instant;
import java.util.List;
import com.peergeek.identity.entity.UserEntity;
import com.peergeek.identity.entity.UserMode;
import com.peergeek.identity.entity.UserRole;

public record UserProfileDto(
		Long id,
		String email,
		String username,
		String displayName,
		String headline,
		String bio,
		String avatarUrl,
		List<String> skills,
		List<String> interests,
		String teachingExpertise,
		String githubUrl,
		String linkedinUrl,
		String portfolioUrl,
		String websiteUrl,
		boolean showEducation,
		boolean showSocialLinks,
		boolean showLearningHistory,
		UserRole role,
		UserMode lastMode,
		Instant createdAt) {

	public UserProfileDto(
			Long id,
			String email,
			String username,
			String displayName,
			String headline,
			String bio,
			UserRole role,
			UserMode lastMode,
			Instant createdAt) {
		this(id, email, username, displayName, headline, bio, null, List.of(), List.of(), null, null, null, null, null, true, true, true, role, lastMode, createdAt);
	}

	public UserProfileDto(
			Long id,
			String email,
			String displayName,
			String headline,
			String bio,
			UserRole role,
			UserMode lastMode,
			Instant createdAt) {
		this(id, email, null, displayName, headline, bio, role, lastMode, createdAt);
	}

	public static UserProfileDto from(UserEntity user) {
		return new UserProfileDto(
				user.getId(),
				user.getEmail(),
				user.getUsername(),
				user.getDisplayName(),
				user.getHeadline(),
				user.getBio(),
				user.getAvatarUrl(),
				parseList(user.getSkills()),
				parseList(user.getInterests()),
				user.getTeachingExpertise(),
				user.getGithubUrl(),
				user.getLinkedinUrl(),
				user.getPortfolioUrl(),
				user.getWebsiteUrl(),
				user.isShowEducation(),
				user.isShowSocialLinks(),
				user.isShowLearningHistory(),
				user.getRole(),
				user.getLastMode(),
				user.getCreatedAt());
	}

	private static List<String> parseList(String raw) {
		if (raw == null || raw.isBlank()) return List.of();
		return java.util.Arrays.stream(raw.split(","))
				.map(String::trim)
				.filter(s -> !s.isEmpty())
				.toList();
	}
}

