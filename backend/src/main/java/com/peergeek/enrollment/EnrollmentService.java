package com.peergeek.enrollment;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import com.peergeek.audit.AuditEvent;
import com.peergeek.audit.AuditService;
import com.peergeek.common.PageResponse;
import com.peergeek.course.CategoryDto;
import com.peergeek.course.CourseDifficulty;
import com.peergeek.course.CourseMetadataSnapshot;
import com.peergeek.course.CourseStatus;
import com.peergeek.course.CourseStructureQueryService;
import com.peergeek.exception.BadRequestException;
import com.peergeek.exception.NotFoundException;
import com.peergeek.user.PublicProfileDto;
import com.peergeek.user.UserService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class EnrollmentService {

	private final EnrollmentRepository enrollmentRepository;
	private final ProgressService progressService;
	private final CourseStructureQueryService courseStructureQueryService;
	private final UserService userService;
	private final AuditService auditService;
	private final ApplicationEventPublisher eventPublisher;
	private final NamedParameterJdbcTemplate jdbcTemplate;

	public EnrollmentService(
			EnrollmentRepository enrollmentRepository,
			ProgressService progressService,
			CourseStructureQueryService courseStructureQueryService,
			UserService userService,
			AuditService auditService,
			ApplicationEventPublisher eventPublisher,
			NamedParameterJdbcTemplate jdbcTemplate) {
		this.enrollmentRepository = enrollmentRepository;
		this.progressService = progressService;
		this.courseStructureQueryService = courseStructureQueryService;
		this.userService = userService;
		this.auditService = auditService;
		this.eventPublisher = eventPublisher;
		this.jdbcTemplate = jdbcTemplate;
	}

	@Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
	public EnrollmentDto enroll(long courseId, long userId) {
		CourseMetadataSnapshot metadata = courseStructureQueryService.getCourseMetadata(courseId)
				.orElseThrow(() -> new NotFoundException("The requested resource was not found"));

		if (metadata.deleted() || metadata.status() != CourseStatus.PUBLISHED) {
			throw new NotFoundException("The requested resource was not found");
		}

		if (metadata.ownerId().equals(userId)) {
			throw new CannotEnrollOwnCourseException();
		}

		Optional<EnrollmentEntity> existing = enrollmentRepository.findByUserIdAndCourseId(userId, courseId);
		if (existing.isPresent()) {
			EnrollmentEntity enrollment = existing.get();
			if (enrollment.getStatus() == EnrollmentStatus.ACTIVE) {
				CourseProgressDto progress = progressService.getOrRecomputeProgress(enrollment);
				return new EnrollmentDto(courseId, enrollment.getStatus(), enrollment.getEnrolledAt(), progress);
			}

			// Reactivate withdrawn enrollment
			enrollment.setStatus(EnrollmentStatus.ACTIVE);
			enrollment.setEnrolledAt(Instant.now());
			enrollment.setWithdrawnAt(null);
			enrollment = enrollmentRepository.save(enrollment);

			CourseProgressDto progress = progressService.getOrRecomputeProgress(enrollment);
			auditService.record(new AuditEvent(
					userId,
					LearningAuditActions.LEARNER_ENROLLED,
					"ENROLLMENT",
					enrollment.getId().toString(),
					Map.of("courseId", courseId)));
			publishAfterCommit(new LearnerEnrolledEvent(courseId, userId, enrollment.getId()));
			return new EnrollmentDto(courseId, enrollment.getStatus(), enrollment.getEnrolledAt(), progress);
		}

		// Insert via jdbcTemplate to handle concurrent duplicate gracefully without corrupting Hibernate session
		MapSqlParameterSource insertParams = new MapSqlParameterSource()
				.addValue("userId", userId)
				.addValue("courseId", courseId);
		int updated = jdbcTemplate.update(
				"INSERT INTO enrollments (user_id, course_id, status, enrolled_at, version, created_at, updated_at) " +
				"VALUES (:userId, :courseId, 'ACTIVE', NOW(), 0, NOW(), NOW()) " +
				"ON DUPLICATE KEY UPDATE updated_at = updated_at",
				insertParams);

		EnrollmentEntity enrollment = enrollmentRepository.findByUserIdAndCourseId(userId, courseId)
				.orElseThrow(() -> new IllegalStateException("Enrollment should exist"));

		CourseProgressDto progress = progressService.getOrRecomputeProgress(enrollment);

		// If this call actually inserted the row (updated == 1), record audit and publish event
		if (updated == 1) {
			auditService.record(new AuditEvent(
					userId,
					LearningAuditActions.LEARNER_ENROLLED,
					"ENROLLMENT",
					enrollment.getId().toString(),
					Map.of("courseId", courseId)));
			publishAfterCommit(new LearnerEnrolledEvent(courseId, userId, enrollment.getId()));
		}

		return new EnrollmentDto(courseId, enrollment.getStatus(), enrollment.getEnrolledAt(), progress);
	}

	@Transactional
	public void withdraw(long courseId, long userId) {
		EnrollmentEntity enrollment = enrollmentRepository.findByUserIdAndCourseId(userId, courseId)
				.orElseThrow(() -> new NotFoundException("The requested resource was not found"));

		if (enrollment.getStatus() == EnrollmentStatus.WITHDRAWN) {
			// Idempotent for already withdrawn
			return;
		}

		enrollment.setStatus(EnrollmentStatus.WITHDRAWN);
		enrollment.setWithdrawnAt(Instant.now());
		enrollmentRepository.save(enrollment);

		auditService.record(new AuditEvent(
				userId,
				LearningAuditActions.LEARNER_WITHDRAWN,
				"ENROLLMENT",
				enrollment.getId().toString(),
				Map.of("courseId", courseId)));
		publishAfterCommit(new LearnerWithdrawnEvent(courseId, userId));
	}

	@Transactional(readOnly = true)
	public EnrollmentStatusDto getEnrollmentStatus(long courseId, long userId) {
		CourseMetadataSnapshot metadata = courseStructureQueryService.getCourseMetadata(courseId)
				.orElseThrow(() -> new NotFoundException("The requested resource was not found"));

		if (metadata.deleted()) {
			throw new NotFoundException("The requested resource was not found");
		}

		Optional<EnrollmentEntity> enrollmentOpt = enrollmentRepository.findByUserIdAndCourseId(userId, courseId);
		boolean isOwner = metadata.ownerId().equals(userId);
		boolean isPublished = metadata.status() == CourseStatus.PUBLISHED;
		boolean isActiveEnrolled = enrollmentOpt.isPresent() && enrollmentOpt.get().getStatus() == EnrollmentStatus.ACTIVE;

		// If course is not published, only active enrolled learners or the course owner may view it
		if (!isPublished && !isActiveEnrolled && !isOwner) {
			throw new NotFoundException("The requested resource was not found");
		}

		boolean canEnroll = isPublished && !isOwner && !isActiveEnrolled;
		EnrollmentStatus status = enrollmentOpt.map(EnrollmentEntity::getStatus).orElse(null);
		CourseProgressDto progress = enrollmentOpt.isPresent()
				? progressService.getOrRecomputeProgress(enrollmentOpt.get())
				: null;

		return new EnrollmentStatusDto(isActiveEnrolled, status, isOwner, canEnroll, progress);
	}

	@Transactional
	public PageResponse<MyCourseCardDto> getMyCourses(
			long userId, String statusFilter, String q, int page, int size, String sort) {
		int safePage = Math.max(0, page);
		int safeSize = Math.clamp(size, 1, 100);

		String sortSql = resolveSort(sort);

		StringBuilder whereSql = new StringBuilder(" WHERE e.user_id = :userId AND e.status = 'ACTIVE' AND c.deleted_at IS NULL ");
		MapSqlParameterSource params = new MapSqlParameterSource();
		params.addValue("userId", userId);

		if (statusFilter != null && !statusFilter.isBlank()) {
			String filter = statusFilter.trim().toLowerCase();
			if ("in-progress".equals(filter)) {
				whereSql.append(" AND (cp.completed_at IS NULL OR cp.percent < 100) ");
			} else if ("completed".equals(filter)) {
				whereSql.append(" AND cp.completed_at IS NOT NULL ");
			} else if (!"all".equals(filter)) {
				throw new BadRequestException("Unknown status filter: " + statusFilter);
			}
		}

		if (q != null && !q.isBlank()) {
			whereSql.append(" AND LOWER(c.title) LIKE :query ");
			params.addValue("query", "%" + q.trim().toLowerCase() + "%");
		}

		String countQuery = "SELECT COUNT(e.id) FROM enrollments e "
				+ "JOIN courses c ON e.course_id = c.id "
				+ "LEFT JOIN course_progress cp ON e.id = cp.enrollment_id "
				+ whereSql;

		Integer total = jdbcTemplate.queryForObject(countQuery, params, Integer.class);
		int totalElements = total == null ? 0 : total;

		if (totalElements == 0) {
			return PageResponse.from(new PageImpl<>(List.of(), PageRequest.of(safePage, safeSize), 0));
		}

		params.addValue("limit", safeSize);
		params.addValue("offset", safePage * safeSize);

		String dataQuery = "SELECT e.id, e.course_id, e.status, e.enrolled_at, e.last_lesson_id, e.last_accessed_at, e.version, "
				+ "c.title, c.difficulty, c.status AS course_status, c.thumbnail_resource_id, c.owner_id, "
				+ "cat.id AS category_id, cat.name AS category_name, cat.slug AS category_slug, "
				+ "cp.total_lessons, cp.completed_lessons, cp.percent, cp.completed_at, cp.stale "
				+ "FROM enrollments e "
				+ "JOIN courses c ON e.course_id = c.id "
				+ "LEFT JOIN categories cat ON c.category_id = cat.id "
				+ "LEFT JOIN course_progress cp ON e.id = cp.enrollment_id "
				+ whereSql
				+ " ORDER BY " + sortSql
				+ " LIMIT :limit OFFSET :offset";

		List<MyCourseRow> rows = jdbcTemplate.query(dataQuery, params, (rs, rowNum) -> mapRow(rs));

		// Bulk resolve instructor profiles
		Set<Long> ownerIds = rows.stream().map(r -> r.ownerId).collect(Collectors.toSet());
		Map<Long, PublicProfileDto> instructorProfiles = userService.getPublicProfiles(ownerIds);

		List<Long> enrollmentIds = rows.stream().map(r -> r.enrollmentId).toList();
		List<EnrollmentEntity> enrollments = enrollmentRepository.findAllById(enrollmentIds);
		Map<Long, EnrollmentEntity> enrollmentsById = enrollments.stream()
				.collect(Collectors.toMap(EnrollmentEntity::getId, e -> e));
		Map<Long, Long> continueLessonIds = progressService.getContinueLessonIdsBulk(enrollments);

		List<MyCourseCardDto> cards = new ArrayList<>(rows.size());
		for (MyCourseRow row : rows) {
			EnrollmentEntity enrollment = enrollmentsById.get(row.enrollmentId);
			if (enrollment == null) {
				continue;
			}

			CourseProgressDto progress;
			if (row.stale || row.totalLessons == null) {
				progress = progressService.getOrRecomputeProgress(enrollment);
			} else {
				progress = new CourseProgressDto(
						row.completedLessons,
						row.totalLessons,
						row.percent,
						row.completedAt);
			}

			Long continueLessonId = continueLessonIds.get(row.enrollmentId);

			PublicProfileDto profile = instructorProfiles.get(row.ownerId);
			InstructorSummaryDto instructor = new InstructorSummaryDto(
					row.ownerId,
					profile != null ? profile.displayName() : "Instructor");

			String thumbnailUrl = row.thumbnailResourceId != null
					? "/api/v1/public/courses/" + row.courseId + "/thumbnail?v=" + row.thumbnailResourceId
					: null;

			CategoryDto category = row.categoryId != null
					? new CategoryDto(row.categoryId, row.categoryName, row.categorySlug)
					: null;

			cards.add(new MyCourseCardDto(
					row.courseId,
					row.title,
					thumbnailUrl,
					instructor,
					row.difficulty,
					row.courseStatus,
					progress,
					row.lastAccessedAt,
					continueLessonId,
					category));
		}

		return PageResponse.from(new PageImpl<>(cards, PageRequest.of(safePage, safeSize), totalElements));
	}

	private String resolveSort(String sort) {
		if (sort == null || sort.isBlank()) {
			return "e.last_accessed_at DESC, e.id DESC";
		}

		String[] parts = sort.split(",");
		String field = parts[0].trim();
		String direction = (parts.length > 1 && "asc".equalsIgnoreCase(parts[1].trim())) ? "ASC" : "DESC";

		return switch (field) {
			case "lastAccessedAt" -> "e.last_accessed_at " + direction + ", e.id DESC";
			case "enrolledAt" -> "e.enrolled_at " + direction + ", e.id DESC";
			case "title" -> "c.title " + direction + ", e.id DESC";
			case "progress" -> "COALESCE(cp.percent, 0) " + direction + ", e.id DESC";
			default -> throw new BadRequestException("Unknown sort: " + sort);
		};
	}

	private MyCourseRow mapRow(ResultSet rs) throws SQLException {
		MyCourseRow row = new MyCourseRow();
		row.enrollmentId = rs.getLong("id");
		row.courseId = rs.getLong("course_id");
		row.status = EnrollmentStatus.valueOf(rs.getString("status"));
		row.enrolledAt = rs.getTimestamp("enrolled_at").toInstant();
		long lastLesson = rs.getLong("last_lesson_id");
		row.lastLessonId = rs.wasNull() ? null : lastLesson;
		java.sql.Timestamp lastAccessed = rs.getTimestamp("last_accessed_at");
		row.lastAccessedAt = lastAccessed == null ? null : lastAccessed.toInstant();

		row.title = rs.getString("title");
		String diffStr = rs.getString("difficulty");
		row.difficulty = diffStr == null ? null : CourseDifficulty.valueOf(diffStr);
		row.courseStatus = rs.getString("course_status");
		long thumb = rs.getLong("thumbnail_resource_id");
		row.thumbnailResourceId = rs.wasNull() ? null : thumb;
		row.ownerId = rs.getLong("owner_id");

		long catId = rs.getLong("category_id");
		if (!rs.wasNull()) {
			row.categoryId = catId;
			row.categoryName = rs.getString("category_name");
			row.categorySlug = rs.getString("category_slug");
		}

		int totalLessons = rs.getInt("total_lessons");
		if (rs.wasNull()) {
			row.totalLessons = null;
		} else {
			row.totalLessons = totalLessons;
			row.completedLessons = rs.getInt("completed_lessons");
			row.percent = rs.getInt("percent");
			java.sql.Timestamp comp = rs.getTimestamp("completed_at");
			row.completedAt = comp == null ? null : comp.toInstant();
			row.stale = rs.getBoolean("stale");
		}

		return row;
	}

	private void publishAfterCommit(Object event) {
		if (TransactionSynchronizationManager.isActualTransactionActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					eventPublisher.publishEvent(event);
				}
			});
		} else {
			eventPublisher.publishEvent(event);
		}
	}

	private static class MyCourseRow {
		Long enrollmentId;
		Long courseId;
		EnrollmentStatus status;
		Instant enrolledAt;
		Long lastLessonId;
		Instant lastAccessedAt;
		String title;
		CourseDifficulty difficulty;
		String courseStatus;
		Long thumbnailResourceId;
		Long ownerId;
		Long categoryId;
		String categoryName;
		String categorySlug;
		Integer totalLessons;
		int completedLessons;
		int percent;
		Instant completedAt;
		boolean stale;
	}
}
