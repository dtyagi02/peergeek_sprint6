package com.peergeek.identity.entity;

import java.util.Locale;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class UserEntity extends BaseEntity {
	@Column(nullable = false, unique = true, length = 254)
	private String email;

	@Column(nullable = false, unique = true, length = 30)
	private String username;

	@Column(name = "password_hash", nullable = false, length = 100)
	private String passwordHash;

	@Column(name = "display_name", nullable = false, length = 100)
	private String displayName;

	@Column(length = 160)
	private String headline;

	@Column(length = 2000)
	private String bio;

	@Column(name = "avatar_key", length = 255)
	private String avatarKey;

	@Column(name = "avatar_url", length = 500)
	private String avatarUrl;

	@Column(columnDefinition = "TEXT")
	private String skills;

	@Column(columnDefinition = "TEXT")
	private String interests;

	@Column(name = "teaching_expertise", columnDefinition = "TEXT")
	private String teachingExpertise;

	@Column(name = "github_url", length = 255)
	private String githubUrl;

	@Column(name = "linkedin_url", length = 255)
	private String linkedinUrl;

	@Column(name = "portfolio_url", length = 255)
	private String portfolioUrl;

	@Column(name = "website_url", length = 255)
	private String websiteUrl;

	@Column(name = "show_education", nullable = false)
	private boolean showEducation = true;

	@Column(name = "show_social_links", nullable = false)
	private boolean showSocialLinks = true;

	@Column(name = "show_learning_history", nullable = false)
	private boolean showLearningHistory = true;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private UserRole role = UserRole.USER;

	@Enumerated(EnumType.STRING)
	@Column(name = "last_mode", nullable = false, length = 16)
	private UserMode lastMode = UserMode.STUDENT;

	@Column(nullable = false)
	private boolean enabled = true;

	@Column(name = "email_verified", nullable = false)
	private boolean emailVerified = true;

	public UserEntity() {
	}

	public UserEntity(String email, String passwordHash, String displayName, String username) {
		this(email, passwordHash, displayName, username, true);
	}

	public UserEntity(String email, String passwordHash, String displayName, String username, boolean emailVerified) {
		this.email = email;
		this.passwordHash = passwordHash;
		this.displayName = displayName;
		this.username = username;
		this.emailVerified = emailVerified;
	}

	public UserEntity(String email, String passwordHash, String displayName) {
		this(email, passwordHash, displayName, defaultUsername(email), true);
	}

	public UserEntity(String email, String passwordHash, String displayName, boolean emailVerified) {
		this(email, passwordHash, displayName, defaultUsername(email), emailVerified);
	}

	public static String defaultUsername(String email) {
		if (email == null) {
			return "user";
		}
		String prefix = email.split("@")[0].replaceAll("[^a-zA-Z0-9_]", "").toLowerCase(Locale.ROOT);
		if (prefix.length() < 3) {
			prefix = prefix + "user";
		}
		return prefix.length() > 30 ? prefix.substring(0, 30) : prefix;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public void setPasswordHash(String passwordHash) {
		this.passwordHash = passwordHash;
	}

	public String getDisplayName() {
		return displayName;
	}

	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}

	public String getHeadline() {
		return headline;
	}

	public void setHeadline(String headline) {
		this.headline = headline;
	}

	public String getBio() {
		return bio;
	}

	public void setBio(String bio) {
		this.bio = bio;
	}

	public String getAvatarKey() {
		return avatarKey;
	}

	public void setAvatarKey(String avatarKey) {
		this.avatarKey = avatarKey;
	}

	public String getAvatarUrl() {
		return avatarUrl;
	}

	public void setAvatarUrl(String avatarUrl) {
		this.avatarUrl = avatarUrl;
	}

	public String getSkills() {
		return skills;
	}

	public void setSkills(String skills) {
		this.skills = skills;
	}

	public String getInterests() {
		return interests;
	}

	public void setInterests(String interests) {
		this.interests = interests;
	}

	public String getTeachingExpertise() {
		return teachingExpertise;
	}

	public void setTeachingExpertise(String teachingExpertise) {
		this.teachingExpertise = teachingExpertise;
	}

	public String getGithubUrl() {
		return githubUrl;
	}

	public void setGithubUrl(String githubUrl) {
		this.githubUrl = githubUrl;
	}

	public String getLinkedinUrl() {
		return linkedinUrl;
	}

	public void setLinkedinUrl(String linkedinUrl) {
		this.linkedinUrl = linkedinUrl;
	}

	public String getPortfolioUrl() {
		return portfolioUrl;
	}

	public void setPortfolioUrl(String portfolioUrl) {
		this.portfolioUrl = portfolioUrl;
	}

	public String getWebsiteUrl() {
		return websiteUrl;
	}

	public void setWebsiteUrl(String websiteUrl) {
		this.websiteUrl = websiteUrl;
	}

	public boolean isShowEducation() {
		return showEducation;
	}

	public void setShowEducation(boolean showEducation) {
		this.showEducation = showEducation;
	}

	public boolean isShowSocialLinks() {
		return showSocialLinks;
	}

	public void setShowSocialLinks(boolean showSocialLinks) {
		this.showSocialLinks = showSocialLinks;
	}

	public boolean isShowLearningHistory() {
		return showLearningHistory;
	}

	public void setShowLearningHistory(boolean showLearningHistory) {
		this.showLearningHistory = showLearningHistory;
	}

	public UserRole getRole() {
		return role;
	}

	public void setRole(UserRole role) {
		this.role = role;
	}

	public UserMode getLastMode() {
		return lastMode;
	}

	public void setLastMode(UserMode lastMode) {
		this.lastMode = lastMode;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public boolean isEmailVerified() {
		return emailVerified;
	}

	public void setEmailVerified(boolean emailVerified) {
		this.emailVerified = emailVerified;
	}

	public void changeMode(UserMode mode) {
		if (mode != null) {
			this.lastMode = mode;
		}
	}

	public void updatePasswordHash(String passwordHash) {
		this.passwordHash = passwordHash;
	}

	public void updateProfile(String displayName, String headline, String bio) {
		this.displayName = displayName;
		this.headline = headline;
		this.bio = bio;
	}
}

