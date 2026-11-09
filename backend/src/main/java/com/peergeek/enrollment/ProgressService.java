package com.peergeek.enrollment;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import com.peergeek.course.CourseMetadataSnapshot;
import com.peergeek.course.CourseStructureQueryService;
import com.peergeek.course.LessonLocation;
import com.peergeek.exception.NotFoundException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class ProgressService {

	private final CourseProgressRepository courseProgressRepository;
	private final LessonProgressRepository lessonProgressRepository;
	private final EnrollmentRepository enrollmentRepository;
	private final CourseStructureQueryService courseStructureQueryService;
	private final ApplicationEventPublisher eventPublisher;
	private final StatsRefresher statsRefresher;
	private final org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate jdbcTemplate;

	public ProgressService(
			CourseProgressRepository courseProgressRepository,
			LessonProgressRepository lessonProgressRepository,
			EnrollmentRepository enrollmentRepository,
			CourseStructureQueryService courseStructureQueryService,
			ApplicationEventPublisher eventPublisher,
			StatsRefresher statsRefresher,
			org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate jdbcTemplate) {
		this.courseProgressRepository = courseProgressRepository;
		this.lessonProgressRepository = lessonProgressRepository;
		this.enrollmentRepository = enrollmentRepository;
		this.courseStructureQueryService = courseStructureQueryService;
		this.eventPublisher = eventPublisher;
		this.statsRefresher = statsRefresher;
		this.jdbcTemplate = jdbcTemplate;
	}

	@Transactional
	public CourseProgressDto getOrRecomputeProgress(EnrollmentEntity enrollment) {
		Optional<CourseProgressEntity> optional = courseProgressRepository.findById(enrollment.getId());
		if (optional.isPresent() && !optional.get().isStale()) {
			CourseProgressEntity progress = optional.get();
			return toDto(progress);
		}
		return upsertAndComputeProgress(enrollment);
	}

	@Transactional
	public CourseProgressDto recomputeProgress(EnrollmentEntity enrollment) {
		return upsertAndComputeProgress(enrollment);
	}

	@Transactional(readOnly = true)
	public ContinueLessonDto getContinueLesson(EnrollmentEntity enrollment) {
		CourseStructureQueryService.CourseStructure structure =
				courseStructureQueryService.getStructure(enrollment.getCourseId());
		List<Long> orderedLessonIds = structure.modules().stream()
				.flatMap(m -> m.lessons().stream())
				.map(CourseStructureQueryService.LessonSnapshot::id)
				.toList();

		if (orderedLessonIds.isEmpty()) {
			return new ContinueLessonDto(null, ContinueReason.COMPLETED);
		}

		Set<Long> completedIds = lessonProgressRepository.findByEnrollmentIdAndLessonIdIn(
				enrollment.getId(), orderedLessonIds).stream()
				.map(LessonProgressEntity::getLessonId)
				.collect(Collectors.toSet());

		Long last = enrollment.getLastLessonId();
		return resolveContinueLesson(orderedLessonIds, completedIds, last);
	}

	@Transactional(readOnly = true)
	public java.util.Map<Long, Long> getContinueLessonIdsBulk(List<EnrollmentEntity> enrollments) {
		if (enrollments.isEmpty()) {
			return java.util.Map.of();
		}
		Set<Long> courseIds = enrollments.stream().map(EnrollmentEntity::getCourseId).collect(Collectors.toSet());
		List<Long> enrollmentIds = enrollments.stream().map(EnrollmentEntity::getId).toList();

		org.springframework.jdbc.core.namedparam.MapSqlParameterSource courseParams =
				new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("courseIds", courseIds);
		String lessonsSql = "SELECT cm.course_id, l.id AS lesson_id " +
				"FROM course_modules cm " +
				"JOIN lessons l ON l.module_id = cm.id " +
				"WHERE cm.course_id IN (:courseIds) " +
				"ORDER BY cm.course_id ASC, cm.position ASC, l.position ASC";

		java.util.Map<Long, List<Long>> orderedLessonsByCourse = new java.util.HashMap<>();
		for (Long cId : courseIds) {
			orderedLessonsByCourse.put(cId, new java.util.ArrayList<>());
		}
		jdbcTemplate.query(lessonsSql, courseParams, rs -> {
			long cId = rs.getLong("course_id");
			long lId = rs.getLong("lesson_id");
			orderedLessonsByCourse.get(cId).add(lId);
		});

		org.springframework.jdbc.core.namedparam.MapSqlParameterSource enrollParams =
				new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("enrollmentIds", enrollmentIds);
		String completedSql = "SELECT lp.enrollment_id, lp.lesson_id " +
				"FROM lesson_progress lp " +
				"WHERE lp.enrollment_id IN (:enrollmentIds)";

		java.util.Map<Long, Set<Long>> completedLessonsByEnrollment = new java.util.HashMap<>();
		for (Long eId : enrollmentIds) {
			completedLessonsByEnrollment.put(eId, new java.util.HashSet<>());
		}
		jdbcTemplate.query(completedSql, enrollParams, rs -> {
			long eId = rs.getLong("enrollment_id");
			long lId = rs.getLong("lesson_id");
			completedLessonsByEnrollment.get(eId).add(lId);
		});

		java.util.Map<Long, Long> continueLessonByEnrollment = new java.util.HashMap<>();
		for (EnrollmentEntity enrollment : enrollments) {
			List<Long> ordered = orderedLessonsByCourse.getOrDefault(enrollment.getCourseId(), List.of());
			Set<Long> completed = completedLessonsByEnrollment.getOrDefault(enrollment.getId(), Set.of());
			ContinueLessonDto dto = resolveContinueLesson(ordered, completed, enrollment.getLastLessonId());
			continueLessonByEnrollment.put(enrollment.getId(), dto.lessonId());
		}
		return continueLessonByEnrollment;
	}

	private ContinueLessonDto resolveContinueLesson(List<Long> orderedLessonIds, Set<Long> completedIds, Long last) {
		if (orderedLessonIds.isEmpty()) {
			return new ContinueLessonDto(null, ContinueReason.COMPLETED);
		}

		// Rule 1: RESUME
		if (last != null && orderedLessonIds.contains(last) && !completedIds.contains(last)) {
			return new ContinueLessonDto(last, ContinueReason.RESUME);
		}

		// Rule 2: NEXT
		if (last != null && orderedLessonIds.contains(last)) {
			int lastIdx = orderedLessonIds.indexOf(last);
			for (int i = lastIdx + 1; i < orderedLessonIds.size(); i++) {
				Long candidate = orderedLessonIds.get(i);
				if (!completedIds.contains(candidate)) {
					return new ContinueLessonDto(candidate, ContinueReason.NEXT);
				}
			}
		}

		// Rule 3: FIRST
		for (Long candidate : orderedLessonIds) {
			if (!completedIds.contains(candidate)) {
				return new ContinueLessonDto(candidate, ContinueReason.FIRST);
			}
		}

		// Rule 4: COMPLETED
		return new ContinueLessonDto(null, ContinueReason.COMPLETED);
	}

	@Transactional
	public CourseProgressDto setLessonCompletion(long courseId, long lessonId, long userId, boolean completed) {
		Optional<LessonLocation> locationOpt = courseStructureQueryService.getLessonLocation(lessonId);
		if (locationOpt.isEmpty() || locationOpt.get().courseDeleted() || !locationOpt.get().courseId().equals(courseId)) {
			throw new NotFoundException("The requested resource was not found");
		}

		CourseMetadataSnapshot metadata = courseStructureQueryService.getCourseMetadata(courseId)
				.orElseThrow(() -> new NotFoundException("The requested resource was not found"));
		if (metadata.deleted()) {
			throw new NotFoundException("The requested resource was not found");
		}

		Optional<EnrollmentEntity> enrollmentOpt =
				enrollmentRepository.findByUserIdAndCourseIdAndStatus(userId, courseId, EnrollmentStatus.ACTIVE);
		if (enrollmentOpt.isEmpty()) {
			if (metadata.status() != com.peergeek.course.CourseStatus.PUBLISHED) {
				throw new NotFoundException("The requested resource was not found");
			}
			throw new EnrollmentRequiredException();
		}

		EnrollmentEntity enrollment = enrollmentOpt.get();

		Optional<LessonProgressEntity> existingOpt =
				lessonProgressRepository.findByEnrollmentIdAndLessonId(enrollment.getId(), lessonId);

		if (completed) {
			if (existingOpt.isEmpty()) {
				lessonProgressRepository.save(new LessonProgressEntity(enrollment.getId(), lessonId, Instant.now()));
				enrollment.setLastLessonId(lessonId);
				enrollment.setLastAccessedAt(Instant.now());
				enrollmentRepository.save(enrollment);

				CourseProgressDto progress = forceRecompute(enrollment);
				publishAfterCommit(new LessonCompletedEvent(courseId, userId, lessonId, progress.completedLessons(), progress.totalLessons()));
				return progress;
			}
		} else {
			if (existingOpt.isPresent()) {
				lessonProgressRepository.deleteByEnrollmentIdAndLessonId(enrollment.getId(), lessonId);
				enrollment.setLastAccessedAt(Instant.now());
				enrollmentRepository.save(enrollment);

				CourseProgressDto progress = forceRecompute(enrollment);
				publishAfterCommit(() -> statsRefresher.refresh(courseId));
				return progress;
			}
		}

		return getOrRecomputeProgress(enrollment);
	}

	@Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
	public void markCourseProgressStale(long courseId) {
		jdbcTemplate.update(
				"UPDATE course_progress SET stale = TRUE WHERE course_id = :courseId",
				new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("courseId", courseId));
	}

	private CourseProgressDto forceRecompute(EnrollmentEntity enrollment) {
		return upsertAndComputeProgress(enrollment);
	}

	private CourseProgressDto upsertAndComputeProgress(EnrollmentEntity enrollment) {
		CourseStructureQueryService.CourseStructure structure =
				courseStructureQueryService.getStructure(enrollment.getCourseId());
		List<Long> currentLessonIds = structure.modules().stream()
				.flatMap(m -> m.lessons().stream())
				.map(CourseStructureQueryService.LessonSnapshot::id)
				.toList();
		int totalLessons = currentLessonIds.size();

		List<LessonProgressEntity> completedList = currentLessonIds.isEmpty()
				? List.of()
				: lessonProgressRepository.findByEnrollmentIdAndLessonIdIn(enrollment.getId(), currentLessonIds);
		int completedLessons = completedList.size();
		int percent = totalLessons == 0 ? 0 : (int) Math.floor((double) completedLessons * 100.0 / totalLessons);
		Instant newlyCompletedAt = (totalLessons > 0 && percent == 100) ? Instant.now() : null;

		org.springframework.jdbc.core.namedparam.MapSqlParameterSource cpParams =
				new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
						.addValue("enrollmentId", enrollment.getId())
						.addValue("courseId", enrollment.getCourseId())
						.addValue("totalLessons", totalLessons)
						.addValue("completedLessons", completedLessons)
						.addValue("percent", percent)
						.addValue("completedAt", newlyCompletedAt)
						.addValue("stale", false)
						.addValue("computedAt", Instant.now());

		String upsertSql = "INSERT INTO course_progress " +
				"(enrollment_id, course_id, total_lessons, completed_lessons, percent, completed_at, stale, computed_at, updated_at) " +
				"VALUES (:enrollmentId, :courseId, :totalLessons, :completedLessons, :percent, :completedAt, :stale, :computedAt, NOW()) " +
				"ON DUPLICATE KEY UPDATE " +
				"total_lessons = VALUES(total_lessons), " +
				"completed_lessons = VALUES(completed_lessons), " +
				"percent = VALUES(percent), " +
				"completed_at = COALESCE(course_progress.completed_at, VALUES(completed_at)), " +
				"stale = VALUES(stale), " +
				"computed_at = VALUES(computed_at), " +
				"updated_at = NOW()";

		jdbcTemplate.update(upsertSql, cpParams);

		Instant finalCompletedAt = jdbcTemplate.queryForObject(
				"SELECT completed_at FROM course_progress WHERE enrollment_id = :enrollmentId",
				new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("enrollmentId", enrollment.getId()),
				(rs, rowNum) -> {
					java.sql.Timestamp ts = rs.getTimestamp("completed_at");
					return ts != null ? ts.toInstant() : null;
				});

		return new CourseProgressDto(completedLessons, totalLessons, percent, finalCompletedAt);
	}

	private CourseProgressDto toDto(CourseProgressEntity entity) {
		return new CourseProgressDto(
				entity.getCompletedLessons(),
				entity.getTotalLessons(),
				entity.getPercent(),
				entity.getCompletedAt());
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

	private void publishAfterCommit(Runnable runnable) {
		if (TransactionSynchronizationManager.isActualTransactionActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					runnable.run();
				}
			});
		} else {
			runnable.run();
		}
	}
}
