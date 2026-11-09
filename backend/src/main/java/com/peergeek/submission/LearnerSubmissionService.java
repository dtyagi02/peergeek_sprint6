package com.peergeek.submission;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import com.peergeek.assignment.AssignmentEntity;
import com.peergeek.assignment.AssignmentException;
import com.peergeek.assignment.AssignmentKind;
import com.peergeek.assignment.AssignmentQuestionEntity;
import com.peergeek.assignment.AssignmentQuestionRepository;
import com.peergeek.assignment.AssignmentRepository;
import com.peergeek.assignment.AssignmentStatus;
import com.peergeek.assignment.QuestionOption;
import com.peergeek.assignment.QuestionType;
import com.peergeek.assignment.dto.DraftSaveResult;
import com.peergeek.assignment.dto.DraftSubmissionRequest;
import com.peergeek.assignment.dto.LearnerSubmissionDetailDto;
import com.peergeek.assignment.dto.SubmitAssignmentRequest;
import com.peergeek.config.AssignmentSettings;
import com.peergeek.exception.BadRequestException;
import com.peergeek.exception.NotFoundException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

@Service
public class LearnerSubmissionService {

	private final SubmissionRepository submissionRepository;
	private final SubmissionAnswerRepository answerRepository;
	private final SubmissionFileRepository fileRepository;
	private final AssignmentRepository assignmentRepository;
	private final AssignmentQuestionRepository questionRepository;
	private final AssignmentSettings settings;
	private final ApplicationEventPublisher eventPublisher;
	private final Clock clock;

	public LearnerSubmissionService(
			SubmissionRepository submissionRepository,
			SubmissionAnswerRepository answerRepository,
			SubmissionFileRepository fileRepository,
			AssignmentRepository assignmentRepository,
			AssignmentQuestionRepository questionRepository,
			AssignmentSettings settings,
			ApplicationEventPublisher eventPublisher,
			Clock clock) {
		this.submissionRepository = submissionRepository;
		this.answerRepository = answerRepository;
		this.fileRepository = fileRepository;
		this.assignmentRepository = assignmentRepository;
		this.questionRepository = questionRepository;
		this.settings = settings;
		this.eventPublisher = eventPublisher;
		this.clock = clock;
	}

	@Transactional
	public DraftSaveResult saveDraft(long assignmentId, DraftSubmissionRequest request, long userId) {
		SubmissionEntity submission = submissionRepository.findWithLockByAssignmentIdAndUserId(assignmentId, userId)
				.orElseThrow(() -> new NotFoundException("Submission not found"));

		if (submission.getStatus() == SubmissionStatus.SUBMITTED || submission.getStatus() == SubmissionStatus.GRADED) {
			throw AssignmentException.alreadySubmitted("Assignment has already been submitted");
		}

		Instant now = clock.instant();

		// Check deadline + grace
		if (submission.getDeadlineAt() != null) {
			Instant cutoff = submission.getDeadlineAt().plus(Duration.ofSeconds(settings.getGraceSeconds()));
			if (now.isAfter(cutoff)) {
				finalizeExpiredSubmission(submission.getId());
				throw AssignmentException.attemptExpired("Attempt has expired");
			}
		}

		// Optimistic locking
		if (submission.getVersion() != request.version()) {
			throw AssignmentException.draftVersionConflict(submission.getVersion());
		}

		AssignmentEntity assignment = assignmentRepository.findById(assignmentId)
				.orElseThrow(() -> new NotFoundException("Assignment not found"));

		// Text length checks
		if (request.textAnswer() != null && request.textAnswer().length() > settings.getMaxProjectTextAnswerLength()) {
			throw new BadRequestException("Text answer exceeds maximum allowed length of " + settings.getMaxProjectTextAnswerLength());
		}

		List<AssignmentQuestionEntity> questions = questionRepository.findByAssignmentIdOrderByPositionAsc(assignmentId);
		Map<Long, AssignmentQuestionEntity> questionMap = questions.stream()
				.collect(Collectors.toMap(AssignmentQuestionEntity::getId, q -> q));

		// Validate question IDs, option IDs, and per-type limits
		for (DraftSubmissionRequest.DraftAnswerItem item : request.answers()) {
			AssignmentQuestionEntity question = questionMap.get(item.questionId());
			if (question == null) {
				throw new BadRequestException("Unknown question id: " + item.questionId());
			}

			if (item.selectedOptionIds() != null && !item.selectedOptionIds().isEmpty()) {
				if (question.getType() == QuestionType.DESCRIPTIVE || question.getType() == QuestionType.CODE) {
					throw new BadRequestException("Non-MCQ question cannot have selected options");
				}

				Set<String> validOptionIds = question.getOptions().stream()
						.map(QuestionOption::id)
						.collect(Collectors.toSet());

				for (String optId : item.selectedOptionIds()) {
					if (!validOptionIds.contains(optId)) {
						throw new BadRequestException("Unknown option id: " + optId + " for question " + item.questionId());
					}
				}

				if (question.getType() == QuestionType.MCQ_SINGLE && item.selectedOptionIds().size() > 1) {
					throw new BadRequestException("Single-choice question cannot have multiple selected options");
				}
			}

			if (item.answerText() != null) {
				if (question.getType() == QuestionType.DESCRIPTIVE
						&& item.answerText().length() > settings.getMaxDescriptiveAnswerLength()) {
					throw new BadRequestException("Descriptive answer exceeds limit of " + settings.getMaxDescriptiveAnswerLength());
				}
				if (question.getType() == QuestionType.CODE
						&& item.answerText().length() > settings.getMaxCodeAnswerLength()) {
					throw new BadRequestException("Code answer exceeds limit of " + settings.getMaxCodeAnswerLength());
				}
			}
		}

		// Upsert answers (deduplicating by questionId)
		Map<Long, SubmissionAnswerEntity> existingAnswers = answerRepository.findBySubmissionId(submission.getId()).stream()
				.collect(Collectors.toMap(SubmissionAnswerEntity::getQuestionId, a -> a));

		Map<Long, SubmissionAnswerEntity> answersToSaveMap = new java.util.LinkedHashMap<>();
		for (DraftSubmissionRequest.DraftAnswerItem item : request.answers()) {
			SubmissionAnswerEntity answer = existingAnswers.get(item.questionId());
			if (answer == null) {
				answer = answersToSaveMap.get(item.questionId());
				if (answer == null) {
					answer = new SubmissionAnswerEntity(submission, item.questionId(), now);
				}
			}
			answer.setAnswerText(item.answerText());
			List<String> distinctOptions = item.selectedOptionIds() != null
					? item.selectedOptionIds().stream().distinct().toList()
					: null;
			answer.setSelectedOptionIds(distinctOptions);
			answer.setUpdatedAt(now);
			answersToSaveMap.put(item.questionId(), answer);
		}

		if (!answersToSaveMap.isEmpty()) {
			answerRepository.saveAll(answersToSaveMap.values());
		}

		submission.setTextAnswer(request.textAnswer());
		submission.setVersion(submission.getVersion() + 1);
		submissionRepository.save(submission);

		return new DraftSaveResult(submission.getVersion(), now);
	}

	@Transactional
	public LearnerSubmissionDetailDto submit(long assignmentId, SubmitAssignmentRequest request, long userId) {
		SubmissionEntity submission = submissionRepository.findWithLockByAssignmentIdAndUserId(assignmentId, userId)
				.orElseThrow(() -> new NotFoundException("Submission not found"));

		// Idempotent resubmit
		if (submission.getStatus() == SubmissionStatus.SUBMITTED || submission.getStatus() == SubmissionStatus.GRADED) {
			return getLearnerSubmission(assignmentId, userId);
		}

		Instant now = clock.instant();

		// Check deadline + grace
		if (submission.getDeadlineAt() != null) {
			Instant cutoff = submission.getDeadlineAt().plus(Duration.ofSeconds(settings.getGraceSeconds()));
			if (now.isAfter(cutoff)) {
				finalizeExpiredSubmission(submission.getId());
				throw AssignmentException.attemptExpired("Attempt has expired");
			}
		}

		AssignmentEntity assignment = assignmentRepository.findById(assignmentId)
				.orElseThrow(() -> new NotFoundException("Assignment not found"));

		// Untimed due date check
		if (submission.getDeadlineAt() == null && assignment.getDueAt() != null && now.isAfter(assignment.getDueAt())) {
			if (!assignment.isAllowLate()) {
				throw AssignmentException.deadlinePassed("Assignment deadline has passed");
			}
		}

		// Optimistic locking: allow request.version() <= submission.getVersion()
		if (request.version() > submission.getVersion()) {
			throw AssignmentException.draftVersionConflict(submission.getVersion());
		}

		// Project assignment validation
		if (assignment.getKind() == AssignmentKind.PROJECT) {
			int readyFilesCount = fileRepository.countBySubmissionIdAndStatus(submission.getId(), SubmissionFileStatus.READY);
			boolean hasText = StringUtils.hasText(submission.getTextAnswer());
			boolean hasFiles = readyFilesCount > 0;

			if (assignment.isAllowText() && !assignment.isAllowFiles() && !hasText) {
				throw new BadRequestException("Text submission is required for this project assignment");
			}
			if (assignment.isAllowFiles() && !assignment.isAllowText() && !hasFiles) {
				throw new BadRequestException("At least one file submission is required for this project assignment");
			}
			if (assignment.isAllowText() && assignment.isAllowFiles() && !hasText && !hasFiles) {
				throw new BadRequestException("Either a text submission or at least one file is required");
			}
		}

		boolean late = (assignment.getDueAt() != null && now.isAfter(assignment.getDueAt())) || submission.isLate();
		submission.setLate(late);
		submission.setSubmittedAt(now);
		submission.setAutoSubmitted(false);

		// Score answers
		executeScoring(submission, assignment, now);

		submission.setVersion(submission.getVersion() + 1);
		SubmissionEntity saved = submissionRepository.save(submission);

		publishEventAfterCommit(new SubmissionSubmittedEvent(
				assignment.getCourseId(),
				assignment.getId(),
				saved.getId(),
				saved.getUserId(),
				saved.isLate(),
				false));

		if (saved.isAutoGraded()) {
			publishEventAfterCommit(new SubmissionGradedEvent(
					assignment.getCourseId(),
					assignment.getId(),
					saved.getId(),
					saved.getUserId(),
					null,
					saved.getTotalPoints(),
					assignment.getMaxPoints(),
					true,
					false));
		}

		return getLearnerSubmission(assignmentId, userId);
	}

	@Transactional
	public boolean finalizeExpiredSubmission(Long submissionId) {
		Optional<SubmissionEntity> opt = submissionRepository.findByIdWithLock(submissionId);
		if (opt.isEmpty()) {
			return false;
		}

		SubmissionEntity submission = opt.get();
		if (submission.getStatus() != SubmissionStatus.IN_PROGRESS) {
			return false;
		}

		if (submission.getDeadlineAt() == null) {
			return false;
		}

		Instant now = clock.instant();
		Instant cutoff = submission.getDeadlineAt().plus(Duration.ofSeconds(settings.getGraceSeconds()));
		if (!now.isAfter(cutoff)) {
			return false;
		}

		AssignmentEntity assignment = assignmentRepository.findById(submission.getAssignmentId()).orElse(null);
		if (assignment == null) {
			return false;
		}

		submission.setAutoSubmitted(true);
		submission.setSubmittedAt(submission.getDeadlineAt());
		boolean late = assignment.getDueAt() != null && submission.getDeadlineAt().isAfter(assignment.getDueAt());
		submission.setLate(late);

		// Score answers using autosaved answers
		executeScoring(submission, assignment, submission.getDeadlineAt());

		submission.setVersion(submission.getVersion() + 1);
		SubmissionEntity saved = submissionRepository.save(submission);

		publishEventAfterCommit(new SubmissionSubmittedEvent(
				assignment.getCourseId(),
				assignment.getId(),
				saved.getId(),
				saved.getUserId(),
				saved.isLate(),
				true));

		if (saved.isAutoGraded()) {
			publishEventAfterCommit(new SubmissionGradedEvent(
					assignment.getCourseId(),
					assignment.getId(),
					saved.getId(),
					saved.getUserId(),
					null,
					saved.getTotalPoints(),
					assignment.getMaxPoints(),
					true,
					false));
		}

		return true;
	}

	private void executeScoring(SubmissionEntity submission, AssignmentEntity assignment, Instant evaluatedAt) {
		List<AssignmentQuestionEntity> questions =
				questionRepository.findByAssignmentIdOrderByPositionAsc(assignment.getId());

		if (assignment.getKind() == AssignmentKind.PROJECT || questions.isEmpty()) {
			submission.setStatus(SubmissionStatus.SUBMITTED);
			submission.setAutoGraded(false);
			submission.setTotalPoints(null);
			return;
		}

		Map<Long, SubmissionAnswerEntity> answerMap = answerRepository.findBySubmissionId(submission.getId()).stream()
				.collect(Collectors.toMap(SubmissionAnswerEntity::getQuestionId, a -> a));

		boolean allAutoGradable = true;
		BigDecimal sumAutoPoints = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
		List<SubmissionAnswerEntity> answersToSave = new ArrayList<>();

		for (AssignmentQuestionEntity q : questions) {
			SubmissionAnswerEntity answer = answerMap.get(q.getId());
			if (answer == null) {
				answer = new SubmissionAnswerEntity(submission, q.getId(), evaluatedAt);
			}

			Set<String> validOptionIds = q.getOptions().stream()
					.map(QuestionOption::id)
					.collect(Collectors.toSet());

			if (q.getType() == QuestionType.MCQ_SINGLE) {
				Optional<QuestionOption> correctOpt = q.getOptions().stream()
						.filter(QuestionOption::correct)
						.findFirst();

				List<String> selected = answer.getSelectedOptionIds();
				List<String> validSelected = selected != null
						? selected.stream().filter(validOptionIds::contains).distinct().toList()
						: List.of();

				if (correctOpt.isPresent() && validSelected.size() == 1
						&& validSelected.contains(correctOpt.get().id())) {
					answer.setAutoPoints(q.getPoints());
				} else {
					answer.setAutoPoints(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
				}
				sumAutoPoints = sumAutoPoints.add(answer.getAutoPoints());
				answersToSave.add(answer);
			} else if (q.getType() == QuestionType.MCQ_MULTI) {
				Set<String> correctIds = q.getOptions().stream()
						.filter(QuestionOption::correct)
						.map(QuestionOption::id)
						.collect(Collectors.toSet());

				Set<String> selected = answer.getSelectedOptionIds() != null
						? answer.getSelectedOptionIds().stream().filter(validOptionIds::contains).collect(Collectors.toSet())
						: Set.of();

				if (!correctIds.isEmpty() && selected.equals(correctIds)) {
					answer.setAutoPoints(q.getPoints());
				} else {
					answer.setAutoPoints(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
				}
				sumAutoPoints = sumAutoPoints.add(answer.getAutoPoints());
				answersToSave.add(answer);
			} else {
				allAutoGradable = false;
				answer.setAutoPoints(null);
				answersToSave.add(answer);
			}
		}

		answerRepository.saveAll(answersToSave);

		if (allAutoGradable) {
			submission.setStatus(SubmissionStatus.GRADED);
			submission.setAutoGraded(true);
			submission.setGradedBy(null);
			submission.setGradedAt(evaluatedAt);
			submission.setTotalPoints(sumAutoPoints);
		} else {
			submission.setStatus(SubmissionStatus.SUBMITTED);
			submission.setAutoGraded(false);
			submission.setTotalPoints(null);
		}
	}

	@Transactional
	public LearnerSubmissionDetailDto getLearnerSubmission(long assignmentId, long userId) {
		SubmissionEntity submission = submissionRepository.findByAssignmentIdAndUserId(assignmentId, userId)
				.orElseThrow(() -> new NotFoundException("Submission not found"));

		// Lazy finalization check
		if (submission.getStatus() == SubmissionStatus.IN_PROGRESS && submission.getDeadlineAt() != null) {
			Instant cutoff = submission.getDeadlineAt().plus(Duration.ofSeconds(settings.getGraceSeconds()));
			if (clock.instant().isAfter(cutoff)) {
				finalizeExpiredSubmission(submission.getId());
				submission = submissionRepository.findById(submission.getId()).orElse(submission);
			}
		}

		AssignmentEntity assignment = assignmentRepository.findById(assignmentId)
				.orElseThrow(() -> new NotFoundException("Assignment not found"));

		List<AssignmentQuestionEntity> questions =
				questionRepository.findByAssignmentIdOrderByPositionAsc(assignmentId);

		Map<Long, SubmissionAnswerEntity> answersByQuestion =
				answerRepository.findBySubmissionId(submission.getId()).stream()
						.collect(Collectors.toMap(SubmissionAnswerEntity::getQuestionId, a -> a));

		List<SubmissionFileEntity> files = fileRepository.findBySubmissionId(submission.getId());

		boolean isGraded = submission.getStatus() == SubmissionStatus.GRADED;
		boolean showCorrect = isGraded && assignment.isShowCorrectAnswers();

		List<LearnerSubmissionDetailDto.LearnerSubmissionQuestionDto> questionDtos = questions.stream()
				.map(q -> {
					SubmissionAnswerEntity answer = answersByQuestion.get(q.getId());
					String answerText = answer != null ? answer.getAnswerText() : null;
					List<String> selected = answer != null ? answer.getSelectedOptionIds() : List.of();

					BigDecimal pointsAwarded = null;
					String feedback = null;
					if (isGraded && answer != null) {
						pointsAwarded = answer.getPoints() != null ? answer.getPoints() : answer.getAutoPoints();
						feedback = answer.getFeedback();
					}

					List<LearnerSubmissionDetailDto.SubmissionQuestionOptionDto> options = q.getOptions().stream()
							.map(opt -> new LearnerSubmissionDetailDto.SubmissionQuestionOptionDto(
									opt.id(),
									opt.text(),
									showCorrect ? opt.correct() : null))
							.toList();

					List<String> correctIds = null;
					if (showCorrect && (q.getType() == QuestionType.MCQ_SINGLE || q.getType() == QuestionType.MCQ_MULTI)) {
						correctIds = q.getOptions().stream()
								.filter(QuestionOption::correct)
								.map(QuestionOption::id)
								.toList();
					}

					return new LearnerSubmissionDetailDto.LearnerSubmissionQuestionDto(
							q.getId(),
							q.getPosition(),
							q.getType(),
							q.getPrompt(),
							q.getPoints(),
							q.getCodeLanguage(),
							options,
							answerText,
							selected,
							pointsAwarded,
							feedback,
							correctIds);
				})
				.toList();

		List<LearnerSubmissionDetailDto.SubmissionFileInfoDto> fileDtos = files.stream()
				.map(f -> new LearnerSubmissionDetailDto.SubmissionFileInfoDto(
						f.getId(),
						f.getOriginalFilename(),
						f.getMimeType(),
						f.getSizeBytes(),
						f.getStatus().name()))
				.toList();

		return new LearnerSubmissionDetailDto(
				submission.getId(),
				assignmentId,
				assignment.getTitle(),
				assignment.getKind(),
				submission.getStatus().name(),
				submission.getStartedAt(),
				submission.getDeadlineAt(),
				submission.getSubmittedAt(),
				submission.isLate(),
				submission.isAutoSubmitted(),
				submission.isAutoGraded(),
				isGraded ? submission.getMarks() : null,
				isGraded ? submission.getTotalPoints() : null,
				assignment.getMaxPoints(),
				isGraded ? submission.getOverallFeedback() : null,
				submission.getTextAnswer(),
				fileDtos,
				questionDtos);
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
