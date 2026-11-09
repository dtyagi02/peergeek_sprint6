package com.peergeek.assignment;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import com.peergeek.assignment.dto.LearnerAnswerDto;
import com.peergeek.assignment.dto.LearnerAssignmentDetailDto;
import com.peergeek.assignment.dto.LearnerAssignmentSummaryDto;
import com.peergeek.assignment.dto.LearnerAttemptDto;
import com.peergeek.assignment.dto.LearnerAttemptSummaryDto;
import com.peergeek.assignment.dto.LearnerQuestionDto;
import com.peergeek.assignment.dto.LearnerQuestionOptionDto;
import com.peergeek.security.CurrentUser;
import com.peergeek.security.CurrentUserProvider;
import com.peergeek.common.PageResponse;
import com.peergeek.config.AssignmentSettings;
import com.peergeek.course.CourseMetadataSnapshot;
import com.peergeek.course.CourseStatus;
import com.peergeek.course.CourseStructureQueryService;
import com.peergeek.enrollment.EnrollmentQueryService;
import com.peergeek.enrollment.EnrollmentRequiredException;
import com.peergeek.exception.NotFoundException;
import com.peergeek.submission.LearnerSubmissionService;
import com.peergeek.submission.SubmissionAnswerEntity;
import com.peergeek.submission.SubmissionAnswerRepository;
import com.peergeek.submission.SubmissionEntity;
import com.peergeek.submission.SubmissionRepository;
import com.peergeek.submission.SubmissionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LearnerAssignmentService {

	private final AssignmentRepository assignmentRepository;
	private final AssignmentQuestionRepository questionRepository;
	private final SubmissionRepository submissionRepository;
	private final SubmissionAnswerRepository answerRepository;
	private final LearnerSubmissionService submissionService;
	private final CourseStructureQueryService courseStructureQueryService;
	private final EnrollmentQueryService enrollmentQueryService;
	private final CurrentUserProvider currentUserProvider;
	private final NamedParameterJdbcTemplate jdbcTemplate;
	private final AssignmentSettings settings;
	private final Clock clock;

	public LearnerAssignmentService(
			AssignmentRepository assignmentRepository,
			AssignmentQuestionRepository questionRepository,
			SubmissionRepository submissionRepository,
			SubmissionAnswerRepository answerRepository,
			LearnerSubmissionService submissionService,
			CourseStructureQueryService courseStructureQueryService,
			EnrollmentQueryService enrollmentQueryService,
			CurrentUserProvider currentUserProvider,
			NamedParameterJdbcTemplate jdbcTemplate,
			AssignmentSettings settings,
			Clock clock) {
		this.assignmentRepository = assignmentRepository;
		this.questionRepository = questionRepository;
		this.submissionRepository = submissionRepository;
		this.answerRepository = answerRepository;
		this.submissionService = submissionService;
		this.courseStructureQueryService = courseStructureQueryService;
		this.enrollmentQueryService = enrollmentQueryService;
		this.currentUserProvider = currentUserProvider;
		this.jdbcTemplate = jdbcTemplate;
		this.settings = settings;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<LearnerAssignmentSummaryDto> getAssignmentsForCourse(
			long courseId, Long lessonId, Long moduleId) {
		CurrentUser user = currentUserProvider.requireCurrentUser();
		CourseMetadataSnapshot metadata = verifyCourseVisibilityAndEnrollment(courseId, user.id());

		List<AssignmentEntity> assignments =
				assignmentRepository.findByCourseIdAndStatus(courseId, AssignmentStatus.PUBLISHED);

		if (assignments.isEmpty()) {
			return List.of();
		}

		CourseStructureQueryService.CourseStructure structure = courseStructureQueryService.getStructure(courseId);
		Set<Long> validModuleIds = structure.modules().stream()
				.map(CourseStructureQueryService.ModuleSnapshot::id)
				.collect(Collectors.toSet());
		Set<Long> validLessonIds = structure.modules().stream()
				.flatMap(m -> m.lessons().stream())
				.map(CourseStructureQueryService.LessonSnapshot::id)
				.collect(Collectors.toSet());

		List<Long> assignmentIds = assignments.stream().map(AssignmentEntity::getId).toList();
		Map<Long, SubmissionEntity> submissionMap = submissionRepository
				.findByUserIdAndAssignmentIdIn(user.id(), assignmentIds).stream()
				.collect(Collectors.toMap(SubmissionEntity::getAssignmentId, s -> s));

		List<LearnerAssignmentSummaryDto> result = new ArrayList<>();
		for (AssignmentEntity a : assignments) {
			Long resolvedModuleId = (a.getModuleId() != null && validModuleIds.contains(a.getModuleId()))
					? a.getModuleId() : null;
			Long resolvedLessonId = (a.getLessonId() != null && validLessonIds.contains(a.getLessonId()))
					? a.getLessonId() : null;

			if (lessonId != null && !Objects.equals(lessonId, resolvedLessonId)) {
				continue;
			}
			if (moduleId != null && !Objects.equals(moduleId, resolvedModuleId)) {
				continue;
			}

			SubmissionEntity sub = submissionMap.get(a.getId());
			String status = sub != null ? sub.getStatus().name() : "NOT_STARTED";
			boolean late = sub != null && sub.isLate();
			BigDecimal totalPoints = (sub != null && sub.getStatus() == SubmissionStatus.GRADED)
					? (sub.getTotalPoints() != null ? sub.getTotalPoints() : sub.getMarks())
					: null;

			result.add(new LearnerAssignmentSummaryDto(
					a.getId(),
					courseId,
					metadata.title(),
					a.getKind(),
					a.getTitle(),
					resolvedModuleId,
					resolvedLessonId,
					a.getOpensAt(),
					a.getDueAt(),
					a.getTimeLimitMinutes(),
					a.getMaxPoints(),
					status,
					late,
					totalPoints));
		}

		return result;
	}

	@Transactional(readOnly = true)
	public PageResponse<LearnerAssignmentSummaryDto> getAssignmentsCrossCourse(
			String filter, Pageable pageable) {
		CurrentUser user = currentUserProvider.requireCurrentUser();

		Page<Long> activeCourseIdsPage = enrollmentQueryService.listActiveCourseIds(user.id(), Pageable.unpaged());
		if (activeCourseIdsPage.isEmpty()) {
			return new PageResponse<>(List.of(), pageable.getPageNumber(), pageable.getPageSize(), 0, 0);
		}

		List<Long> activeCourseIds = activeCourseIdsPage.getContent();
		Map<Long, CourseMetadataSnapshot> courseMeta = courseStructureQueryService.getCourseMetadataBulk(activeCourseIds);
		List<Long> visibleCourseIds = activeCourseIds.stream()
				.filter(id -> {
					CourseMetadataSnapshot meta = courseMeta.get(id);
					return meta != null && !meta.deleted();
				})
				.toList();

		if (visibleCourseIds.isEmpty()) {
			return new PageResponse<>(List.of(), pageable.getPageNumber(), pageable.getPageSize(), 0, 0);
		}

		List<AssignmentEntity> assignments =
				assignmentRepository.findByCourseIdInAndStatus(visibleCourseIds, AssignmentStatus.PUBLISHED);

		if (assignments.isEmpty()) {
			return new PageResponse<>(List.of(), pageable.getPageNumber(), pageable.getPageSize(), 0, 0);
		}

		List<Long> assignmentIds = assignments.stream().map(AssignmentEntity::getId).toList();
		Map<Long, SubmissionEntity> submissionMap = submissionRepository
				.findByUserIdAndAssignmentIdIn(user.id(), assignmentIds).stream()
				.collect(Collectors.toMap(SubmissionEntity::getAssignmentId, s -> s));

		Instant now = clock.instant();
		List<LearnerAssignmentSummaryDto> matching = new ArrayList<>();

		for (AssignmentEntity a : assignments) {
			SubmissionEntity sub = submissionMap.get(a.getId());
			String status = sub != null ? sub.getStatus().name() : "NOT_STARTED";

			boolean matches = switch (filter != null ? filter.toLowerCase() : "all") {
				case "todo" -> (status.equals("NOT_STARTED") || status.equals("IN_PROGRESS"))
						&& (a.getDueAt() == null || now.isBefore(a.getDueAt()) || a.isAllowLate());
				case "submitted" -> status.equals("SUBMITTED");
				case "graded" -> status.equals("GRADED");
				default -> true;
			};

			if (!matches) {
				continue;
			}

			CourseMetadataSnapshot meta = courseMeta.get(a.getCourseId());
			String courseTitle = meta != null ? meta.title() : "";
			boolean late = sub != null && sub.isLate();
			BigDecimal totalPoints = (sub != null && sub.getStatus() == SubmissionStatus.GRADED)
					? (sub.getTotalPoints() != null ? sub.getTotalPoints() : sub.getMarks())
					: null;

			matching.add(new LearnerAssignmentSummaryDto(
					a.getId(),
					a.getCourseId(),
					courseTitle,
					a.getKind(),
					a.getTitle(),
					a.getModuleId(),
					a.getLessonId(),
					a.getOpensAt(),
					a.getDueAt(),
					a.getTimeLimitMinutes(),
					a.getMaxPoints(),
					status,
					late,
					totalPoints));
		}

		// Sort
		Comparator<LearnerAssignmentSummaryDto> comparator = Comparator
				.comparing(LearnerAssignmentSummaryDto::dueAt, Comparator.nullsLast(Comparator.naturalOrder()))
				.thenComparing(LearnerAssignmentSummaryDto::id);
		matching.sort(comparator);

		int start = Math.min((int) pageable.getOffset(), matching.size());
		int end = Math.min(start + pageable.getPageSize(), matching.size());
		List<LearnerAssignmentSummaryDto> pageContent = matching.subList(start, end);

		int totalPages = (int) Math.ceil((double) matching.size() / pageable.getPageSize());
		return new PageResponse<>(
				pageContent,
				pageable.getPageNumber(),
				pageable.getPageSize(),
				matching.size(),
				totalPages);
	}

	@Transactional
	public LearnerAssignmentDetailDto getAssignmentDetail(long assignmentId) {
		CurrentUser user = currentUserProvider.requireCurrentUser();
		AssignmentEntity assignment = loadPublishedAssignment(assignmentId);
		verifyCourseVisibilityAndEnrollment(assignment.getCourseId(), user.id());

		// Attachment resolution
		CourseStructureQueryService.CourseStructure structure = courseStructureQueryService.getStructure(assignment.getCourseId());
		Set<Long> validModuleIds = structure.modules().stream()
				.map(CourseStructureQueryService.ModuleSnapshot::id)
				.collect(Collectors.toSet());
		Set<Long> validLessonIds = structure.modules().stream()
				.flatMap(m -> m.lessons().stream())
				.map(CourseStructureQueryService.LessonSnapshot::id)
				.collect(Collectors.toSet());

		Long resolvedModuleId = (assignment.getModuleId() != null && validModuleIds.contains(assignment.getModuleId()))
				? assignment.getModuleId() : null;
		Long resolvedLessonId = (assignment.getLessonId() != null && validLessonIds.contains(assignment.getLessonId()))
				? assignment.getLessonId() : null;

		SubmissionEntity submission = submissionRepository.findByAssignmentIdAndUserId(assignmentId, user.id()).orElse(null);
		LearnerAttemptSummaryDto attemptSummary = null;

		if (submission != null) {
			if (submission.getStatus() == SubmissionStatus.IN_PROGRESS && submission.getDeadlineAt() != null) {
				Instant cutoff = submission.getDeadlineAt().plus(Duration.ofSeconds(settings.getGraceSeconds()));
				if (clock.instant().isAfter(cutoff)) {
					submissionService.finalizeExpiredSubmission(submission.getId());
					submission = submissionRepository.findById(submission.getId()).orElse(submission);
				}
			}

			BigDecimal totalPoints = submission.getStatus() == SubmissionStatus.GRADED
					? (submission.getTotalPoints() != null ? submission.getTotalPoints() : submission.getMarks())
					: null;

			attemptSummary = new LearnerAttemptSummaryDto(
					submission.getId(),
					submission.getStatus().name(),
					submission.getStartedAt(),
					submission.getDeadlineAt(),
					submission.getSubmittedAt(),
					submission.isLate(),
					submission.isAutoSubmitted(),
					submission.isAutoGraded(),
					totalPoints);
		}

		return new LearnerAssignmentDetailDto(
				assignment.getId(),
				assignment.getCourseId(),
				assignment.getTitle(),
				assignment.getInstructions(),
				assignment.getKind(),
				assignment.getMaxPoints(),
				assignment.getOpensAt(),
				assignment.getDueAt(),
				assignment.getTimeLimitMinutes(),
				assignment.isAllowLate(),
				assignment.isAllowText(),
				assignment.isAllowFiles(),
				assignment.isShowCorrectAnswers(),
				resolvedModuleId,
				resolvedLessonId,
				attemptSummary);
	}

	@Transactional
	public LearnerAttemptDto startAttempt(long assignmentId) {
		CurrentUser user = currentUserProvider.requireCurrentUser();
		AssignmentEntity assignment = loadPublishedAssignment(assignmentId);
		verifyCourseVisibilityAndEnrollment(assignment.getCourseId(), user.id());

		Instant now = clock.instant();

		// Check opensAt & dueAt
		if (assignment.getOpensAt() != null && now.isBefore(assignment.getOpensAt())) {
			throw AssignmentException.notOpen("Assignment is not open yet");
		}
		if (assignment.getDueAt() != null && now.isAfter(assignment.getDueAt()) && !assignment.isAllowLate()) {
			throw AssignmentException.deadlinePassed("Assignment deadline has passed");
		}

		// Check if existing attempt
		Optional<SubmissionEntity> existingOpt = submissionRepository.findByAssignmentIdAndUserId(assignmentId, user.id());
		if (existingOpt.isPresent()) {
			SubmissionEntity existing = existingOpt.get();
			if (existing.getStatus() == SubmissionStatus.IN_PROGRESS) {
				if (existing.getDeadlineAt() != null) {
					Instant cutoff = existing.getDeadlineAt().plus(Duration.ofSeconds(settings.getGraceSeconds()));
					if (now.isAfter(cutoff)) {
						submissionService.finalizeExpiredSubmission(existing.getId());
						throw AssignmentException.attemptExpired("Attempt has expired");
					}
				}
				return buildAttemptDto(existing, assignment);
			}
			throw AssignmentException.alreadySubmitted("Assignment already submitted");
		}

		// Calculate deadlineAt
		Instant startedAt = now;
		Instant deadlineAt = null;
		if (assignment.getTimeLimitMinutes() != null) {
			Instant limitDeadline = startedAt.plus(Duration.ofMinutes(assignment.getTimeLimitMinutes()));
			if (assignment.getDueAt() != null && !assignment.isAllowLate() && assignment.getDueAt().isBefore(limitDeadline)) {
				deadlineAt = assignment.getDueAt();
			} else {
				deadlineAt = limitDeadline;
			}
		}

		boolean isLate = assignment.getDueAt() != null && startedAt.isAfter(assignment.getDueAt());

		// Insert via jdbcTemplate to handle concurrent duplicate gracefully
		MapSqlParameterSource params = new MapSqlParameterSource()
				.addValue("assignmentId", assignmentId)
				.addValue("userId", user.id())
				.addValue("startedAt", Timestamp.from(startedAt))
				.addValue("deadlineAt", deadlineAt != null ? Timestamp.from(deadlineAt) : null)
				.addValue("late", isLate ? 1 : 0)
				.addValue("now", Timestamp.from(now));

		int updated = jdbcTemplate.update(
				"INSERT INTO submissions (assignment_id, user_id, status, started_at, deadline_at, late, auto_submitted, auto_graded, version, created_at, updated_at) " +
				"VALUES (:assignmentId, :userId, 'IN_PROGRESS', :startedAt, :deadlineAt, :late, 0, 0, 0, :now, :now) " +
				"ON DUPLICATE KEY UPDATE updated_at = updated_at",
				params);

		if (updated == 1) {
			jdbcTemplate.update(
					"UPDATE assignments SET attempt_count = attempt_count + 1 WHERE id = :assignmentId",
					new MapSqlParameterSource("assignmentId", assignmentId));
		}

		SubmissionEntity submission = submissionRepository.findByAssignmentIdAndUserId(assignmentId, user.id())
				.orElseThrow(() -> new IllegalStateException("Submission should exist"));

		if (submission.getStatus() != SubmissionStatus.IN_PROGRESS) {
			throw AssignmentException.alreadySubmitted("Assignment already submitted");
		}

		return buildAttemptDto(submission, assignment);
	}

	@Transactional
	public LearnerAttemptDto getAttempt(long assignmentId) {
		CurrentUser user = currentUserProvider.requireCurrentUser();
		AssignmentEntity assignment = loadPublishedAssignment(assignmentId);
		verifyCourseVisibilityAndEnrollment(assignment.getCourseId(), user.id());

		SubmissionEntity submission = submissionRepository.findByAssignmentIdAndUserId(assignmentId, user.id())
				.orElseThrow(() -> new NotFoundException("Attempt not found"));

		if (submission.getStatus() == SubmissionStatus.IN_PROGRESS && submission.getDeadlineAt() != null) {
			Instant cutoff = submission.getDeadlineAt().plus(Duration.ofSeconds(settings.getGraceSeconds()));
			if (clock.instant().isAfter(cutoff)) {
				submissionService.finalizeExpiredSubmission(submission.getId());
				submission = submissionRepository.findById(submission.getId()).orElse(submission);
			}
		}

		return buildAttemptDto(submission, assignment);
	}

	private LearnerAttemptDto buildAttemptDto(SubmissionEntity submission, AssignmentEntity assignment) {
		List<AssignmentQuestionEntity> questions =
				questionRepository.findByAssignmentIdOrderByPositionAsc(assignment.getId());

		List<LearnerQuestionDto> questionDtos = questions.stream()
				.map(q -> new LearnerQuestionDto(
						q.getId(),
						q.getPosition(),
						q.getType(),
						q.getPrompt(),
						q.getPoints(),
						q.getOptions().stream()
								.map(opt -> new LearnerQuestionOptionDto(opt.id(), opt.text()))
								.toList(),
						q.getCodeLanguage()))
				.toList();

		List<SubmissionAnswerEntity> answers = answerRepository.findBySubmissionId(submission.getId());
		List<LearnerAnswerDto> answerDtos = answers.stream()
				.map(a -> new LearnerAnswerDto(a.getQuestionId(), a.getAnswerText(), a.getSelectedOptionIds()))
				.toList();

		return new LearnerAttemptDto(
				submission.getId(),
				assignment.getId(),
				submission.getStatus().name(),
				submission.getStartedAt(),
				submission.getDeadlineAt(),
				clock.instant(),
				submission.getVersion(),
				submission.getTextAnswer(),
				questionDtos,
				answerDtos);
	}

	private AssignmentEntity loadPublishedAssignment(long assignmentId) {
		AssignmentEntity assignment = assignmentRepository.findById(assignmentId)
				.orElseThrow(() -> new NotFoundException("Assignment not found"));
		if (assignment.getStatus() != AssignmentStatus.PUBLISHED) {
			throw new NotFoundException("Assignment not found");
		}
		return assignment;
	}

	private CourseMetadataSnapshot verifyCourseVisibilityAndEnrollment(long courseId, long userId) {
		CourseMetadataSnapshot metadata = courseStructureQueryService.getCourseMetadata(courseId)
				.orElseThrow(() -> new NotFoundException("Course not found"));

		if (metadata.deleted()) {
			throw new NotFoundException("Course not found");
		}

		boolean activelyEnrolled = enrollmentQueryService.isActivelyEnrolled(userId, courseId);

		if (metadata.status() != CourseStatus.PUBLISHED) {
			if (!activelyEnrolled) {
				throw new NotFoundException("Course not found");
			}
		} else {
			if (!activelyEnrolled) {
				throw new EnrollmentRequiredException();
			}
		}

		return metadata;
	}
}
