package com.peergeek.assignment;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import com.peergeek.assignment.dto.CreateAssignmentRequest;
import com.peergeek.assignment.dto.InstructorAssignmentDetailDto;
import com.peergeek.assignment.dto.InstructorAssignmentSummaryDto;
import com.peergeek.assignment.dto.QuestionDto;
import com.peergeek.assignment.dto.QuestionUpsertRequest;
import com.peergeek.assignment.dto.UpdateAssignmentRequest;
import com.peergeek.audit.AuditEvent;
import com.peergeek.audit.AuditService;
import com.peergeek.common.PageResponse;
import com.peergeek.config.AssignmentSettings;
import com.peergeek.course.CourseMetadataSnapshot;
import com.peergeek.course.CourseStructureQueryService;
import com.peergeek.exception.FieldErrorResponse;
import com.peergeek.exception.NotFoundException;
import com.peergeek.security.CurrentUser;
import com.peergeek.security.CurrentUserProvider;
import com.peergeek.security.OwnershipGuard;
import com.peergeek.submission.SubmissionRepository;
import com.peergeek.submission.SubmissionStatus;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

@Service
public class InstructorAssignmentService {

	private final AssignmentRepository assignmentRepository;
	private final AssignmentQuestionRepository questionRepository;
	private final SubmissionRepository submissionRepository;
	private final CourseStructureQueryService courseStructureQueryService;
	private final OwnershipGuard ownershipGuard;
	private final CurrentUserProvider currentUserProvider;
	private final AssignmentSettings settings;
	private final AuditService auditService;
	private final ApplicationEventPublisher eventPublisher;
	private final Clock clock;

	public InstructorAssignmentService(
			AssignmentRepository assignmentRepository,
			AssignmentQuestionRepository questionRepository,
			SubmissionRepository submissionRepository,
			CourseStructureQueryService courseStructureQueryService,
			OwnershipGuard ownershipGuard,
			CurrentUserProvider currentUserProvider,
			AssignmentSettings settings,
			AuditService auditService,
			ApplicationEventPublisher eventPublisher,
			Clock clock) {
		this.assignmentRepository = assignmentRepository;
		this.questionRepository = questionRepository;
		this.submissionRepository = submissionRepository;
		this.courseStructureQueryService = courseStructureQueryService;
		this.ownershipGuard = ownershipGuard;
		this.currentUserProvider = currentUserProvider;
		this.settings = settings;
		this.auditService = auditService;
		this.eventPublisher = eventPublisher;
		this.clock = clock;
	}

	@Transactional
	public InstructorAssignmentDetailDto createAssignment(long courseId, CreateAssignmentRequest request) {
		CurrentUser user = currentUserProvider.requireCurrentUser();
		verifyCourseOwnership(courseId, user.id());

		if (assignmentRepository.countByCourseId(courseId) >= settings.getMaxAssignmentsPerCourse()) {
			throw new AssignmentValidationException("courseId", "Maximum assignments limit reached for this course");
		}

		if (request.kind() != AssignmentKind.QUESTIONS) {
			throw new AssignmentValidationException("kind", "Only QUESTIONS (MCQ) assessments can be created");
		}

		validateAssignmentFields(courseId, request.kind(), request.title(), request.instructions(),
				request.moduleId(), request.lessonId(), request.opensAt(), request.dueAt(),
				request.timeLimitMinutes(), request.allowText(), request.allowFiles(), request.maxPoints(), true);

		BigDecimal maxPoints = BigDecimal.ZERO;

		AssignmentEntity assignment = new AssignmentEntity(
				courseId,
				request.moduleId(),
				request.lessonId(),
				request.kind(),
				request.title().trim(),
				request.instructions().trim(),
				maxPoints,
				user.id());

		assignment.setOpensAt(request.opensAt());
		assignment.setDueAt(request.dueAt());
		assignment.setTimeLimitMinutes(request.timeLimitMinutes());
		assignment.setAllowLate(Boolean.TRUE.equals(request.allowLate()));
		assignment.setAllowText(Boolean.TRUE.equals(request.allowText()));
		assignment.setAllowFiles(Boolean.TRUE.equals(request.allowFiles()));
		assignment.setShowCorrectAnswers(Boolean.TRUE.equals(request.showCorrectAnswers()));

		AssignmentEntity saved = assignmentRepository.save(assignment);

		auditService.record(new AuditEvent(
				user.id(),
				AssignmentAuditActions.ASSIGNMENT_CREATED,
				"ASSIGNMENT",
				Long.toString(saved.getId()),
				Map.of("courseId", courseId, "assignmentId", saved.getId())));

		return mapToDetailDto(saved, List.of());
	}

	@Transactional(readOnly = true)
	public PageResponse<InstructorAssignmentSummaryDto> listAssignments(
			long courseId, AssignmentStatus statusFilter, Pageable pageable) {
		CurrentUser user = currentUserProvider.requireCurrentUser();
		verifyCourseOwnership(courseId, user.id());

		Page<AssignmentEntity> page = statusFilter != null
				? assignmentRepository.findByCourseIdAndStatus(courseId, statusFilter, pageable)
				: assignmentRepository.findByCourseId(courseId, pageable);

		List<Long> assignmentIds = page.getContent().stream()
				.map(AssignmentEntity::getId)
				.toList();

		Map<Long, Map<SubmissionStatus, Integer>> countsByAssignment = new HashMap<>();
		if (!assignmentIds.isEmpty()) {
			List<SubmissionRepository.SubmissionCountProjection> countList =
					submissionRepository.countByAssignmentIdInGroupByStatus(assignmentIds);
			for (var item : countList) {
				countsByAssignment
						.computeIfAbsent(item.getAssignmentId(), k -> new HashMap<>())
						.put(item.getStatus(), item.getCount());
			}
		}

		List<InstructorAssignmentSummaryDto> content = page.getContent().stream()
				.map(a -> {
					Map<SubmissionStatus, Integer> counts = countsByAssignment.getOrDefault(a.getId(), Map.of());
					int submitted = counts.getOrDefault(SubmissionStatus.SUBMITTED, 0);
					int graded = counts.getOrDefault(SubmissionStatus.GRADED, 0);
					int inProgress = counts.getOrDefault(SubmissionStatus.IN_PROGRESS, 0);
					return new InstructorAssignmentSummaryDto(
							a.getId(),
							a.getCourseId(),
							a.getModuleId(),
							a.getLessonId(),
							a.getKind(),
							a.getTitle(),
							a.getInstructions(),
							a.getStatus(),
							a.getMaxPoints(),
							a.getOpensAt(),
							a.getDueAt(),
							a.getTimeLimitMinutes(),
							a.isAllowLate(),
							a.isAllowText(),
							a.isAllowFiles(),
							a.isShowCorrectAnswers(),
							a.getAttemptCount(),
							a.getPublishedAt(),
							a.getVersion(),
							new InstructorAssignmentSummaryDto.SubmissionCounts(submitted, graded, inProgress));
				})
				.toList();

		return new PageResponse<>(content, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
	}

	@Transactional(readOnly = true)
	public InstructorAssignmentDetailDto getAssignment(long assignmentId) {
		CurrentUser user = currentUserProvider.requireCurrentUser();
		AssignmentEntity assignment = loadAssignment(assignmentId);
		verifyCourseOwnership(assignment.getCourseId(), user.id());

		List<AssignmentQuestionEntity> questions =
				questionRepository.findByAssignmentIdOrderByPositionAsc(assignmentId);
		return mapToDetailDto(assignment, questions);
	}

	@Transactional
	public InstructorAssignmentDetailDto updateAssignment(long assignmentId, UpdateAssignmentRequest request) {
		CurrentUser user = currentUserProvider.requireCurrentUser();
		AssignmentEntity assignment = loadAssignment(assignmentId);
		verifyCourseOwnership(assignment.getCourseId(), user.id());

		if (request.version() == null || assignment.getVersion() != request.version()) {
			throw AssignmentException.draftVersionConflict("Assignment version conflict; please reload the latest assignment");
		}

		boolean hasAttempts = assignment.getAttemptCount() > 0;
		if (hasAttempts && request.maxPoints() != null
				&& assignment.getKind() == AssignmentKind.PROJECT
				&& request.maxPoints().compareTo(assignment.getMaxPoints()) != 0) {
			throw AssignmentException.locked("Cannot modify points once learner attempts exist");
		}

		List<String> changedFields = new ArrayList<>();

		if (StringUtils.hasText(request.title()) && !request.title().equals(assignment.getTitle())) {
			if (request.title().trim().length() > settings.getMaxTitleLength()) {
				throw new AssignmentValidationException("title", "Title must not exceed " + settings.getMaxTitleLength() + " characters");
			}
			assignment.setTitle(request.title().trim());
			changedFields.add("title");
		}

		if (request.instructions() != null && !request.instructions().equals(assignment.getInstructions())) {
			if (request.instructions().trim().length() > settings.getMaxInstructionsLength()) {
				throw new AssignmentValidationException("instructions", "Instructions must not exceed " + settings.getMaxInstructionsLength() + " characters");
			}
			assignment.setInstructions(request.instructions().trim());
			changedFields.add("instructions");
		}

		if (request.moduleId() != null || request.lessonId() != null) {
			validateModuleAndLesson(assignment.getCourseId(), request.moduleId(), request.lessonId());
		}
		if (!Objects.equals(request.moduleId(), assignment.getModuleId())) {
			assignment.setModuleId(request.moduleId());
			changedFields.add("moduleId");
		}
		if (!Objects.equals(request.lessonId(), assignment.getLessonId())) {
			assignment.setLessonId(request.lessonId());
			changedFields.add("lessonId");
		}

		Instant opensAt = request.opensAt() != null ? request.opensAt() : assignment.getOpensAt();
		Instant dueAt = request.dueAt() != null ? request.dueAt() : assignment.getDueAt();
		if (opensAt != null && dueAt != null && !opensAt.isBefore(dueAt)) {
			throw new AssignmentValidationException("opensAt", "opensAt must be before dueAt");
		}

		if (request.opensAt() != null && !Objects.equals(request.opensAt(), assignment.getOpensAt())) {
			assignment.setOpensAt(request.opensAt());
			changedFields.add("opensAt");
		}
		if (request.dueAt() != null && !Objects.equals(request.dueAt(), assignment.getDueAt())) {
			assignment.setDueAt(request.dueAt());
			changedFields.add("dueAt");
		}

		if (request.timeLimitMinutes() != null) {
			if (request.timeLimitMinutes() < 1 || request.timeLimitMinutes() > 480) {
				throw new AssignmentValidationException("timeLimitMinutes", "timeLimitMinutes must be between 1 and 480");
			}
			if (!Objects.equals(request.timeLimitMinutes(), assignment.getTimeLimitMinutes())) {
				assignment.setTimeLimitMinutes(request.timeLimitMinutes());
				changedFields.add("timeLimitMinutes");
			}
		}

		if (request.allowLate() != null && request.allowLate() != assignment.isAllowLate()) {
			assignment.setAllowLate(request.allowLate());
			changedFields.add("allowLate");
		}

		if (request.allowText() != null && request.allowText() != assignment.isAllowText()) {
			assignment.setAllowText(request.allowText());
			changedFields.add("allowText");
		}

		if (request.allowFiles() != null && request.allowFiles() != assignment.isAllowFiles()) {
			assignment.setAllowFiles(request.allowFiles());
			changedFields.add("allowFiles");
		}

		if (request.showCorrectAnswers() != null && request.showCorrectAnswers() != assignment.isShowCorrectAnswers()) {
			assignment.setShowCorrectAnswers(request.showCorrectAnswers());
			changedFields.add("showCorrectAnswers");
		}

		if (!hasAttempts && request.maxPoints() != null && assignment.getKind() == AssignmentKind.PROJECT) {
			if (request.maxPoints().compareTo(BigDecimal.ZERO) <= 0) {
				throw new AssignmentValidationException("maxPoints", "maxPoints must be greater than zero");
			}
			if (!request.maxPoints().equals(assignment.getMaxPoints())) {
				assignment.setMaxPoints(request.maxPoints());
				changedFields.add("maxPoints");
			}
		}

		AssignmentEntity saved = assignmentRepository.save(assignment);

		if (!changedFields.isEmpty()) {
			auditService.record(new AuditEvent(
					user.id(),
					AssignmentAuditActions.ASSIGNMENT_UPDATED,
					"ASSIGNMENT",
					Long.toString(assignmentId),
					Map.of("assignmentId", assignmentId, "changedFields", changedFields)));
		}

		List<AssignmentQuestionEntity> questions =
				questionRepository.findByAssignmentIdOrderByPositionAsc(assignmentId);
		return mapToDetailDto(saved, questions);
	}

	@Transactional
	public InstructorAssignmentDetailDto replaceQuestions(
			long assignmentId, List<QuestionUpsertRequest> questionRequests) {
		CurrentUser user = currentUserProvider.requireCurrentUser();
		AssignmentEntity assignment = loadAssignment(assignmentId);
		verifyCourseOwnership(assignment.getCourseId(), user.id());

		if (assignment.getAttemptCount() > 0) {
			throw AssignmentException.locked("Cannot modify questions because attempts already exist");
		}

		if (questionRequests == null) {
			questionRequests = List.of();
		}
		if (questionRequests.size() > 100) {
			throw new AssignmentValidationException("questions", "Maximum 100 questions permitted per assignment");
		}

		questionRepository.deleteByAssignmentId(assignmentId);

		BigDecimal totalPoints = BigDecimal.ZERO;
		List<AssignmentQuestionEntity> newQuestions = new ArrayList<>();
		int position = 1;

		for (QuestionUpsertRequest q : questionRequests) {
			if (!StringUtils.hasText(q.prompt())) {
				throw new AssignmentValidationException("questions[" + (position - 1) + "].prompt", "Prompt is required");
			}
			if (q.prompt().trim().length() > settings.getMaxQuestionPromptLength()) {
				throw new AssignmentValidationException("questions[" + (position - 1) + "].prompt",
						"Prompt must not exceed " + settings.getMaxQuestionPromptLength() + " characters");
			}
			if (q.points() == null || q.points().compareTo(BigDecimal.ZERO) <= 0) {
				throw new AssignmentValidationException("questions[" + (position - 1) + "].points", "Points must be greater than zero");
			}

			if (q.type() != QuestionType.MCQ_SINGLE && q.type() != QuestionType.MCQ_MULTI) {
				throw new AssignmentValidationException("questions[" + (position - 1) + "].type",
						"Only MCQ_SINGLE and MCQ_MULTI question types are supported");
			}

			List<QuestionOption> sanitizedOptions = new ArrayList<>();
			if (q.type() == QuestionType.MCQ_SINGLE || q.type() == QuestionType.MCQ_MULTI) {
				if (q.options() == null || q.options().size() < 2 || q.options().size() > 10) {
					throw new AssignmentValidationException("questions[" + (position - 1) + "].options",
							"MCQ questions must have between 2 and 10 options");
				}
				for (int i = 0; i < q.options().size(); i++) {
					QuestionOption opt = q.options().get(i);
					if (!StringUtils.hasText(opt.text())) {
						throw new AssignmentValidationException("questions[" + (position - 1) + "].options[" + i + "].text",
								"Option text is required");
					}
					if (opt.text().trim().length() > settings.getMaxOptionTextLength()) {
						throw new AssignmentValidationException("questions[" + (position - 1) + "].options[" + i + "].text",
								"Option text must not exceed " + settings.getMaxOptionTextLength() + " characters");
					}
					String optId = StringUtils.hasText(opt.id()) ? opt.id() : UUID.randomUUID().toString();
					sanitizedOptions.add(new QuestionOption(optId, opt.text().trim(), opt.correct()));
				}
			}

			AssignmentQuestionEntity entity = new AssignmentQuestionEntity(
					assignment,
					position++,
					q.type(),
					q.prompt().trim(),
					q.points(),
					sanitizedOptions,
					q.codeLanguage());

			if (q.id() != null) {
				entity.setId(q.id());
			}

			totalPoints = totalPoints.add(q.points());
			newQuestions.add(entity);
		}

		List<AssignmentQuestionEntity> savedQuestions = questionRepository.saveAll(newQuestions);

		if (assignment.getKind() == AssignmentKind.QUESTIONS) {
			assignment.setMaxPoints(totalPoints);
			assignmentRepository.save(assignment);
		}

		return mapToDetailDto(assignment, savedQuestions);
	}

	@Transactional
	public InstructorAssignmentDetailDto publishAssignment(long assignmentId) {
		CurrentUser user = currentUserProvider.requireCurrentUser();
		AssignmentEntity assignment = loadAssignment(assignmentId);
		verifyCourseOwnership(assignment.getCourseId(), user.id());

		CourseMetadataSnapshot metadata = courseStructureQueryService.getCourseMetadata(assignment.getCourseId())
				.orElseThrow(() -> new NotFoundException("Course not found"));
		if (metadata.deleted()) {
			throw new NotFoundException("Cannot publish assignment for a deleted course");
		}

		List<AssignmentQuestionEntity> questions =
				questionRepository.findByAssignmentIdOrderByPositionAsc(assignmentId);

		List<FieldErrorResponse> issues = new ArrayList<>();

		if (!StringUtils.hasText(assignment.getTitle())) {
			issues.add(new FieldErrorResponse("title", "Title is required to publish"));
		}

		if (assignment.getOpensAt() != null && assignment.getDueAt() != null
				&& !assignment.getOpensAt().isBefore(assignment.getDueAt())) {
			issues.add(new FieldErrorResponse("opensAt", "opensAt must be before dueAt"));
		}

		if (assignment.getKind() == AssignmentKind.QUESTIONS) {
			if (questions.isEmpty()) {
				issues.add(new FieldErrorResponse("questions", "At least one question is required to publish"));
			} else if (questions.size() > 100) {
				issues.add(new FieldErrorResponse("questions", "Maximum 100 questions permitted"));
			}
			for (int idx = 0; idx < questions.size(); idx++) {
				AssignmentQuestionEntity q = questions.get(idx);
				if (q.getPoints() == null || q.getPoints().compareTo(BigDecimal.ZERO) <= 0) {
					issues.add(new FieldErrorResponse("questions[" + idx + "].points", "Question points must be greater than zero"));
				}
				if (q.getType() != QuestionType.MCQ_SINGLE && q.getType() != QuestionType.MCQ_MULTI) {
					issues.add(new FieldErrorResponse("questions[" + idx + "].type", "Only MCQ_SINGLE and MCQ_MULTI question types are supported"));
				} else {
					List<QuestionOption> opts = q.getOptions();
					if (opts == null || opts.size() < 2 || opts.size() > 10) {
						issues.add(new FieldErrorResponse("questions[" + idx + "].options", "MCQ questions must have between 2 and 10 options"));
					} else {
						long correctCount = opts.stream().filter(QuestionOption::correct).count();
						if (correctCount == 0) {
							issues.add(new FieldErrorResponse("questions[" + idx + "].options", "At least one option must be marked correct"));
						}
						if (q.getType() == QuestionType.MCQ_SINGLE && correctCount != 1) {
							issues.add(new FieldErrorResponse("questions[" + idx + "].options", "MCQ_SINGLE must have exactly one correct option"));
						}
					}
				}
			}
		} else if (assignment.getKind() == AssignmentKind.PROJECT) {
			issues.add(new FieldErrorResponse("kind", "PROJECT assignments cannot be published; only MCQ assessments are supported"));
		}

		if (assignment.getMaxPoints() == null || assignment.getMaxPoints().compareTo(BigDecimal.ZERO) <= 0) {
			issues.add(new FieldErrorResponse("maxPoints", "maxPoints must be greater than zero"));
		}

		if (!issues.isEmpty()) {
			throw new AssignmentValidationException(issues);
		}

		assignment.setStatus(AssignmentStatus.PUBLISHED);
		assignment.setPublishedAt(clock.instant());
		AssignmentEntity saved = assignmentRepository.save(assignment);

		publishEventAfterCommit(new AssignmentPublishedEvent(
				saved.getCourseId(), saved.getId(), saved.getTitle()));

		auditService.record(new AuditEvent(
				user.id(),
				AssignmentAuditActions.ASSIGNMENT_PUBLISHED,
				"ASSIGNMENT",
				Long.toString(assignmentId),
				Map.of("courseId", saved.getCourseId(), "assignmentId", assignmentId)));

		return mapToDetailDto(saved, questions);
	}

	@Transactional
	public InstructorAssignmentDetailDto unpublishAssignment(long assignmentId) {
		CurrentUser user = currentUserProvider.requireCurrentUser();
		AssignmentEntity assignment = loadAssignment(assignmentId);
		verifyCourseOwnership(assignment.getCourseId(), user.id());

		assignment.setStatus(AssignmentStatus.UNPUBLISHED);
		AssignmentEntity saved = assignmentRepository.save(assignment);

		auditService.record(new AuditEvent(
				user.id(),
				AssignmentAuditActions.ASSIGNMENT_UNPUBLISHED,
				"ASSIGNMENT",
				Long.toString(assignmentId),
				Map.of("courseId", saved.getCourseId(), "assignmentId", assignmentId)));

		List<AssignmentQuestionEntity> questions =
				questionRepository.findByAssignmentIdOrderByPositionAsc(assignmentId);
		return mapToDetailDto(saved, questions);
	}

	@Transactional
	public void deleteAssignment(long assignmentId) {
		CurrentUser user = currentUserProvider.requireCurrentUser();
		AssignmentEntity assignment = loadAssignment(assignmentId);
		verifyCourseOwnership(assignment.getCourseId(), user.id());

		if (assignment.getAttemptCount() > 0) {
			throw AssignmentException.locked("Cannot delete assignment because attempts exist. Unpublish it instead.");
		}

		Long courseId = assignment.getCourseId();
		assignmentRepository.delete(assignment);

		auditService.record(new AuditEvent(
				user.id(),
				AssignmentAuditActions.ASSIGNMENT_DELETED,
				"ASSIGNMENT",
				Long.toString(assignmentId),
				Map.of("courseId", courseId, "assignmentId", assignmentId)));
	}

	private void verifyCourseOwnership(long courseId, long currentUserId) {
		Long ownerId = courseStructureQueryService.getCourseOwnerId(courseId);
		if (ownerId == null) {
			throw new NotFoundException("Course not found");
		}
		ownershipGuard.requireOwnerOrNotFound(ownerId, currentUserId);
	}

	private AssignmentEntity loadAssignment(long assignmentId) {
		return assignmentRepository.findById(assignmentId)
				.orElseThrow(() -> new NotFoundException("Assignment not found"));
	}

	private void validateAssignmentFields(
			long courseId,
			AssignmentKind kind,
			String title,
			String instructions,
			Long moduleId,
			Long lessonId,
			Instant opensAt,
			Instant dueAt,
			Integer timeLimitMinutes,
			Boolean allowText,
			Boolean allowFiles,
			BigDecimal maxPoints,
			boolean isCreate) {
		List<FieldErrorResponse> errors = new ArrayList<>();

		if (!StringUtils.hasText(title)) {
			errors.add(new FieldErrorResponse("title", "Title is required"));
		} else if (title.trim().length() > settings.getMaxTitleLength()) {
			errors.add(new FieldErrorResponse("title", "Title must not exceed " + settings.getMaxTitleLength() + " characters"));
		}

		if (!StringUtils.hasText(instructions)) {
			errors.add(new FieldErrorResponse("instructions", "Instructions are required"));
		} else if (instructions.trim().length() > settings.getMaxInstructionsLength()) {
			errors.add(new FieldErrorResponse("instructions", "Instructions must not exceed " + settings.getMaxInstructionsLength() + " characters"));
		}

		if (moduleId != null && lessonId != null) {
			errors.add(new FieldErrorResponse("moduleId", "Cannot specify both moduleId and lessonId"));
			errors.add(new FieldErrorResponse("lessonId", "Cannot specify both moduleId and lessonId"));
		} else {
			if (moduleId != null) {
				try {
					var structure = courseStructureQueryService.getStructure(courseId);
					boolean exists = structure != null && structure.modules().stream().anyMatch(m -> m.id().equals(moduleId));
					if (!exists) {
						errors.add(new FieldErrorResponse("moduleId", "Module does not belong to this course"));
					}
				} catch (Exception e) {
					errors.add(new FieldErrorResponse("moduleId", "Module does not belong to this course"));
				}
			}
			if (lessonId != null) {
				var location = courseStructureQueryService.getLessonLocation(lessonId);
				if (location.isEmpty() || !location.get().courseId().equals(courseId)) {
					errors.add(new FieldErrorResponse("lessonId", "Lesson does not belong to this course"));
				}
			}
		}

		if (opensAt != null && dueAt != null && !opensAt.isBefore(dueAt)) {
			errors.add(new FieldErrorResponse("opensAt", "opensAt must be before dueAt"));
		}

		if (timeLimitMinutes != null && (timeLimitMinutes < 1 || timeLimitMinutes > 480)) {
			errors.add(new FieldErrorResponse("timeLimitMinutes", "timeLimitMinutes must be between 1 and 480"));
		}

		if (isCreate && kind != AssignmentKind.QUESTIONS) {
			errors.add(new FieldErrorResponse("kind", "Only QUESTIONS (MCQ) assessments can be created"));
		}

		if (!errors.isEmpty()) {
			throw new AssignmentValidationException(errors);
		}
	}

	private void validateModuleAndLesson(long courseId, Long moduleId, Long lessonId) {
		if (moduleId != null && lessonId != null) {
			throw new AssignmentValidationException(List.of(
					new FieldErrorResponse("moduleId", "Cannot specify both moduleId and lessonId"),
					new FieldErrorResponse("lessonId", "Cannot specify both moduleId and lessonId")));
		}
		if (moduleId != null) {
			var structure = courseStructureQueryService.getStructure(courseId);
			boolean exists = structure != null && structure.modules().stream().anyMatch(m -> m.id().equals(moduleId));
			if (!exists) {
				throw new AssignmentValidationException("moduleId", "Module does not belong to this course");
			}
		}
		if (lessonId != null) {
			var location = courseStructureQueryService.getLessonLocation(lessonId);
			if (location.isEmpty() || !location.get().courseId().equals(courseId)) {
				throw new AssignmentValidationException("lessonId", "Lesson does not belong to this course");
			}
		}
	}

	private void publishEventAfterCommit(Object event) {
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

	private InstructorAssignmentDetailDto mapToDetailDto(
			AssignmentEntity a, List<AssignmentQuestionEntity> questions) {
		List<QuestionDto> questionDtos = questions.stream()
				.map(q -> new QuestionDto(
						q.getId(),
						q.getPosition(),
						q.getType(),
						q.getPrompt(),
						q.getPoints(),
						q.getOptions(),
						q.getCodeLanguage()))
				.toList();

		return new InstructorAssignmentDetailDto(
				a.getId(),
				a.getCourseId(),
				a.getModuleId(),
				a.getLessonId(),
				a.getKind(),
				a.getTitle(),
				a.getInstructions(),
				a.getStatus(),
				a.getMaxPoints(),
				a.getOpensAt(),
				a.getDueAt(),
				a.getTimeLimitMinutes(),
				a.isAllowLate(),
				a.isAllowText(),
				a.isAllowFiles(),
				a.isShowCorrectAnswers(),
				a.getAttemptCount(),
				a.getPublishedAt(),
				a.getVersion(),
				questionDtos);
	}
}
