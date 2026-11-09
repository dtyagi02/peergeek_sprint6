package com.peergeek.identity.service;

import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;
import com.peergeek.identity.dto.*;
import com.peergeek.identity.entity.*;
import com.peergeek.identity.exception.BadRequestException;
import com.peergeek.identity.exception.FieldErrorResponse;
import com.peergeek.identity.exception.InputValidationException;
import com.peergeek.identity.exception.NotFoundException;
import com.peergeek.identity.repository.UserEducationRepository;
import com.peergeek.identity.repository.UserProjectRepository;
import com.peergeek.identity.repository.UserRepository;
import com.peergeek.identity.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class UserService {
	private static final Logger log = LoggerFactory.getLogger(UserService.class);
	private static final long MAX_AVATAR_SIZE = 5 * 1024 * 1024; // 5MB
	private static final Set<String> ALLOWED_AVATAR_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/gif");

	private final UserRepository users;
	private final UserEducationRepository educationRepository;
	private final UserProjectRepository projectRepository;
	private final ObjectStorageService storage;

	public UserService(
			UserRepository users,
			UserEducationRepository educationRepository,
			UserProjectRepository projectRepository,
			ObjectStorageService storage) {
		this.users = users;
		this.educationRepository = educationRepository;
		this.projectRepository = projectRepository;
		this.storage = storage;
	}

	@Transactional(readOnly = true)
	public UserProfileDto currentProfile(Long currentUserId) {
		UserEntity user = getUser(currentUserId);
		return UserProfileDto.from(user);
	}

	@Transactional
	public UserProfileDto updateProfile(Long currentUserId, UpdateProfileRequest request) {
		UserEntity user = getUser(currentUserId);

		if (request.displayName() != null) {
			String name = request.displayName().trim();
			if (name.isEmpty() || name.length() > 100 || containsMarkup(name)) {
				throw new InputValidationException("Invalid display name",
						List.of(new FieldErrorResponse("displayName", "Display name is invalid")));
			}
			user.setDisplayName(name);
		}

		if (request.username() != null && !request.username().isBlank()) {
			String username = request.username().trim().toLowerCase(Locale.ROOT);
			if (!username.equals(user.getUsername())) {
				if (users.existsByUsername(username)) {
					throw new InputValidationException("Username is already taken",
							List.of(new FieldErrorResponse("username", "Username is already taken. Please choose another username.")));
				}
				user.setUsername(username);
			}
		}

		if (request.headline() != null) {
			String headline = request.headline().trim();
			if (containsMarkup(headline)) {
				throw new InputValidationException("Invalid headline",
						List.of(new FieldErrorResponse("headline", "HTML markup is not allowed")));
			}
			user.setHeadline(headline.isEmpty() ? null : headline);
		}

		if (request.bio() != null) {
			String bio = request.bio().trim();
			if (containsMarkup(bio)) {
				throw new InputValidationException("Invalid bio",
						List.of(new FieldErrorResponse("bio", "HTML markup is not allowed")));
			}
			user.setBio(bio.isEmpty() ? null : bio);
		}

		if (request.skills() != null) {
			user.setSkills(String.join(",", request.skills().stream().map(String::trim).filter(s -> !s.isEmpty()).toList()));
		}

		if (request.interests() != null) {
			user.setInterests(String.join(",", request.interests().stream().map(String::trim).filter(s -> !s.isEmpty()).toList()));
		}

		if (request.teachingExpertise() != null) {
			user.setTeachingExpertise(request.teachingExpertise().trim());
		}

		if (request.githubUrl() != null) user.setGithubUrl(request.githubUrl().trim());
		if (request.linkedinUrl() != null) user.setLinkedinUrl(request.linkedinUrl().trim());
		if (request.portfolioUrl() != null) user.setPortfolioUrl(request.portfolioUrl().trim());
		if (request.websiteUrl() != null) user.setWebsiteUrl(request.websiteUrl().trim());

		if (request.showEducation() != null) user.setShowEducation(request.showEducation());
		if (request.showSocialLinks() != null) user.setShowSocialLinks(request.showSocialLinks());
		if (request.showLearningHistory() != null) user.setShowLearningHistory(request.showLearningHistory());

		try {
			users.save(user);
		} catch (DataIntegrityViolationException ex) {
			throw new InputValidationException("Username is already taken",
					List.of(new FieldErrorResponse("username", "Username is already taken. Please choose another username.")));
		}

		return UserProfileDto.from(user);
	}

	@Transactional
	public UserProfileDto updateMode(Long currentUserId, UserMode mode) {
		UserEntity user = getUser(currentUserId);
		user.changeMode(mode);
		users.save(user);
		return UserProfileDto.from(user);
	}

	@Transactional(readOnly = true)
	public PublicProfileDto publicProfile(Long userId) {
		UserEntity user = users.findById(userId)
				.orElseThrow(() -> new NotFoundException("The requested user was not found"));
		return PublicProfileDto.from(user);
	}

	@Transactional(readOnly = true)
	public PublicProfileDto publicProfileByUsername(String username) {
		String normalized = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
		UserEntity user = users.findByUsername(normalized)
				.orElseThrow(() -> new NotFoundException("The requested user was not found"));
		return PublicProfileDto.from(user);
	}

	@Transactional(readOnly = true)
	public UserFullProfileDto getFullProfile(Long targetUserId, Optional<CurrentUser> viewer) {
		UserEntity targetUser = getUser(targetUserId);
		boolean isOwner = viewer.isPresent() && viewer.get().id().equals(targetUser.getId());

		String email = isOwner ? targetUser.getEmail() : null;

		List<UserEducationDto> educations = (isOwner || targetUser.isShowEducation())
				? educationRepository.findByUserIdOrderByStartYearDescCreatedAtDesc(targetUser.getId())
						.stream().map(UserEducationDto::from).toList()
				: List.of();

		List<UserProjectDto> projects = projectRepository.findByUserIdOrderByCreatedAtDesc(targetUser.getId())
				.stream().map(UserProjectDto::from).toList();

		String githubUrl = (isOwner || targetUser.isShowSocialLinks()) ? targetUser.getGithubUrl() : null;
		String linkedinUrl = (isOwner || targetUser.isShowSocialLinks()) ? targetUser.getLinkedinUrl() : null;
		String portfolioUrl = (isOwner || targetUser.isShowSocialLinks()) ? targetUser.getPortfolioUrl() : null;
		String websiteUrl = (isOwner || targetUser.isShowSocialLinks()) ? targetUser.getWebsiteUrl() : null;

		List<String> skills = parseList(targetUser.getSkills());
		List<String> interests = parseList(targetUser.getInterests());

		InstructorStatsDto instructorStats = new InstructorStatsDto(0, 0, 0.0, 0, List.of());
		StudentLearningStatsDto learningStats = new StudentLearningStatsDto(0, 0, 0, 0);

		return new UserFullProfileDto(
				targetUser.getId(),
				targetUser.getUsername(),
				targetUser.getDisplayName(),
				targetUser.getHeadline(),
				targetUser.getBio(),
				targetUser.getAvatarUrl(),
				targetUser.getRole(),
				targetUser.getLastMode(),
				email,
				skills,
				interests,
				targetUser.getTeachingExpertise(),
				githubUrl,
				linkedinUrl,
				portfolioUrl,
				websiteUrl,
				targetUser.isShowEducation(),
				targetUser.isShowSocialLinks(),
				targetUser.isShowLearningHistory(),
				isOwner,
				educations,
				projects,
				List.of(),
				instructorStats,
				learningStats,
				List.of(),
				List.of(),
				targetUser.getCreatedAt());
	}

	@Transactional(readOnly = true)
	public UserFullProfileDto getFullProfileByUsername(String username, Optional<CurrentUser> viewer) {
		String normalized = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
		UserEntity user = users.findByUsername(normalized)
				.orElseThrow(() -> new NotFoundException("The requested user was not found"));
		return getFullProfile(user.getId(), viewer);
	}

	@Transactional
	public UserEducationDto addEducation(Long currentUserId, UpsertEducationRequest request) {
		validateEducationRequest(request);
		UserEducationEntity entity = new UserEducationEntity(
				currentUserId,
				request.institution().trim(),
				trimToNull(request.degree()),
				trimToNull(request.fieldOfStudy()),
				request.startYear(),
				request.endYear());
		return UserEducationDto.from(educationRepository.save(entity));
	}

	@Transactional
	public UserEducationDto updateEducation(Long currentUserId, Long educationId, UpsertEducationRequest request) {
		validateEducationRequest(request);
		UserEducationEntity entity = educationRepository.findByIdAndUserId(educationId, currentUserId)
				.orElseThrow(() -> new NotFoundException("Education record not found"));
		entity.setInstitution(request.institution().trim());
		entity.setDegree(trimToNull(request.degree()));
		entity.setFieldOfStudy(trimToNull(request.fieldOfStudy()));
		entity.setStartYear(request.startYear());
		entity.setEndYear(request.endYear());
		return UserEducationDto.from(educationRepository.save(entity));
	}

	@Transactional
	public void deleteEducation(Long currentUserId, Long educationId) {
		UserEducationEntity entity = educationRepository.findByIdAndUserId(educationId, currentUserId)
				.orElseThrow(() -> new NotFoundException("Education record not found"));
		educationRepository.delete(entity);
	}

	@Transactional
	public UserProjectDto addProject(Long currentUserId, UpsertProjectRequest request) {
		validateProjectRequest(request);
		UserProjectEntity entity = new UserProjectEntity(
				currentUserId,
				request.title().trim(),
				trimToNull(request.description()),
				trimToNull(request.technologies()),
				trimToNull(request.projectUrl()),
				trimToNull(request.repoUrl()));
		return UserProjectDto.from(projectRepository.save(entity));
	}

	@Transactional
	public UserProjectDto updateProject(Long currentUserId, Long projectId, UpsertProjectRequest request) {
		validateProjectRequest(request);
		UserProjectEntity entity = projectRepository.findByIdAndUserId(projectId, currentUserId)
				.orElseThrow(() -> new NotFoundException("Project record not found"));
		entity.setTitle(request.title().trim());
		entity.setDescription(trimToNull(request.description()));
		entity.setTechnologies(trimToNull(request.technologies()));
		entity.setProjectUrl(trimToNull(request.projectUrl()));
		entity.setRepoUrl(trimToNull(request.repoUrl()));
		return UserProjectDto.from(projectRepository.save(entity));
	}

	@Transactional
	public void deleteProject(Long currentUserId, Long projectId) {
		UserProjectEntity entity = projectRepository.findByIdAndUserId(projectId, currentUserId)
				.orElseThrow(() -> new NotFoundException("Project record not found"));
		projectRepository.delete(entity);
	}

	@Transactional
	public Map<String, String> uploadAvatar(Long currentUserId, MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new BadRequestException("Avatar image file is required");
		}
		String contentType = file.getContentType();
		if (contentType == null || !ALLOWED_AVATAR_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
			throw new BadRequestException("Invalid image format. Allowed formats: JPEG, PNG, WebP, GIF");
		}
		if (file.getSize() > MAX_AVATAR_SIZE) {
			throw new BadRequestException("Avatar image size must not exceed 5MB");
		}

		UserEntity user = getUser(currentUserId);
		String ext = getFileExtension(file.getOriginalFilename(), contentType);
		String key = "avatars/" + currentUserId + "/" + UUID.randomUUID() + "." + ext;

		if (user.getAvatarKey() != null) {
			storage.delete(user.getAvatarKey());
		}

		try (InputStream stream = file.getInputStream()) {
			storage.putObject(key, contentType, file.getSize(), stream);
		} catch (Exception e) {
			throw new RuntimeException("Failed to upload avatar", e);
		}

		user.setAvatarKey(key);
		user.setAvatarUrl("/api/v1/users/" + currentUserId + "/avatar");
		users.save(user);

		return Map.of("avatarUrl", user.getAvatarUrl());
	}

	@Transactional
	public void deleteAvatar(Long currentUserId) {
		UserEntity user = getUser(currentUserId);
		if (user.getAvatarKey() != null) {
			storage.delete(user.getAvatarKey());
		}
		user.setAvatarKey(null);
		user.setAvatarUrl(null);
		users.save(user);
	}

	@Transactional(readOnly = true)
	public AvatarStream getAvatarStream(Long userId) {
		UserEntity user = getUser(userId);
		if (user.getAvatarKey() == null) {
			throw new NotFoundException("Avatar not found");
		}
		var headOpt = storage.head(user.getAvatarKey());
		String contentType = headOpt.map(ObjectStorageService.ObjectHead::contentType).orElse("image/jpeg");
		long contentLength = headOpt.map(ObjectStorageService.ObjectHead::sizeBytes).orElse(0L);
		InputStream stream = storage.openStream(user.getAvatarKey());
		return new AvatarStream(stream, contentType, contentLength);
	}

	public record AvatarStream(InputStream stream, String contentType, long contentLength) {}

	private UserEntity getUser(Long userId) {
		return users.findById(userId)
				.orElseThrow(() -> new NotFoundException("User not found with id: " + userId));
	}

	private void validateEducationRequest(UpsertEducationRequest request) {
		List<FieldErrorResponse> errors = new ArrayList<>();
		if (request.institution() == null || request.institution().isBlank()) {
			errors.add(new FieldErrorResponse("institution", "Institution is required"));
		}
		if (request.startYear() != null && (request.startYear() < 1900 || request.startYear() > 2100)) {
			errors.add(new FieldErrorResponse("startYear", "Start year must be between 1900 and 2100"));
		}
		if (request.endYear() != null && (request.endYear() < 1900 || request.endYear() > 2100)) {
			errors.add(new FieldErrorResponse("endYear", "End year must be between 1900 and 2100"));
		}
		if (request.startYear() != null && request.endYear() != null && request.startYear() > request.endYear()) {
			errors.add(new FieldErrorResponse("endYear", "End year must be after start year"));
		}
		if (!errors.isEmpty()) {
			throw new InputValidationException("Education validation failed", errors);
		}
	}

	private void validateProjectRequest(UpsertProjectRequest request) {
		if (request.title() == null || request.title().isBlank()) {
			throw new InputValidationException("Project validation failed",
					List.of(new FieldErrorResponse("title", "Title is required")));
		}
	}

	private List<String> parseList(String raw) {
		if (raw == null || raw.isBlank()) return List.of();
		return Arrays.stream(raw.split(","))
				.map(String::trim)
				.filter(s -> !s.isEmpty())
				.toList();
	}

	private String trimToNull(String s) {
		if (s == null || s.isBlank()) return null;
		return s.trim();
	}

	private boolean containsMarkup(String value) {
		return value != null && (value.contains("<") || value.contains(">"));
	}

	private String getFileExtension(String filename, String contentType) {
		if (filename != null && filename.contains(".")) {
			return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
		}
		return switch (contentType != null ? contentType.toLowerCase(Locale.ROOT) : "") {
			case "image/png" -> "png";
			case "image/webp" -> "webp";
			case "image/gif" -> "gif";
			default -> "jpg";
		};
	}
}

