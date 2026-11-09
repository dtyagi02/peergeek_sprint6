package com.peergeek.identity.dto;

import java.time.Instant;
import java.util.List;
import com.peergeek.identity.entity.UserMode;
import com.peergeek.identity.entity.UserRole;

public record UserFullProfileDto(
		Long id,
		String username,
		String displayName,
		String headline,
		String bio,
		String avatarUrl,
		UserRole role,
		UserMode lastMode,
		String email,
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
		boolean isOwner,
		List<UserEducationDto> educations,
		List<UserProjectDto> projects,
		List<CompletedCourseAchievementDto> achievements,
		InstructorStatsDto instructorStats,
		StudentLearningStatsDto learningStats,
		List<EnrolledCourseCardDto> enrolledCourses,
		List<EarnedSkillDto> earnedSkills,
		Instant createdAt) {

	public UserFullProfileDto(
			Long id,
			String username,
			String displayName,
			String headline,
			String bio,
			String avatarUrl,
			UserRole role,
			UserMode lastMode,
			String email,
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
			boolean isOwner,
			List<UserEducationDto> educations,
			List<UserProjectDto> projects,
			List<CompletedCourseAchievementDto> achievements,
			InstructorStatsDto instructorStats,
			Instant createdAt) {
		this(id, username, displayName, headline, bio, avatarUrl, role, lastMode, email, skills, interests,
				teachingExpertise, githubUrl, linkedinUrl, portfolioUrl, websiteUrl, showEducation, showSocialLinks,
				showLearningHistory, isOwner, educations, projects, achievements, instructorStats, null, List.of(), List.of(), createdAt);
	}
}

