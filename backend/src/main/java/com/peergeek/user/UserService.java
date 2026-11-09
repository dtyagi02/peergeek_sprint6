package com.peergeek.user;

import java.io.InputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import com.peergeek.assignment.AssignmentEntity;
import com.peergeek.assignment.AssignmentRepository;
import com.peergeek.audit.AuditAction;
import com.peergeek.audit.AuditEvent;
import com.peergeek.audit.AuditService;
import com.peergeek.auth.AuthService;
import com.peergeek.auth.InputValidationException;
import com.peergeek.course.CourseEntity;
import com.peergeek.course.CourseRepository;
import com.peergeek.course.CourseStatus;
import com.peergeek.course.CourseTopicEntity;
import com.peergeek.enrollment.CourseProgressEntity;
import com.peergeek.enrollment.CourseProgressRepository;
import com.peergeek.enrollment.EnrollmentEntity;
import com.peergeek.enrollment.EnrollmentRepository;
import com.peergeek.enrollment.EnrollmentStatus;
import com.peergeek.exception.FieldErrorResponse;
import com.peergeek.exception.NotFoundException;
import com.peergeek.file.ObjectStorageService;
import com.peergeek.review.ReviewRepository;
import com.peergeek.security.CurrentUser;
import com.peergeek.security.OwnershipGuard;
import com.peergeek.submission.SubmissionEntity;
import com.peergeek.submission.SubmissionRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class UserService {
	private final UserRepository users;
	private final UserEducationRepository educationRepository;
	private final UserProjectRepository projectRepository;
	private final CourseRepository courseRepository;
	private final EnrollmentRepository enrollmentRepository;
	private final CourseProgressRepository courseProgressRepository;
	private final AssignmentRepository assignmentRepository;
	private final SubmissionRepository submissionRepository;
	private final ReviewRepository reviewRepository;
	private final ObjectStorageService storage;
	private final OwnershipGuard ownershipGuard;
	private final AuditService auditService;

	public UserService(
			UserRepository users,
			UserEducationRepository educationRepository,
			UserProjectRepository projectRepository,
			CourseRepository courseRepository,
			EnrollmentRepository enrollmentRepository,
			CourseProgressRepository courseProgressRepository,
			AssignmentRepository assignmentRepository,
			SubmissionRepository submissionRepository,
			ReviewRepository reviewRepository,
			ObjectStorageService storage,
			OwnershipGuard ownershipGuard,
			AuditService auditService) {
		this.users = users;
		this.educationRepository = educationRepository;
		this.projectRepository = projectRepository;
		this.courseRepository = courseRepository;
		this.enrollmentRepository = enrollmentRepository;
		this.courseProgressRepository = courseProgressRepository;
		this.assignmentRepository = assignmentRepository;
		this.submissionRepository = submissionRepository;
		this.reviewRepository = reviewRepository;
		this.storage = storage;
		this.ownershipGuard = ownershipGuard;
		this.auditService = auditService;
	}

	@PreAuthorize("isAuthenticated()")
	@Transactional(readOnly = true)
	public UserProfileDto currentProfile(Long currentUserId) {
		UserEntity user = getUser(currentUserId);
		ownershipGuard.requireOwner(user.getId(), currentUserId);
		return UserProfileDto.from(user);
	}

	@PreAuthorize("isAuthenticated()")
	@Transactional
	public UserProfileDto updateProfile(Long currentUserId, UpdateProfileRequest request) {
		UserEntity user = getUser(currentUserId);
		ownershipGuard.requireOwner(user.getId(), currentUserId);

		String displayName = trim(request.displayName());
		String headline = trimToNull(request.headline());
		String bio = trimToNull(request.bio());
		String teachingExpertise = trimToNull(request.teachingExpertise());

		List<FieldErrorResponse> fields = new ArrayList<>();
		if (displayName.isEmpty()) {
			fields.add(new FieldErrorResponse("displayName", "Display name is required"));
		} else if (displayName.length() > 100) {
			fields.add(new FieldErrorResponse("displayName", "Display name must be at most 100 characters"));
		} else if (containsMarkup(displayName)) {
			fields.add(new FieldErrorResponse("displayName", "HTML markup is not allowed"));
		}

		if (headline != null && headline.length() > 160) {
			fields.add(new FieldErrorResponse("headline", "Headline must be at most 160 characters"));
		} else if (containsMarkup(headline)) {
			fields.add(new FieldErrorResponse("headline", "HTML markup is not allowed"));
		}

		if (bio != null && bio.length() > 2000) {
			fields.add(new FieldErrorResponse("bio", "Bio must be at most 2000 characters"));
		} else if (containsMarkup(bio)) {
			fields.add(new FieldErrorResponse("bio", "HTML markup is not allowed"));
		}

		if (teachingExpertise != null && teachingExpertise.length() > 1000) {
			fields.add(new FieldErrorResponse("teachingExpertise", "Teaching expertise must be at most 1000 characters"));
		} else if (containsMarkup(teachingExpertise)) {
			fields.add(new FieldErrorResponse("teachingExpertise", "HTML markup is not allowed"));
		}

		validateUrl(request.githubUrl(), "githubUrl", fields);
		validateUrl(request.linkedinUrl(), "linkedinUrl", fields);
		validateUrl(request.portfolioUrl(), "portfolioUrl", fields);
		validateUrl(request.websiteUrl(), "websiteUrl", fields);

		if (request.username() != null && !request.username().isBlank()) {
			String newUsername = request.username().trim().toLowerCase(java.util.Locale.ROOT);
			if (!newUsername.equalsIgnoreCase(user.getUsername())) {
				if (newUsername.length() < 3 || newUsername.length() > 30) {
					fields.add(new FieldErrorResponse("username", "Username must be between 3 and 30 characters"));
				} else if (!Character.isLetter(newUsername.charAt(0))) {
					fields.add(new FieldErrorResponse("username", "Username must start with an English letter"));
				} else if (!AuthService.USERNAME_PATTERN.matcher(newUsername).matches()) {
					fields.add(new FieldErrorResponse("username", "Username can only contain letters, numbers, and underscores"));
				} else if (AuthService.RESERVED_USERNAMES.contains(newUsername)) {
					fields.add(new FieldErrorResponse("username", "This username is reserved and cannot be chosen"));
				} else {
					var existing = users.findByUsername(newUsername);
					if (existing.isPresent() && !existing.get().getId().equals(user.getId())) {
						fields.add(new FieldErrorResponse("username", "Username is already taken. Please choose another username."));
					} else {
						user.setUsername(newUsername);
					}
				}
			}
		}

		Map<String, String> normalizedSkillsMap = new LinkedHashMap<>();
		if (request.skills() != null) {
			for (String skill : request.skills()) {
				if (skill == null) continue;
				String trimmed = skill.trim();
				if (trimmed.isEmpty()) continue;
				if (trimmed.length() > 50) {
					fields.add(new FieldErrorResponse("skills", "Skill name must not exceed 50 characters: " + trimmed));
				} else if (containsMarkup(trimmed)) {
					fields.add(new FieldErrorResponse("skills", "HTML markup is not allowed in skills"));
				} else {
					normalizedSkillsMap.putIfAbsent(trimmed.toLowerCase(java.util.Locale.ROOT), trimmed);
				}
			}
		}

		if (!fields.isEmpty()) {
			throw new InputValidationException("Profile validation failed", fields);
		}

		user.updateProfile(displayName, headline, bio);
		user.setTeachingExpertise(teachingExpertise);
		user.setGithubUrl(trimToNull(request.githubUrl()));
		user.setLinkedinUrl(trimToNull(request.linkedinUrl()));
		user.setPortfolioUrl(trimToNull(request.portfolioUrl()));
		user.setWebsiteUrl(trimToNull(request.websiteUrl()));

		if (request.skills() != null) {
			user.setSkills(serializeList(new ArrayList<>(normalizedSkillsMap.values())));
		}
		if (request.interests() != null) {
			user.setInterests(serializeList(request.interests()));
		}
		if (request.showEducation() != null) {
			user.setShowEducation(request.showEducation());
		}
		if (request.showSocialLinks() != null) {
			user.setShowSocialLinks(request.showSocialLinks());
		}
		if (request.showLearningHistory() != null) {
			user.setShowLearningHistory(request.showLearningHistory());
		}

		auditService.record(new AuditEvent(currentUserId, AuditAction.USER_PROFILE_UPDATED,
				"USER", Long.toString(user.getId()), Map.of("fields", List.of("displayName", "headline", "bio"))));

		try {
			users.save(user);
		} catch (DataIntegrityViolationException ex) {
			throw new InputValidationException(
					"Username is already taken. Please choose another username.",
					List.of(new FieldErrorResponse("username", "Username is already taken. Please choose another username.")));
		}

		return UserProfileDto.from(user);
	}

	@PreAuthorize("isAuthenticated()")
	@Transactional
	public UserProfileDto updateMode(Long currentUserId, UserMode mode) {
		UserEntity user = getUser(currentUserId);
		ownershipGuard.requireOwner(user.getId(), currentUserId);
		user.changeMode(mode);
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
		String normalized = username == null ? "" : username.trim().toLowerCase(java.util.Locale.ROOT);
		UserEntity user = users.findByUsername(normalized)
				.orElseThrow(() -> new NotFoundException("The requested user was not found"));
		return PublicProfileDto.from(user);
	}

	@Transactional(readOnly = true)
	public Map<Long, PublicProfileDto> getPublicProfiles(Collection<Long> ids) {
		if (ids == null || ids.isEmpty()) {
			return Map.of();
		}
		return users.findAllById(ids).stream()
				.collect(Collectors.toMap(UserEntity::getId, PublicProfileDto::from));
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

		StudentLearningData learningData = getStudentLearningData(targetUser.getId(), isOwner, targetUser.isShowLearningHistory());
		InstructorStatsDto instructorStats = getInstructorStats(targetUser.getId(), isOwner);

		List<String> skills = parseList(targetUser.getSkills());
		List<String> interests = parseList(targetUser.getInterests());

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
				learningData.achievements(),
				instructorStats,
				learningData.stats(),
				learningData.enrolledCourses(),
				learningData.earnedSkills(),
				targetUser.getCreatedAt());
	}

	@Transactional(readOnly = true)
	public UserFullProfileDto getFullProfileByUsername(String username, Optional<CurrentUser> viewer) {
		String normalized = username == null ? "" : username.trim().toLowerCase(java.util.Locale.ROOT);
		UserEntity user = users.findByUsername(normalized)
				.orElseThrow(() -> new NotFoundException("The requested user was not found"));
		return getFullProfile(user.getId(), viewer);
	}

	@PreAuthorize("isAuthenticated()")
	@Transactional
	public UserEducationDto addEducation(Long currentUserId, UpsertEducationRequest request) {
		List<FieldErrorResponse> errors = new ArrayList<>();
		String institution = trim(request.institution());
		String degree = trimToNull(request.degree());
		String fieldOfStudy = trimToNull(request.fieldOfStudy());

		if (institution.isEmpty()) {
			errors.add(new FieldErrorResponse("institution", "Institution is required"));
		} else if (institution.length() > 200) {
			errors.add(new FieldErrorResponse("institution", "Institution must be at most 200 characters"));
		} else if (containsMarkup(institution)) {
			errors.add(new FieldErrorResponse("institution", "HTML markup is not allowed"));
		}

		if (degree != null && (degree.length() > 100 || containsMarkup(degree))) {
			errors.add(new FieldErrorResponse("degree", "Degree is invalid"));
		}
		if (fieldOfStudy != null && (fieldOfStudy.length() > 100 || containsMarkup(fieldOfStudy))) {
			errors.add(new FieldErrorResponse("fieldOfStudy", "Field of study is invalid"));
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

		UserEducationEntity entity = new UserEducationEntity(
				currentUserId, institution, degree, fieldOfStudy, request.startYear(), request.endYear());
		return UserEducationDto.from(educationRepository.save(entity));
	}

	@PreAuthorize("isAuthenticated()")
	@Transactional
	public UserEducationDto updateEducation(Long currentUserId, Long educationId, UpsertEducationRequest request) {
		UserEducationEntity entity = educationRepository.findByIdAndUserId(educationId, currentUserId)
				.orElseThrow(() -> new NotFoundException("Education record not found"));
		List<FieldErrorResponse> errors = new ArrayList<>();
		String institution = trim(request.institution());
		String degree = trimToNull(request.degree());
		String fieldOfStudy = trimToNull(request.fieldOfStudy());

		if (institution.isEmpty()) {
			errors.add(new FieldErrorResponse("institution", "Institution is required"));
		} else if (institution.length() > 200 || containsMarkup(institution)) {
			errors.add(new FieldErrorResponse("institution", "Institution is invalid"));
		}
		if (degree != null && (degree.length() > 100 || containsMarkup(degree))) {
			errors.add(new FieldErrorResponse("degree", "Degree is invalid"));
		}
		if (fieldOfStudy != null && (fieldOfStudy.length() > 100 || containsMarkup(fieldOfStudy))) {
			errors.add(new FieldErrorResponse("fieldOfStudy", "Field of study is invalid"));
		}
		if (request.startYear() != null && request.endYear() != null && request.startYear() > request.endYear()) {
			errors.add(new FieldErrorResponse("endYear", "End year must be after start year"));
		}

		if (!errors.isEmpty()) {
			throw new InputValidationException("Education validation failed", errors);
		}

		entity.update(institution, degree, fieldOfStudy, request.startYear(), request.endYear());
		return UserEducationDto.from(educationRepository.save(entity));
	}

	@PreAuthorize("isAuthenticated()")
	@Transactional
	public void deleteEducation(Long currentUserId, Long educationId) {
		UserEducationEntity entity = educationRepository.findByIdAndUserId(educationId, currentUserId)
				.orElseThrow(() -> new NotFoundException("Education record not found"));
		educationRepository.delete(entity);
	}

	@PreAuthorize("isAuthenticated()")
	@Transactional
	public UserProjectDto addProject(Long currentUserId, UpsertProjectRequest request) {
		List<FieldErrorResponse> errors = new ArrayList<>();
		String title = trim(request.title());
		String description = trimToNull(request.description());
		String technologies = trimToNull(request.technologies());

		if (title.isEmpty()) {
			errors.add(new FieldErrorResponse("title", "Project title is required"));
		} else if (title.length() > 150 || containsMarkup(title)) {
			errors.add(new FieldErrorResponse("title", "Project title is invalid"));
		}
		if (description != null && (description.length() > 2000 || containsMarkup(description))) {
			errors.add(new FieldErrorResponse("description", "Description is invalid"));
		}
		if (technologies != null && (technologies.length() > 500 || containsMarkup(technologies))) {
			errors.add(new FieldErrorResponse("technologies", "Technologies is invalid"));
		}

		validateProjectUrl(request.projectUrl(), "projectUrl", errors);
		validateProjectUrl(request.repoUrl(), "repoUrl", errors);

		if (!errors.isEmpty()) {
			throw new InputValidationException("Project validation failed", errors);
		}

		UserProjectEntity entity = new UserProjectEntity(
				currentUserId, title, description, technologies, trimToNull(request.projectUrl()), trimToNull(request.repoUrl()));
		return UserProjectDto.from(projectRepository.save(entity));
	}

	@PreAuthorize("isAuthenticated()")
	@Transactional
	public UserProjectDto updateProject(Long currentUserId, Long projectId, UpsertProjectRequest request) {
		UserProjectEntity entity = projectRepository.findByIdAndUserId(projectId, currentUserId)
				.orElseThrow(() -> new NotFoundException("Project record not found"));

		List<FieldErrorResponse> errors = new ArrayList<>();
		String title = trim(request.title());
		String description = trimToNull(request.description());
		String technologies = trimToNull(request.technologies());

		if (title.isEmpty() || title.length() > 150 || containsMarkup(title)) {
			errors.add(new FieldErrorResponse("title", "Project title is invalid"));
		}
		if (description != null && (description.length() > 2000 || containsMarkup(description))) {
			errors.add(new FieldErrorResponse("description", "Description is invalid"));
		}
		if (technologies != null && (technologies.length() > 500 || containsMarkup(technologies))) {
			errors.add(new FieldErrorResponse("technologies", "Technologies is invalid"));
		}

		validateProjectUrl(request.projectUrl(), "projectUrl", errors);
		validateProjectUrl(request.repoUrl(), "repoUrl", errors);

		if (!errors.isEmpty()) {
			throw new InputValidationException("Project validation failed", errors);
		}

		entity.update(title, description, technologies, trimToNull(request.projectUrl()), trimToNull(request.repoUrl()));
		return UserProjectDto.from(projectRepository.save(entity));
	}

	@PreAuthorize("isAuthenticated()")
	@Transactional
	public void deleteProject(Long currentUserId, Long projectId) {
		UserProjectEntity entity = projectRepository.findByIdAndUserId(projectId, currentUserId)
				.orElseThrow(() -> new NotFoundException("Project record not found"));
		projectRepository.delete(entity);
	}

	@PreAuthorize("isAuthenticated()")
	@Transactional
	public Map<String, String> uploadAvatar(Long currentUserId, MultipartFile file) {
		UserEntity user = getUser(currentUserId);
		ownershipGuard.requireOwner(user.getId(), currentUserId);

		if (file == null || file.isEmpty()) {
			throw new InputValidationException("File is empty", List.of(new FieldErrorResponse("file", "File is required")));
		}
		String contentType = file.getContentType();
		if (contentType == null || (!contentType.equals("image/jpeg") && !contentType.equals("image/png")
				&& !contentType.equals("image/webp") && !contentType.equals("image/gif"))) {
			throw new InputValidationException("Invalid file type",
					List.of(new FieldErrorResponse("file", "Only JPEG, PNG, WebP, and GIF images are allowed")));
		}
		if (file.getSize() > 5 * 1024 * 1024) {
			throw new InputValidationException("File too large",
					List.of(new FieldErrorResponse("file", "Avatar image size must not exceed 5MB")));
		}

		String ext = switch (contentType) {
			case "image/png" -> "png";
			case "image/webp" -> "webp";
			case "image/gif" -> "gif";
			default -> "jpg";
		};

		String key = "avatars/" + currentUserId + "/" + UUID.randomUUID() + "." + ext;
		if (user.getAvatarKey() != null) {
			try {
				storage.delete(user.getAvatarKey());
			} catch (Exception ignored) {
			}
		}

		try (InputStream stream = file.getInputStream()) {
			storage.putObject(key, contentType, file.getSize(), stream);
		} catch (IOException e) {
			throw new RuntimeException("Failed to upload avatar", e);
		}

		user.setAvatarKey(key);
		user.setAvatarUrl("/api/v1/users/" + currentUserId + "/avatar");
		users.save(user);

		return Map.of("avatarUrl", user.getAvatarUrl());
	}

	@PreAuthorize("isAuthenticated()")
	@Transactional
	public void deleteAvatar(Long currentUserId) {
		UserEntity user = getUser(currentUserId);
		ownershipGuard.requireOwner(user.getId(), currentUserId);
		if (user.getAvatarKey() != null) {
			try {
				storage.delete(user.getAvatarKey());
			} catch (Exception ignored) {
			}
		}
		user.setAvatarKey(null);
		user.setAvatarUrl(null);
		users.save(user);
	}

	public record AvatarStream(InputStream stream, String contentType, long contentLength) {
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

	@PreAuthorize("isAuthenticated()")
	@Transactional(readOnly = true)
	public CourseStudentDetailDto getCourseStudentDetail(Long courseId, Long studentId, Long instructorId) {
		CourseEntity course = courseRepository.findByIdAndDeletedAtIsNull(courseId)
				.orElseThrow(() -> new NotFoundException("Course not found"));
		if (!course.getOwner().getId().equals(instructorId)) {
			throw new AccessDeniedException("You are not authorized to view student records for this course");
		}
		UserEntity student = users.findById(studentId)
				.orElseThrow(() -> new NotFoundException("Student not found"));
		EnrollmentEntity enrollment = enrollmentRepository.findByUserIdAndCourseId(studentId, courseId)
				.orElseThrow(() -> new NotFoundException("The student is not enrolled in this course"));

		CourseProgressEntity progress = courseProgressRepository.findById(enrollment.getId()).orElse(null);
		int totalLessons = progress != null ? progress.getTotalLessons() : 0;
		int completedLessons = progress != null ? progress.getCompletedLessons() : 0;
		int progressPercent = progress != null ? progress.getPercent() : 0;
		java.time.Instant completedAt = progress != null ? progress.getCompletedAt() : null;

		List<AssignmentEntity> assignments = assignmentRepository.findByCourseId(courseId);
		Map<Long, String> assignmentTitles = assignments.stream()
				.collect(Collectors.toMap(AssignmentEntity::getId, AssignmentEntity::getTitle));
		List<Long> assignmentIds = assignments.stream().map(AssignmentEntity::getId).toList();

		List<CourseStudentDetailDto.StudentSubmissionSummary> submissions = new ArrayList<>();
		if (!assignmentIds.isEmpty()) {
			List<SubmissionEntity> userSubmissions = submissionRepository.findByUserIdAndAssignmentIdIn(studentId, assignmentIds);
			submissions = userSubmissions.stream()
					.map(s -> new CourseStudentDetailDto.StudentSubmissionSummary(
							s.getAssignmentId(),
							assignmentTitles.getOrDefault(s.getAssignmentId(), "Assignment #" + s.getAssignmentId()),
							s.getStatus().name(),
							s.getMarks(),
							s.getTotalPoints(),
							s.getSubmittedAt(),
							s.isLate()))
					.toList();
		}

		CourseStudentDetailDto.StudentSummary studentSummary = new CourseStudentDetailDto.StudentSummary(
				student.getId(),
				student.getUsername(),
				student.getDisplayName(),
				student.getHeadline(),
				student.getBio(),
				student.getAvatarUrl());

		return new CourseStudentDetailDto(
				studentSummary,
				course.getId(),
				course.getTitle(),
				enrollment.getStatus().name(),
				enrollment.getEnrolledAt(),
				enrollment.getLastAccessedAt(),
				totalLessons,
				completedLessons,
				progressPercent,
				completedAt,
				submissions);
	}

	private record StudentLearningData(
			StudentLearningStatsDto stats,
			List<EnrolledCourseCardDto> enrolledCourses,
			List<EarnedSkillDto> earnedSkills,
			List<CompletedCourseAchievementDto> achievements) {}

	private static class EarnedSkillBuilder {
		private final String skillName;
		private final List<String> sourceCourses = new ArrayList<>();
		private Instant latestEarnedAt;

		EarnedSkillBuilder(String skillName, Instant earnedAt) {
			this.skillName = skillName;
			this.latestEarnedAt = earnedAt;
		}

		void addCourse(String courseTitle, Instant earnedAt) {
			if (courseTitle != null && !sourceCourses.contains(courseTitle)) {
				sourceCourses.add(courseTitle);
			}
			if (earnedAt != null && (latestEarnedAt == null || earnedAt.isAfter(latestEarnedAt))) {
				latestEarnedAt = earnedAt;
			}
		}

		EarnedSkillDto toDto() {
			return new EarnedSkillDto(skillName, sourceCourses, latestEarnedAt);
		}
	}

	private StudentLearningData getStudentLearningData(Long userId, boolean isOwner, boolean showLearningHistory) {
		if (!isOwner && !showLearningHistory) {
			return new StudentLearningData(null, List.of(), List.of(), List.of());
		}

		List<EnrollmentEntity> enrollments = enrollmentRepository.findByUserIdAndStatus(userId, EnrollmentStatus.ACTIVE);
		if (enrollments.isEmpty()) {
			return new StudentLearningData(new StudentLearningStatsDto(0, 0, 0, 0), List.of(), List.of(), List.of());
		}

		List<Long> enrollmentIds = enrollments.stream().map(EnrollmentEntity::getId).toList();
		List<CourseProgressEntity> progresses = courseProgressRepository.findByEnrollmentIdIn(enrollmentIds);
		Map<Long, CourseProgressEntity> progressByEnrollment = progresses.stream()
				.collect(Collectors.toMap(CourseProgressEntity::getEnrollmentId, p -> p, (a, b) -> a));

		List<Long> courseIds = enrollments.stream().map(EnrollmentEntity::getCourseId).toList();
		Map<Long, CourseEntity> coursesById = courseRepository.findAllById(courseIds).stream()
				.filter(c -> c.getDeletedAt() == null)
				.collect(Collectors.toMap(CourseEntity::getId, c -> c));

		Map<Long, Double> avgRatings = new HashMap<>();
		Map<Long, Integer> reviewCounts = new HashMap<>();
		for (Object[] row : reviewRepository.getCourseRatingSummaries(courseIds)) {
			Long cid = (Long) row[0];
			Double avg = row[1] != null ? ((Number) row[1]).doubleValue() : 0.0;
			Integer cnt = row[2] != null ? ((Number) row[2]).intValue() : 0;
			avgRatings.put(cid, Math.round(avg * 10.0) / 10.0);
			reviewCounts.put(cid, cnt);
		}

		int totalEnrolled = enrollments.size();
		int completedCourses = 0;
		int lessonsCompleted = 0;
		Map<String, EarnedSkillBuilder> earnedSkillsMap = new LinkedHashMap<>();
		List<EnrolledCourseCardDto> cards = new ArrayList<>();
		List<CompletedCourseAchievementDto> achievements = new ArrayList<>();

		for (EnrollmentEntity enrollment : enrollments) {
			CourseEntity course = coursesById.get(enrollment.getCourseId());
			if (course == null) continue;

			CourseProgressEntity prog = progressByEnrollment.get(enrollment.getId());
			int totalLessons = prog != null ? prog.getTotalLessons() : 0;
			int completedL = prog != null ? prog.getCompletedLessons() : 0;
			int percent = prog != null ? Math.min(100, Math.max(0, prog.getPercent())) : 0;
			boolean isCompleted = prog != null && (prog.getCompletedAt() != null || percent >= 100);
			Instant completedAt = prog != null ? prog.getCompletedAt() : null;
			if (isCompleted && completedAt == null && prog != null) {
				completedAt = prog.getComputedAt();
			}

			lessonsCompleted += completedL;

			final Instant finalCompletedAt = completedAt;
			if (isCompleted) {
				completedCourses++;
				achievements.add(new CompletedCourseAchievementDto(
						course.getId(),
						course.getTitle(),
						finalCompletedAt != null ? finalCompletedAt : Instant.now()));

				if (course.getTopics() != null) {
					for (CourseTopicEntity topic : course.getTopics()) {
						String rawName = topic.getName();
						if (rawName == null || rawName.isBlank()) continue;
						String trimmed = rawName.trim();
						String key = trimmed.toLowerCase(java.util.Locale.ROOT);
						earnedSkillsMap.computeIfAbsent(key, k -> new EarnedSkillBuilder(trimmed, finalCompletedAt))
								.addCourse(course.getTitle(), finalCompletedAt);
					}
				}
			}

			if (isOwner || isCompleted) {
				String thumb = course.getThumbnailResourceId() != null
						? "/api/v1/public/courses/" + course.getId() + "/thumbnail?v=" + course.getThumbnailResourceId()
						: null;
				String instructorName = course.getOwner() != null ? course.getOwner().getDisplayName() : "Instructor";
				String instructorUsername = course.getOwner() != null ? course.getOwner().getUsername() : null;
				double rating = avgRatings.getOrDefault(course.getId(), 0.0);
				int reviews = reviewCounts.getOrDefault(course.getId(), 0);

				cards.add(new EnrolledCourseCardDto(
						course.getId(),
						course.getTitle(),
						course.getDescription(),
						thumb,
						instructorName,
						instructorUsername,
						course.getDifficulty() != null ? course.getDifficulty().name() : null,
						enrollment.getStatus().name(),
						percent,
						completedL,
						totalLessons,
						rating,
						reviews,
						isCompleted,
						completedAt,
						null));
			}
		}

		int inProgress = Math.max(0, totalEnrolled - completedCourses);
		StudentLearningStatsDto stats = new StudentLearningStatsDto(totalEnrolled, completedCourses, inProgress, lessonsCompleted);
		List<EarnedSkillDto> earnedSkills = earnedSkillsMap.values().stream().map(EarnedSkillBuilder::toDto).toList();

		return new StudentLearningData(stats, cards, earnedSkills, achievements);
	}

	private InstructorStatsDto getInstructorStats(Long instructorId, boolean isOwner) {
		List<CourseEntity> courses = isOwner
				? courseRepository.findByOwnerIdAndDeletedAtIsNull(instructorId)
				: courseRepository.findByOwnerIdAndStatusAndDeletedAtIsNull(instructorId, CourseStatus.PUBLISHED);

		List<CourseEntity> publishedCourses = courses.stream()
				.filter(c -> c.getStatus() == CourseStatus.PUBLISHED)
				.toList();

		if (courses.isEmpty()) {
			return new InstructorStatsDto(0, 0, 0.0, 0, List.of());
		}

		List<Long> publishedCourseIds = publishedCourses.stream().map(CourseEntity::getId).toList();
		long totalLearners = publishedCourseIds.isEmpty() ? 0 : enrollmentRepository.countDistinctActiveLearnersByCourseIds(publishedCourseIds);
		Double avgRating = publishedCourseIds.isEmpty() ? null : reviewRepository.getAverageRatingForCourses(publishedCourseIds);
		int totalReviews = publishedCourseIds.isEmpty() ? 0 : reviewRepository.countActiveReviewsForCourses(publishedCourseIds);

		List<Long> allCourseIds = courses.stream().map(CourseEntity::getId).toList();
		Map<Long, Long> enrolledCounts = new HashMap<>();
		for (Object[] row : enrollmentRepository.countActiveByCourseIds(allCourseIds)) {
			enrolledCounts.put((Long) row[0], ((Number) row[1]).longValue());
		}

		Map<Long, Double> ratings = new HashMap<>();
		Map<Long, Integer> reviewCounts = new HashMap<>();
		for (Object[] row : reviewRepository.getCourseRatingSummaries(allCourseIds)) {
			Long cid = (Long) row[0];
			Double avg = row[1] != null ? ((Number) row[1]).doubleValue() : 0.0;
			Integer cnt = row[2] != null ? ((Number) row[2]).intValue() : 0;
			ratings.put(cid, Math.round(avg * 10.0) / 10.0);
			reviewCounts.put(cid, cnt);
		}

		List<TeachingCourseCardDto> cards = courses.stream().map(c -> {
			String thumb = c.getThumbnailResourceId() != null
					? "/api/v1/public/courses/" + c.getId() + "/thumbnail?v=" + c.getThumbnailResourceId()
					: null;
			long enrolled = enrolledCounts.getOrDefault(c.getId(), 0L);
			double rating = ratings.getOrDefault(c.getId(), 0.0);
			int reviews = reviewCounts.getOrDefault(c.getId(), 0);
			return new TeachingCourseCardDto(
					c.getId(),
					c.getTitle(),
					c.getDescription(),
					c.getDifficulty() != null ? c.getDifficulty().name() : null,
					c.getStatus() != null ? c.getStatus().name() : "DRAFT",
					thumb,
					enrolled,
					rating,
					reviews,
					c.getPublishedAt());
		}).toList();

		return new InstructorStatsDto(
				publishedCourses.size(),
				totalLearners,
				avgRating != null ? Math.round(avgRating * 10.0) / 10.0 : 0.0,
				totalReviews,
				cards);
	}

	private UserEntity getUser(Long id) {
		return users.findById(id)
				.orElseThrow(() -> new NotFoundException("The requested user was not found"));
	}

	private String trim(String value) {
		return value == null ? "" : value.trim();
	}

	private String trimToNull(String value) {
		if (value == null || value.isBlank()) return null;
		return value.trim();
	}

	private boolean containsMarkup(String value) {
		return value != null && (value.contains("<") || value.contains(">"));
	}

	private void validateUrl(String url, String field, List<FieldErrorResponse> errors) {
		if (url == null || url.isBlank()) return;
		String trimmed = url.trim();
		if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
			errors.add(new FieldErrorResponse(field, "URL must use http or https scheme"));
		} else if (trimmed.length() > 255) {
			errors.add(new FieldErrorResponse(field, "URL must not exceed 255 characters"));
		}
	}

	private void validateProjectUrl(String url, String field, List<FieldErrorResponse> errors) {
		if (url == null || url.isBlank()) return;
		String trimmed = url.trim();
		if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
			errors.add(new FieldErrorResponse(field, "URL must use http or https scheme"));
		} else if (trimmed.length() > 500) {
			errors.add(new FieldErrorResponse(field, "URL must not exceed 500 characters"));
		}
	}

	private List<String> parseList(String raw) {
		if (raw == null || raw.isBlank()) return List.of();
		return java.util.Arrays.stream(raw.split(","))
				.map(String::trim)
				.filter(s -> !s.isEmpty())
				.toList();
	}

	private String serializeList(List<String> items) {
		if (items == null || items.isEmpty()) return null;
		return items.stream()
				.map(this::trim)
				.filter(s -> !s.isEmpty())
				.collect(Collectors.joining(","));
	}
}
