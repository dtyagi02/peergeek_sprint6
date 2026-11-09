package com.peergeek.admin;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.peergeek.admin.dto.AdminActivityDto;
import com.peergeek.admin.dto.AdminCourseDto;
import com.peergeek.admin.dto.AdminDashboardSummaryDto;
import com.peergeek.admin.dto.AdminReportDto;
import com.peergeek.admin.dto.AdminUserDto;
import com.peergeek.admin.dto.UpdateAdminReportRequest;
import com.peergeek.admin.dto.UpdateCourseStatusRequest;
import com.peergeek.admin.dto.UpdateUserStatusRequest;
import com.peergeek.audit.AuditAction;
import com.peergeek.audit.AuditEvent;
import com.peergeek.audit.AuditLogEntity;
import com.peergeek.audit.AuditLogRepository;
import com.peergeek.audit.AuditService;
import com.peergeek.auth.RefreshTokenRepository;
import com.peergeek.common.PageResponse;
import com.peergeek.course.CourseAuditActions;
import com.peergeek.course.CourseEntity;
import com.peergeek.course.CoursePublishedEvent;
import com.peergeek.course.CourseRepository;
import com.peergeek.course.CourseStatus;
import com.peergeek.course.CourseUnpublishedEvent;
import com.peergeek.enrollment.EnrollmentRepository;
import com.peergeek.enrollment.LearningAuditActions;
import com.peergeek.exception.BadRequestException;
import com.peergeek.exception.ForbiddenException;
import com.peergeek.exception.NotFoundException;
import com.peergeek.report.ReportCategory;
import com.peergeek.report.ReportEntity;
import com.peergeek.report.ReportRepository;
import com.peergeek.report.ReportService;
import com.peergeek.report.ReportStatus;
import com.peergeek.report.UpdateReportStatusRequest;
import com.peergeek.user.PublicProfileDto;
import com.peergeek.user.UserEntity;
import com.peergeek.user.UserMode;
import com.peergeek.user.UserRepository;
import com.peergeek.user.UserRole;
import com.peergeek.user.UserService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class AdminDashboardService {

	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AdminDashboardService.class);

	private final UserRepository userRepository;
	private final CourseRepository courseRepository;
	private final EnrollmentRepository enrollmentRepository;
	private final ReportRepository reportRepository;
	private final AuditLogRepository auditLogRepository;
	private final UserService userService;
	private final ReportService reportService;
	private final RefreshTokenRepository refreshTokenRepository;
	private final AuditService auditService;
	private final ApplicationEventPublisher eventPublisher;

	public AdminDashboardService(
			UserRepository userRepository,
			CourseRepository courseRepository,
			EnrollmentRepository enrollmentRepository,
			ReportRepository reportRepository,
			AuditLogRepository auditLogRepository,
			UserService userService,
			ReportService reportService,
			RefreshTokenRepository refreshTokenRepository,
			AuditService auditService,
			ApplicationEventPublisher eventPublisher) {
		this.userRepository = userRepository;
		this.courseRepository = courseRepository;
		this.enrollmentRepository = enrollmentRepository;
		this.reportRepository = reportRepository;
		this.auditLogRepository = auditLogRepository;
		this.userService = userService;
		this.reportService = reportService;
		this.refreshTokenRepository = refreshTokenRepository;
		this.auditService = auditService;
		this.eventPublisher = eventPublisher;
	}

	@Transactional(readOnly = true)
	public AdminDashboardSummaryDto getSummary() {
		long totalUsers = userRepository.count();
		long totalStudents = userRepository.countByLastMode(UserMode.STUDENT);
		long totalInstructors = userRepository.countByLastMode(UserMode.INSTRUCTOR);
		long totalCourses = courseRepository.countByDeletedAtIsNull();
		long publishedCourses = courseRepository.countByStatusAndDeletedAtIsNull(CourseStatus.PUBLISHED);
		long openReportsCount = reportRepository.countByStatusIn(List.of(ReportStatus.PENDING, ReportStatus.IN_REVIEW));
		long totalActivitiesCount = auditLogRepository.count();

		return new AdminDashboardSummaryDto(
				totalUsers,
				totalStudents,
				totalInstructors,
				totalCourses,
				publishedCourses,
				openReportsCount,
				totalActivitiesCount);
	}

	@Transactional(readOnly = true)
	public PageResponse<AdminActivityDto> getActivities(int page, int size) {
		int safePage = Math.max(0, page);
		int safeSize = Math.clamp(size, 1, 100);
		Pageable pageable = PageRequest.of(safePage, safeSize);
		Page<AuditLogEntity> logPage = auditLogRepository.findAllByOrderByCreatedAtDesc(pageable);

		List<AuditLogEntity> logs = logPage.getContent();
		Set<Long> userIds = new HashSet<>();
		Set<Long> courseIds = new HashSet<>();

		for (AuditLogEntity log : logs) {
			if (log.getActorUserId() != null) {
				userIds.add(log.getActorUserId());
			}
			Long detailCourseId = parseLongSafe(log.getDetails().get("courseId"));
			if (detailCourseId != null) {
				courseIds.add(detailCourseId);
			} else if ("COURSE".equalsIgnoreCase(log.getEntityType())) {
				Long entityCourseId = parseLongSafe(log.getEntityId());
				if (entityCourseId != null) {
					courseIds.add(entityCourseId);
				}
			}
		}

		Map<Long, PublicProfileDto> userProfiles = userService.getPublicProfiles(userIds);
		Map<Long, UserEntity> users = userRepository.findAllById(userIds).stream()
				.collect(Collectors.toMap(UserEntity::getId, Function.identity()));
		Map<Long, CourseEntity> courses = courseRepository.findAllById(courseIds).stream()
				.collect(Collectors.toMap(CourseEntity::getId, Function.identity()));

		List<AdminActivityDto> dtos = logs.stream().map(log -> {
			Long actorId = log.getActorUserId();
			PublicProfileDto actorProfile = actorId != null ? userProfiles.get(actorId) : null;
			UserEntity actorUser = actorId != null ? users.get(actorId) : null;
			String actorName = actorProfile != null ? actorProfile.displayName()
					: (actorUser != null ? actorUser.getDisplayName() : "System");
			String actorRole = actorUser != null ? actorUser.getRole().name() : null;

			Long courseId = parseLongSafe(log.getDetails().get("courseId"));
			if (courseId == null && "COURSE".equalsIgnoreCase(log.getEntityType())) {
				courseId = parseLongSafe(log.getEntityId());
			}
			CourseEntity course = courseId != null ? courses.get(courseId) : null;
			String courseTitle = course != null ? course.getTitle() : (String) log.getDetails().get("title");
			if (courseTitle == null && courseId != null) {
				courseTitle = "Course #" + courseId;
			}

			String action = log.getAction();
			String title = formatActivityTitle(action);
			String description = formatActivityDescription(action, actorName, actorRole, courseTitle, log.getDetails());

			return new AdminActivityDto(
					log.getId(),
					action,
					actorId,
					actorName,
					actorRole,
					log.getEntityType(),
					log.getEntityId(),
					title,
					description,
					log.getDetails(),
					log.getCreatedAt());
		}).toList();

		return new PageResponse<>(
				dtos,
				logPage.getNumber(),
				logPage.getSize(),
				logPage.getTotalElements(),
				logPage.getTotalPages());
	}

	@Transactional(readOnly = true)
	public PageResponse<AdminReportDto> getReports(String status, String query, int page, int size) {
		int safePage = Math.max(0, page);
		int safeSize = Math.clamp(size, 1, 100);
		Pageable pageable = PageRequest.of(safePage, safeSize);
		String safeQuery = (query != null && !query.trim().isEmpty()) ? query.trim() : null;

		List<ReportStatus> statuses = null;
		if (status != null && !status.trim().isEmpty() && !status.equalsIgnoreCase("ALL")) {
			if (status.equalsIgnoreCase("OPEN")) {
				statuses = List.of(ReportStatus.PENDING, ReportStatus.IN_REVIEW);
			} else {
				try {
					statuses = List.of(ReportStatus.valueOf(status.trim().toUpperCase()));
				} catch (IllegalArgumentException e) {
					log.warn("Invalid report status filter: {}", status);
				}
			}
		}

		boolean useStatuses = statuses != null && !statuses.isEmpty();
		Collection<ReportStatus> queryStatuses = useStatuses ? statuses : List.of(ReportStatus.PENDING);

		log.info("Querying admin reports: statusFilter={}, queryFilter={}, page={}, size={}",
				status, safeQuery, safePage, safeSize);
		Page<ReportEntity> reportsPage = reportRepository.findAdminReports(useStatuses, queryStatuses, safeQuery, pageable);
		log.info("Admin reports query completed: found {} reports across {} pages",
				reportsPage.getTotalElements(), reportsPage.getTotalPages());
		List<ReportEntity> reports = reportsPage.getContent();

		Set<Long> courseIds = reports.stream().map(ReportEntity::getCourseId).collect(Collectors.toSet());
		Set<Long> userIds = new HashSet<>();
		for (ReportEntity r : reports) {
			if (r.getReporterUserId() != null) userIds.add(r.getReporterUserId());
			if (r.getResolvedBy() != null) userIds.add(r.getResolvedBy());
		}

		Map<Long, CourseEntity> courses = courseRepository.findAllById(courseIds).stream()
				.collect(Collectors.toMap(CourseEntity::getId, Function.identity()));
		Map<Long, UserEntity> users = userRepository.findAllById(userIds).stream()
				.collect(Collectors.toMap(UserEntity::getId, Function.identity()));

		List<AdminReportDto> dtos = reports.stream().map(report -> {
			CourseEntity course = courses.get(report.getCourseId());
			String courseTitle = course != null ? course.getTitle() : "Course #" + report.getCourseId();

			UserEntity reporter = users.get(report.getReporterUserId());
			String reporterName = reporter != null ? reporter.getDisplayName() : "User";
			String reporterEmail = reporter != null ? reporter.getEmail() : "";

			UserEntity resolver = report.getResolvedBy() != null ? users.get(report.getResolvedBy()) : null;
			String resolverName = resolver != null ? resolver.getDisplayName() : null;

			return new AdminReportDto(
					report.getId(),
					report.getCourseId(),
					courseTitle,
					report.getReporterUserId(),
					reporterName,
					reporterEmail,
					report.getCategory(),
					report.getDetails(),
					report.getStatus(),
					report.getResolutionNotes(),
					report.getResolvedBy(),
					resolverName,
					report.getResolvedAt(),
					report.getCreatedAt());
		}).toList();

		return new PageResponse<>(
				dtos,
				reportsPage.getNumber(),
				reportsPage.getSize(),
				reportsPage.getTotalElements(),
				reportsPage.getTotalPages());
	}

	@Transactional
	public AdminReportDto updateReport(long reportId, UpdateAdminReportRequest request, long adminUserId) {
		log.info("Admin updating report status: reportId={}, newStatus={}, adminUserId={}",
				reportId, request.status(), adminUserId);
		reportService.updateReportStatus(
				reportId,
				adminUserId,
				true,
				new UpdateReportStatusRequest(request.status(), request.resolutionNotes()));

		ReportEntity report = reportRepository.findById(reportId)
				.orElseThrow(() -> new NotFoundException("Report not found"));

		CourseEntity course = courseRepository.findById(report.getCourseId()).orElse(null);
		String courseTitle = course != null ? course.getTitle() : "Course #" + report.getCourseId();

		UserEntity reporter = userRepository.findById(report.getReporterUserId()).orElse(null);
		String reporterName = reporter != null ? reporter.getDisplayName() : "User";
		String reporterEmail = reporter != null ? reporter.getEmail() : "";

		UserEntity resolver = report.getResolvedBy() != null ? userRepository.findById(report.getResolvedBy()).orElse(null) : null;
		String resolverName = resolver != null ? resolver.getDisplayName() : null;

		return new AdminReportDto(
				report.getId(),
				report.getCourseId(),
				courseTitle,
				report.getReporterUserId(),
				reporterName,
				reporterEmail,
				report.getCategory(),
				report.getDetails(),
				report.getStatus(),
				report.getResolutionNotes(),
				report.getResolvedBy(),
				resolverName,
				report.getResolvedAt(),
				report.getCreatedAt());
	}

	@Transactional(readOnly = true)
	public PageResponse<AdminUserDto> getUsers(
			UserRole role, UserMode mode, Boolean enabled, String query, int page, int size) {
		int safePage = Math.max(0, page);
		int safeSize = Math.clamp(size, 1, 100);
		Pageable pageable = PageRequest.of(safePage, safeSize);
		String safeQuery = (query != null && !query.trim().isEmpty()) ? query.trim() : null;

		Page<UserEntity> userPage = userRepository.findAdminUsers(role, mode, enabled, safeQuery, pageable);

		List<AdminUserDto> dtos = userPage.getContent().stream().map(user -> new AdminUserDto(
				user.getId(),
				user.getEmail(),
				user.getDisplayName(),
				user.getRole(),
				user.getLastMode(),
				user.isEnabled(),
				user.isEmailVerified(),
				user.getCreatedAt())).toList();

		return new PageResponse<>(
				dtos,
				userPage.getNumber(),
				userPage.getSize(),
				userPage.getTotalElements(),
				userPage.getTotalPages());
	}

	@Transactional
	public AdminUserDto updateUserStatus(long userId, UpdateUserStatusRequest request, long adminUserId) {
		if (userId == adminUserId) {
			throw new BadRequestException("Administrators cannot block their own account");
		}

		UserEntity user = userRepository.findById(userId)
				.orElseThrow(() -> new NotFoundException("User not found"));

		if (user.getRole() == UserRole.ADMIN) {
			throw new BadRequestException("Cannot block administrator accounts");
		}

		boolean previousEnabled = user.isEnabled();
		user.setEnabled(request.enabled());
		user = userRepository.save(user);

		if (!request.enabled()) {
			refreshTokenRepository.revokeAllByUserId(userId);
			auditService.record(new AuditEvent(
					adminUserId,
					AuditAction.USER_BLOCKED,
					"USER",
					Long.toString(userId),
					Map.of("reason", request.reason() != null ? request.reason() : "",
							"email", user.getEmail())));
			log.info("Admin userId={} blocked user account targetUserId={} reason={}",
					adminUserId, userId, request.reason());
		} else if (!previousEnabled && request.enabled()) {
			auditService.record(new AuditEvent(
					adminUserId,
					AuditAction.USER_UNBLOCKED,
					"USER",
					Long.toString(userId),
					Map.of("reason", request.reason() != null ? request.reason() : "",
							"email", user.getEmail())));
			log.info("Admin userId={} unblocked user account targetUserId={} reason={}",
					adminUserId, userId, request.reason());
		}

		return new AdminUserDto(
				user.getId(),
				user.getEmail(),
				user.getDisplayName(),
				user.getRole(),
				user.getLastMode(),
				user.isEnabled(),
				user.isEmailVerified(),
				user.getCreatedAt());
	}

	@Transactional(readOnly = true)
	public PageResponse<AdminCourseDto> getCourses(CourseStatus status, String query, int page, int size) {
		int safePage = Math.max(0, page);
		int safeSize = Math.clamp(size, 1, 100);
		Pageable pageable = PageRequest.of(safePage, safeSize);
		String safeQuery = (query != null && !query.trim().isEmpty()) ? query.trim() : null;

		Page<CourseEntity> coursePage = courseRepository.findAdminCourses(status, safeQuery, pageable);
		List<CourseEntity> courses = coursePage.getContent();

		Set<Long> courseIds = courses.stream().map(CourseEntity::getId).collect(Collectors.toSet());
		Map<Long, Long> enrollmentCounts = new HashMap<>();
		Map<Long, Long> openReportCounts = new HashMap<>();

		if (!courseIds.isEmpty()) {
			List<Object[]> counts = enrollmentRepository.countActiveByCourseIds(courseIds);
			for (Object[] row : counts) {
				Long cId = (Long) row[0];
				Long count = (Long) row[1];
				enrollmentCounts.put(cId, count);
			}

			List<Object[]> reportCounts = reportRepository.countOpenReportsByCourseIds(courseIds);
			for (Object[] row : reportCounts) {
				Long cId = (Long) row[0];
				Long count = (Long) row[1];
				openReportCounts.put(cId, count);
			}
		}

		List<AdminCourseDto> dtos = courses.stream().map(course -> new AdminCourseDto(
				course.getId(),
				course.getTitle(),
				course.getOwner().getId(),
				course.getOwner().getDisplayName(),
				course.getOwner().getEmail(),
				course.getCategory() != null ? course.getCategory().getName() : null,
				course.getDifficulty(),
				course.getStatus(),
				enrollmentCounts.getOrDefault(course.getId(), 0L),
				openReportCounts.getOrDefault(course.getId(), 0L),
				course.getPublishedAt(),
				course.getCreatedAt())).toList();

		return new PageResponse<>(
				dtos,
				coursePage.getNumber(),
				coursePage.getSize(),
				coursePage.getTotalElements(),
				coursePage.getTotalPages());
	}

	@Transactional(readOnly = true)
	public AdminCourseDto getCourseById(long courseId) {
		CourseEntity course = courseRepository.findByIdAndDeletedAtIsNull(courseId)
				.orElseThrow(() -> new NotFoundException("Course not found"));

		List<Object[]> enrollmentRows = enrollmentRepository.countActiveByCourseIds(Set.of(courseId));
		long enrollmentCount = enrollmentRows.isEmpty() ? 0L : ((Number) enrollmentRows.get(0)[1]).longValue();

		List<Object[]> reportRows = reportRepository.countOpenReportsByCourseIds(Set.of(courseId));
		long openReportsCount = reportRows.isEmpty() ? 0L : ((Number) reportRows.get(0)[1]).longValue();

		return new AdminCourseDto(
				course.getId(),
				course.getTitle(),
				course.getOwner().getId(),
				course.getOwner().getDisplayName(),
				course.getOwner().getEmail(),
				course.getCategory() != null ? course.getCategory().getName() : null,
				course.getDifficulty(),
				course.getStatus(),
				enrollmentCount,
				openReportsCount,
				course.getPublishedAt(),
				course.getCreatedAt());
	}

	@Transactional
	public AdminCourseDto updateCourseStatus(long courseId, UpdateCourseStatusRequest request, long adminUserId) {
		CourseEntity course = courseRepository.findByIdAndDeletedAtIsNull(courseId)
				.orElseThrow(() -> new NotFoundException("Course not found"));

		CourseStatus requested = request.status();
		CourseStatus current = course.getStatus();

		if (requested != current) {
			Instant now = Instant.now();
			if (requested == CourseStatus.UNPUBLISHED) {
				courseRepository.transitionStatus(courseId, Set.of(CourseStatus.PUBLISHED, CourseStatus.DRAFT),
						CourseStatus.UNPUBLISHED, now);
				publishAfterCommit(new CourseUnpublishedEvent(courseId, adminUserId, now));
				auditService.record(new AuditEvent(adminUserId, "COURSE_UNPUBLISHED", "COURSE", Long.toString(courseId),
						Map.of("reason", request.reason() != null ? request.reason() : "",
								"reportId", request.reportId() != null ? request.reportId().toString() : "",
								"moderatedByAdmin", "true")));
				log.info("Admin userId={} unpublished course courseId={} reason={}", adminUserId, courseId, request.reason());
			} else if (requested == CourseStatus.PUBLISHED) {
				courseRepository.transitionStatus(courseId, Set.of(CourseStatus.UNPUBLISHED, CourseStatus.DRAFT),
						CourseStatus.PUBLISHED, now);
				publishAfterCommit(new CoursePublishedEvent(courseId, adminUserId, now));
				auditService.record(new AuditEvent(adminUserId, "COURSE_PUBLISHED", "COURSE", Long.toString(courseId),
						Map.of("reason", request.reason() != null ? request.reason() : "",
								"reportId", request.reportId() != null ? request.reportId().toString() : "",
								"moderatedByAdmin", "true")));
				log.info("Admin userId={} published/restored course courseId={} reason={}", adminUserId, courseId, request.reason());
			} else if (requested == CourseStatus.DRAFT) {
				courseRepository.transitionStatus(courseId, Set.of(CourseStatus.PUBLISHED, CourseStatus.UNPUBLISHED),
						CourseStatus.DRAFT, now);
				if (current == CourseStatus.PUBLISHED) {
					publishAfterCommit(new CourseUnpublishedEvent(courseId, adminUserId, now));
				}
				auditService.record(new AuditEvent(adminUserId, "COURSE_UPDATED", "COURSE", Long.toString(courseId),
						Map.of("status", "DRAFT",
								"reason", request.reason() != null ? request.reason() : "",
								"moderatedByAdmin", "true")));
				log.info("Admin userId={} moved course to DRAFT courseId={} reason={}", adminUserId, courseId, request.reason());
			}
		}

		return getCourseById(courseId);
	}

	private void publishAfterCommit(Object event) {
		eventPublisher.publishEvent(event);
	}

	private Long parseLongSafe(Object val) {
		if (val == null) return null;
		if (val instanceof Number n) return n.longValue();
		try {
			return Long.parseLong(val.toString());
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private String formatActivityTitle(String action) {
		if (action == null) return "Activity";
		return switch (action) {
			case AuditAction.USER_REGISTERED -> "New User Registration";
			case AuditAction.AUTH_BOOTSTRAP_ADMIN_CREATED -> "Bootstrap Admin Created";
			case AuditAction.AUTH_EMAIL_VERIFIED -> "Email Verified";
			case AuditAction.REPORT_CREATED -> "Course Issue Reported";
			case AuditAction.REPORT_STATUS_UPDATED -> "Issue Report Moderated";
			case CourseAuditActions.COURSE_CREATED -> "Course Created";
			case CourseAuditActions.COURSE_UPDATED -> "Course Updated";
			case CourseAuditActions.COURSE_DELETED -> "Course Deleted";
			case "COURSE_PUBLISHED" -> "Course Published";
			case "COURSE_UNPUBLISHED" -> "Course Unpublished";
			case LearningAuditActions.LEARNER_ENROLLED -> "New Course Enrollment";
			case LearningAuditActions.LEARNER_WITHDRAWN -> "Learner Withdrawn";
			case AuditAction.REVIEW_CREATED -> "New Course Review";
			case AuditAction.USER_BLOCKED -> "User Account Blocked";
			case AuditAction.USER_UNBLOCKED -> "User Account Unblocked";
			default -> humanize(action);
		};
	}

	private String formatActivityDescription(
			String action, String actorName, String actorRole, String courseTitle, Map<String, Object> details) {
		if (action == null) return actorName + " performed an action";
		String titleRef = (courseTitle != null && !courseTitle.isBlank()) ? " \"" + courseTitle + "\"" : "";

		return switch (action) {
			case AuditAction.USER_REGISTERED -> {
				String roleStr = (String) details.getOrDefault("role", actorRole != null ? actorRole : "USER");
				yield actorName + " registered as " + roleStr;
			}
			case AuditAction.AUTH_BOOTSTRAP_ADMIN_CREATED -> "Bootstrap administrator account was initialized";
			case AuditAction.AUTH_EMAIL_VERIFIED -> actorName + " verified their email address";
			case AuditAction.USER_BLOCKED -> {
				String targetEmail = (String) details.getOrDefault("email", "user");
				String reason = (String) details.getOrDefault("reason", "");
				String reasonStr = (reason != null && !reason.isBlank()) ? " (Reason: " + reason + ")" : "";
				yield actorName + " blocked account " + targetEmail + reasonStr;
			}
			case AuditAction.USER_UNBLOCKED -> {
				String targetEmail = (String) details.getOrDefault("email", "user");
				yield actorName + " restored account access for " + targetEmail;
			}
			case AuditAction.REPORT_CREATED -> {
				String cat = (String) details.getOrDefault("category", "an issue");
				yield actorName + " reported " + cat.toLowerCase().replace('_', ' ') + " on" + titleRef;
			}
			case AuditAction.REPORT_STATUS_UPDATED -> {
				String status = (String) details.getOrDefault("status", "updated");
				yield actorName + " updated report status to " + status + " on" + titleRef;
			}
			case CourseAuditActions.COURSE_CREATED -> actorName + " created course" + titleRef;
			case CourseAuditActions.COURSE_UPDATED -> actorName + " updated details for" + titleRef;
			case CourseAuditActions.COURSE_DELETED -> actorName + " deleted course" + titleRef;
			case "COURSE_PUBLISHED" -> actorName + " published course" + titleRef;
			case "COURSE_UNPUBLISHED" -> actorName + " unpublished course" + titleRef;
			case LearningAuditActions.LEARNER_ENROLLED -> actorName + " enrolled in" + titleRef;
			case LearningAuditActions.LEARNER_WITHDRAWN -> actorName + " withdrew from" + titleRef;
			case AuditAction.REVIEW_CREATED -> {
				Object rating = details.get("rating");
				yield actorName + " submitted a " + (rating != null ? rating + "-star " : "") + "review for" + titleRef;
			}
			default -> actorName + " triggered " + action;
		};
	}

	private String humanize(String text) {
		String[] words = text.toLowerCase().split("_");
		StringBuilder sb = new StringBuilder();
		for (String w : words) {
			if (!w.isEmpty()) {
				sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(" ");
			}
		}
		return sb.toString().trim();
	}
}
