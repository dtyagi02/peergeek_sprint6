package com.peergeek.submission;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import com.peergeek.assignment.AssignmentAuditActions;
import com.peergeek.assignment.AssignmentEntity;
import com.peergeek.assignment.AssignmentException;
import com.peergeek.assignment.AssignmentKind;
import com.peergeek.assignment.AssignmentQuestionEntity;
import com.peergeek.assignment.AssignmentQuestionRepository;
import com.peergeek.assignment.AssignmentRepository;
import com.peergeek.assignment.QuestionType;
import com.peergeek.audit.AuditEvent;
import com.peergeek.audit.AuditService;
import com.peergeek.common.PageResponse;
import com.peergeek.config.AssignmentSettings;
import com.peergeek.course.CourseMetadataSnapshot;
import com.peergeek.course.CourseStructureQueryService;
import com.peergeek.exception.BadRequestException;
import com.peergeek.exception.NotFoundException;
import com.peergeek.submission.dto.GradeSubmissionRequest;
import com.peergeek.submission.dto.InstructorSubmissionDetailDto;
import com.peergeek.submission.dto.InstructorSubmissionSummaryDto;
import com.peergeek.user.PublicProfileDto;
import com.peergeek.user.UserService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class InstructorGradingService {

	private final SubmissionRepository submissionRepository;
	private final SubmissionAnswerRepository answerRepository;
	private final SubmissionFileRepository fileRepository;
	private final AssignmentRepository assignmentRepository;
	private final AssignmentQuestionRepository questionRepository;
	private final CourseStructureQueryService courseStructureQueryService;
	private final UserService userService;
	private final AuditService auditService;
	private final ApplicationEventPublisher eventPublisher;
	private final AssignmentSettings settings;
	private final Clock clock;

	public InstructorGradingService(
			SubmissionRepository submissionRepository,
			SubmissionAnswerRepository answerRepository,
			SubmissionFileRepository fileRepository,
			AssignmentRepository assignmentRepository,
			AssignmentQuestionRepository questionRepository,
			CourseStructureQueryService courseStructureQueryService,
			UserService userService,
			AuditService auditService,
			ApplicationEventPublisher eventPublisher,
			AssignmentSettings settings,
			Clock clock) {
		this.submissionRepository = submissionRepository;
		this.answerRepository = answerRepository;
		this.fileRepository = fileRepository;
		this.assignmentRepository = assignmentRepository;
		this.questionRepository = questionRepository;
		this.courseStructureQueryService = courseStructureQueryService;
		this.userService = userService;
		this.auditService = auditService;
		this.eventPublisher = eventPublisher;
		this.settings = settings;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public PageResponse<InstructorSubmissionSummaryDto> listSubmissions(
			long courseId, Long assignmentId, SubmissionStatus status, int page, int size, String sortParam, long instructorUserId) {
		verifyCourseOwnership(courseId, instructorUserId);

		SubmissionStatus queryStatus = status != null ? status : SubmissionStatus.SUBMITTED;

		List<AssignmentEntity> courseAssignments = assignmentRepository.findByCourseId(courseId);
		if (courseAssignments.isEmpty()) {
			return new PageResponse<>(List.of(), page, size, 0, 0);
		}

		Map<Long, AssignmentEntity> assignmentMap = courseAssignments.stream()
				.collect(Collectors.toMap(AssignmentEntity::getId, a -> a));

		List<Long> targetAssignmentIds = assignmentId != null
				? List.of(assignmentId)
				: new ArrayList<>(assignmentMap.keySet());

		if (assignmentId != null && !assignmentMap.containsKey(assignmentId)) {
			throw new NotFoundException("Assignment not found in this course");
		}

		// Parse sort: whitelist submittedAt, learnerName
		String sortField = "submittedAt";
		Sort.Direction sortDirection = Sort.Direction.ASC;
		if (sortParam != null && !sortParam.isBlank()) {
			String[] parts = sortParam.split(",", -1);
			if (parts.length > 0 && !parts[0].isBlank()) {
				String field = parts[0].trim();
				if (!field.equals("submittedAt") && !field.equals("learnerName")) {
					throw new BadRequestException("Sort field must be one of: submittedAt, learnerName");
				}
				sortField = field;
			}
			if (parts.length > 1 && parts[1].trim().equalsIgnoreCase("desc")) {
				sortDirection = Sort.Direction.DESC;
			}
		}

		if (sortField.equals("learnerName")) {
			// Query all matching and sort in memory
			List<SubmissionEntity> allSubs = assignmentId != null
					? submissionRepository.findByAssignmentIdAndStatus(assignmentId, queryStatus, Pageable.unpaged()).getContent()
					: submissionRepository.findByAssignmentIdInAndStatus(targetAssignmentIds, queryStatus, Pageable.unpaged()).getContent();

			Set<Long> userIds = allSubs.stream().map(SubmissionEntity::getUserId).collect(Collectors.toSet());
			Map<Long, PublicProfileDto> profiles = userService.getPublicProfiles(userIds);

			List<InstructorSubmissionSummaryDto> dtos = allSubs.stream()
					.map(s -> {
						AssignmentEntity a = assignmentMap.get(s.getAssignmentId());
						String aTitle = a != null ? a.getTitle() : "";
						BigDecimal maxPts = a != null ? a.getMaxPoints() : null;
						PublicProfileDto profile = profiles.get(s.getUserId());
						String displayName = profile != null ? profile.displayName() : "Learner #" + s.getUserId();
						BigDecimal totalPts = s.getStatus() == SubmissionStatus.GRADED
								? (s.getTotalPoints() != null ? s.getTotalPoints() : s.getMarks())
								: null;

						return new InstructorSubmissionSummaryDto(
								s.getId(),
								s.getAssignmentId(),
								aTitle,
								new InstructorSubmissionSummaryDto.LearnerSummary(s.getUserId(), displayName),
								s.getStatus().name(),
								s.getSubmittedAt(),
								s.isLate(),
								s.isAutoSubmitted(),
								s.isAutoGraded(),
								totalPts,
								maxPts);
					})
					.collect(Collectors.toCollection(ArrayList::new));

			Comparator<InstructorSubmissionSummaryDto> comp = Comparator.comparing(
					dto -> dto.learner().displayName().toLowerCase(),
					Comparator.nullsLast(Comparator.naturalOrder()));
			if (sortDirection == Sort.Direction.DESC) {
				comp = comp.reversed();
			}
			comp = comp.thenComparing(InstructorSubmissionSummaryDto::submissionId);
			dtos.sort(comp);

			int start = Math.min(page * size, dtos.size());
			int end = Math.min(start + size, dtos.size());
			List<InstructorSubmissionSummaryDto> pageList = dtos.subList(start, end);
			int totalPages = (int) Math.ceil((double) dtos.size() / size);

			return new PageResponse<>(pageList, page, size, dtos.size(), totalPages);
		} else {
			Sort pageSort = Sort.by(sortDirection, "submittedAt").and(Sort.by(Sort.Direction.ASC, "id"));
			Pageable pageable = PageRequest.of(page, size, pageSort);

			Page<SubmissionEntity> submissionPage = assignmentId != null
					? submissionRepository.findByAssignmentIdAndStatus(assignmentId, queryStatus, pageable)
					: submissionRepository.findByAssignmentIdInAndStatus(targetAssignmentIds, queryStatus, pageable);

			Set<Long> userIds = submissionPage.getContent().stream()
					.map(SubmissionEntity::getUserId)
					.collect(Collectors.toSet());
			Map<Long, PublicProfileDto> profiles = userService.getPublicProfiles(userIds);

			List<InstructorSubmissionSummaryDto> dtos = submissionPage.getContent().stream()
					.map(s -> {
						AssignmentEntity a = assignmentMap.get(s.getAssignmentId());
						String aTitle = a != null ? a.getTitle() : "";
						BigDecimal maxPts = a != null ? a.getMaxPoints() : null;
						PublicProfileDto profile = profiles.get(s.getUserId());
						String displayName = profile != null ? profile.displayName() : "Learner #" + s.getUserId();
						BigDecimal totalPts = s.getStatus() == SubmissionStatus.GRADED
								? (s.getTotalPoints() != null ? s.getTotalPoints() : s.getMarks())
								: null;

						return new InstructorSubmissionSummaryDto(
								s.getId(),
								s.getAssignmentId(),
								aTitle,
								new InstructorSubmissionSummaryDto.LearnerSummary(s.getUserId(), displayName),
								s.getStatus().name(),
								s.getSubmittedAt(),
								s.isLate(),
								s.isAutoSubmitted(),
								s.isAutoGraded(),
								totalPts,
								maxPts);
					})
					.toList();

			return new PageResponse<>(
					dtos,
					submissionPage.getNumber(),
					submissionPage.getSize(),
					submissionPage.getTotalElements(),
					submissionPage.getTotalPages());
		}
	}

	@Transactional(readOnly = true)
	public InstructorSubmissionDetailDto getSubmission(long submissionId, long instructorUserId) {
		SubmissionEntity submission = submissionRepository.findById(submissionId)
				.orElseThrow(() -> new NotFoundException("Submission not found"));

		AssignmentEntity assignment = assignmentRepository.findById(submission.getAssignmentId())
				.orElseThrow(() -> new NotFoundException("Assignment not found"));

		verifyCourseOwnership(assignment.getCourseId(), instructorUserId);

		return buildDetailDto(submission, assignment);
	}

	@Transactional
	public InstructorSubmissionDetailDto gradeSubmission(
			long submissionId, GradeSubmissionRequest request, long instructorUserId) {
		SubmissionEntity submission = submissionRepository.findById(submissionId)
				.orElseThrow(() -> new NotFoundException("Submission not found"));

		AssignmentEntity assignment = assignmentRepository.findById(submission.getAssignmentId())
				.orElseThrow(() -> new NotFoundException("Assignment not found"));

		verifyCourseOwnership(assignment.getCourseId(), instructorUserId);

		if (submission.getStatus() == SubmissionStatus.IN_PROGRESS) {
			throw AssignmentException.locked("Cannot grade an in-progress submission");
		}

		if (submission.getVersion() != request.version()) {
			throw AssignmentException.locked("Submission was modified concurrently");
		}

		if (request.overallFeedback() != null && request.overallFeedback().length() > settings.getMaxFeedbackLength()) {
			throw new BadRequestException("Overall feedback exceeds limit of " + settings.getMaxFeedbackLength());
		}

		boolean regrade = submission.getStatus() == SubmissionStatus.GRADED;
		BigDecimal finalTotal;

		if (assignment.getKind() == AssignmentKind.PROJECT) {
			if (request.marks() == null) {
				throw new BadRequestException("Marks are required for project grading");
			}
			if (request.marks().compareTo(BigDecimal.ZERO) < 0 || request.marks().compareTo(assignment.getMaxPoints()) > 0) {
				throw new BadRequestException("Marks must be between 0 and " + assignment.getMaxPoints());
			}
			BigDecimal scaledMarks = request.marks().setScale(2, RoundingMode.HALF_UP);
			submission.setMarks(scaledMarks);
			submission.setTotalPoints(scaledMarks);
			finalTotal = scaledMarks;
		} else {
			List<AssignmentQuestionEntity> questions =
					questionRepository.findByAssignmentIdOrderByPositionAsc(assignment.getId());

			Map<Long, GradeSubmissionRequest.QuestionGradeItem> gradeMap = request.questionGrades().stream()
					.collect(Collectors.toMap(GradeSubmissionRequest.QuestionGradeItem::questionId, g -> g));

			// Completeness check
			for (AssignmentQuestionEntity q : questions) {
				if (q.getType() == QuestionType.DESCRIPTIVE || q.getType() == QuestionType.CODE) {
					GradeSubmissionRequest.QuestionGradeItem item = gradeMap.get(q.getId());
					if (item == null || item.points() == null) {
						throw AssignmentException.gradingIncomplete("Missing grade points for question id: " + q.getId());
					}
				}
			}

			// Bounds check
			for (GradeSubmissionRequest.QuestionGradeItem item : request.questionGrades()) {
				AssignmentQuestionEntity q = questions.stream()
						.filter(question -> question.getId().equals(item.questionId()))
						.findFirst()
						.orElseThrow(() -> new BadRequestException("Unknown question id in grades: " + item.questionId()));

				if (item.points() != null) {
					if (item.points().compareTo(BigDecimal.ZERO) < 0 || item.points().compareTo(q.getPoints()) > 0) {
						throw new BadRequestException("Points for question " + q.getId() + " must be between 0 and " + q.getPoints());
					}
				}

				if (item.feedback() != null && item.feedback().length() > settings.getMaxFeedbackLength()) {
					throw new BadRequestException("Feedback for question " + q.getId() + " exceeds limit");
				}
			}

			// Apply grades
			Map<Long, SubmissionAnswerEntity> answerMap = answerRepository.findBySubmissionId(submission.getId()).stream()
					.collect(Collectors.toMap(SubmissionAnswerEntity::getQuestionId, a -> a));

			BigDecimal sumTotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
			List<SubmissionAnswerEntity> answersToSave = new ArrayList<>();

			for (AssignmentQuestionEntity q : questions) {
				SubmissionAnswerEntity answer = answerMap.get(q.getId());
				if (answer == null) {
					answer = new SubmissionAnswerEntity(submission, q.getId(), clock.instant());
				}

				GradeSubmissionRequest.QuestionGradeItem item = gradeMap.get(q.getId());
				if (item != null && item.points() != null) {
					answer.setPoints(item.points().setScale(2, RoundingMode.HALF_UP));
				}
				if (item != null && item.feedback() != null) {
					answer.setFeedback(item.feedback());
				}

				BigDecimal effective = answer.getPoints() != null ? answer.getPoints() : answer.getAutoPoints();
				if (effective != null) {
					sumTotal = sumTotal.add(effective);
				}
				answersToSave.add(answer);
			}

			answerRepository.saveAll(answersToSave);
			submission.setTotalPoints(sumTotal);
			finalTotal = sumTotal;
		}

		submission.setStatus(SubmissionStatus.GRADED);
		submission.setOverallFeedback(request.overallFeedback());
		submission.setGradedBy(instructorUserId);
		submission.setGradedAt(clock.instant());
		submission.setVersion(submission.getVersion() + 1);
		SubmissionEntity saved = submissionRepository.save(submission);

		publishEventAfterCommit(new SubmissionGradedEvent(
				assignment.getCourseId(),
				assignment.getId(),
				saved.getId(),
				saved.getUserId(),
				instructorUserId,
				finalTotal,
				assignment.getMaxPoints(),
				false,
				regrade));

		auditService.record(new AuditEvent(
				instructorUserId,
				AssignmentAuditActions.SUBMISSION_GRADED,
				"SUBMISSION",
				saved.getId().toString(),
				Map.of(
						"courseId", assignment.getCourseId(),
						"submissionId", saved.getId(),
						"totalPoints", finalTotal)));

		return buildDetailDto(saved, assignment);
	}

	private InstructorSubmissionDetailDto buildDetailDto(SubmissionEntity submission, AssignmentEntity assignment) {
		List<AssignmentQuestionEntity> questions =
				questionRepository.findByAssignmentIdOrderByPositionAsc(assignment.getId());

		Map<Long, SubmissionAnswerEntity> answersByQuestion =
				answerRepository.findBySubmissionId(submission.getId()).stream()
						.collect(Collectors.toMap(SubmissionAnswerEntity::getQuestionId, a -> a));

		List<SubmissionFileEntity> files = fileRepository.findBySubmissionId(submission.getId());

		List<InstructorSubmissionDetailDto.InstructorSubmissionQuestionDto> questionDtos = questions.stream()
				.map(q -> {
					SubmissionAnswerEntity answer = answersByQuestion.get(q.getId());
					String answerText = answer != null ? answer.getAnswerText() : null;
					List<String> selected = answer != null ? answer.getSelectedOptionIds() : List.of();
					BigDecimal autoPoints = answer != null ? answer.getAutoPoints() : null;
					BigDecimal points = answer != null ? answer.getPoints() : null;
					BigDecimal effective = points != null ? points : autoPoints;
					String feedback = answer != null ? answer.getFeedback() : null;

					return new InstructorSubmissionDetailDto.InstructorSubmissionQuestionDto(
							q.getId(),
							q.getPosition(),
							q.getType(),
							q.getPrompt(),
							q.getPoints(),
							q.getCodeLanguage(),
							q.getOptions(),
							answerText,
							selected,
							autoPoints,
							points,
							effective,
							feedback);
				})
				.toList();

		List<InstructorSubmissionDetailDto.InstructorSubmissionFileDto> fileDtos = files.stream()
				.map(f -> new InstructorSubmissionDetailDto.InstructorSubmissionFileDto(
						f.getId(),
						f.getOriginalFilename(),
						f.getMimeType(),
						f.getSizeBytes(),
						f.getStatus().name()))
				.toList();

		return new InstructorSubmissionDetailDto(
				submission.getId(),
				assignment.getId(),
				assignment.getCourseId(),
				assignment.getTitle(),
				assignment.getKind(),
				submission.getStatus().name(),
				submission.getVersion(),
				submission.getStartedAt(),
				submission.getDeadlineAt(),
				submission.getSubmittedAt(),
				submission.isLate(),
				submission.isAutoSubmitted(),
				submission.isAutoGraded(),
				submission.getMarks(),
				submission.getTotalPoints(),
				assignment.getMaxPoints(),
				submission.getOverallFeedback(),
				submission.getTextAnswer(),
				submission.getGradedBy(),
				submission.getGradedAt(),
				fileDtos,
				questionDtos);
	}

	private void verifyCourseOwnership(long courseId, long userId) {
		CourseMetadataSnapshot metadata = courseStructureQueryService.getCourseMetadata(courseId)
				.orElseThrow(() -> new NotFoundException("Course not found"));
		if (metadata.deleted() || !metadata.ownerId().equals(userId)) {
			throw new NotFoundException("Course not found");
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
}
