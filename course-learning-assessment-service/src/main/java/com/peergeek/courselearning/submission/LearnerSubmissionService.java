package com.peergeek.courselearning.submission;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.peergeek.courselearning.assignment.*;
import com.peergeek.courselearning.common.AssignmentKind;
import com.peergeek.courselearning.common.SubmissionStatus;
import com.peergeek.courselearning.exception.BadRequestException;
import com.peergeek.courselearning.exception.ConflictException;
import com.peergeek.courselearning.exception.NotFoundException;
import com.peergeek.courselearning.outbox.OutboxEventWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Service
public class LearnerSubmissionService {

    private final AssignmentRepository assignmentRepository;
    private final AssignmentQuestionRepository questionRepository;
    private final SubmissionRepository submissionRepository;
    private final SubmissionAnswerRepository answerRepository;
    private final SubmissionFileRepository fileRepository;
    private final OutboxEventWriter outboxEventWriter;
    private final ObjectMapper objectMapper;

    public LearnerSubmissionService(
            AssignmentRepository assignmentRepository,
            AssignmentQuestionRepository questionRepository,
            SubmissionRepository submissionRepository,
            SubmissionAnswerRepository answerRepository,
            SubmissionFileRepository fileRepository,
            OutboxEventWriter outboxEventWriter,
            ObjectMapper objectMapper) {
        this.assignmentRepository = assignmentRepository;
        this.questionRepository = questionRepository;
        this.submissionRepository = submissionRepository;
        this.answerRepository = answerRepository;
        this.fileRepository = fileRepository;
        this.outboxEventWriter = outboxEventWriter;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AssignmentDtos.DraftSaveResult saveDraft(
            long assignmentId, AssignmentDtos.DraftSubmissionRequest request, Long userId) {
        SubmissionEntity submission = submissionRepository.findByAssignmentIdAndUserId(assignmentId, userId)
                .orElseThrow(() -> new NotFoundException("Submission attempt not found for assignment: " + assignmentId));

        if (submission.getStatus() != SubmissionStatus.IN_PROGRESS) {
            throw new ConflictException("Cannot save draft: attempt is already submitted.");
        }

        if (request.textAnswer() != null) {
            submission.setTextAnswer(request.textAnswer());
        }

        Instant now = Instant.now();
        if (request.answers() != null) {
            for (var item : request.answers()) {
                SubmissionAnswerEntity answer = answerRepository
                        .findBySubmissionIdAndQuestionId(submission.getId(), item.questionId())
                        .orElse(new SubmissionAnswerEntity(submission.getId(), item.questionId(), item.answerText(), null));

                answer.setAnswerText(item.answerText());
                if (item.selectedOptionIds() != null) {
                    try {
                        answer.setSelectedOptionIds(objectMapper.writeValueAsString(item.selectedOptionIds()));
                    } catch (Exception ignored) {}
                }
                answer.setUpdatedAt(now);
                answerRepository.save(answer);
            }
        }

        submission.setUpdatedAt(now);
        submission = submissionRepository.save(submission);

        return new AssignmentDtos.DraftSaveResult(submission.getVersion(), now);
    }

    @Transactional
    public AssignmentDtos.LearnerSubmissionDetailDto submit(
            long assignmentId, AssignmentDtos.SubmitAssignmentRequest request, Long userId) {
        AssignmentEntity assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new NotFoundException("Assignment not found: " + assignmentId));

        SubmissionEntity submission = submissionRepository.findByAssignmentIdAndUserId(assignmentId, userId)
                .orElseThrow(() -> new NotFoundException("Submission attempt not found for assignment: " + assignmentId));

        if (submission.getStatus() != SubmissionStatus.IN_PROGRESS) {
            throw new ConflictException("Submission has already been submitted.");
        }

        Instant now = Instant.now();
        boolean late = false;
        if (submission.getDeadlineAt() != null && now.isAfter(submission.getDeadlineAt())) {
            if (!assignment.isAllowLate()) {
                throw new BadRequestException("Submission deadline has passed and late submissions are not allowed.");
            }
            late = true;
        }

        submission.setSubmittedAt(now);
        submission.setLate(late);

        // Auto-grading for MCQ
        List<AssignmentQuestionEntity> questions = questionRepository.findByAssignmentIdOrderByPositionAsc(assignmentId);
        List<SubmissionAnswerEntity> answers = answerRepository.findBySubmissionId(submission.getId());
        Map<Long, SubmissionAnswerEntity> answersByQId = new HashMap<>();
        answers.forEach(a -> answersByQId.put(a.getQuestionId(), a));

        if (assignment.getKind() == AssignmentKind.MCQ) {
            BigDecimal totalScore = BigDecimal.ZERO;
            for (AssignmentQuestionEntity q : questions) {
                SubmissionAnswerEntity ans = answersByQId.get(q.getId());
                BigDecimal qPoints = q.getPoints() != null ? q.getPoints() : BigDecimal.ONE;

                List<QuestionOption> options = List.of();
                if (q.getOptions() != null) {
                    try {
                        options = objectMapper.readValue(q.getOptions(), new TypeReference<List<QuestionOption>>() {});
                    } catch (Exception ignored) {}
                }

                Set<String> correctIds = new HashSet<>();
                options.stream().filter(QuestionOption::correct).forEach(opt -> correctIds.add(opt.id()));

                List<String> userSelected = List.of();
                if (ans != null && ans.getSelectedOptionIds() != null) {
                    try {
                        userSelected = objectMapper.readValue(ans.getSelectedOptionIds(), new TypeReference<List<String>>() {});
                    } catch (Exception ignored) {}
                }

                Set<String> userSelectedSet = new HashSet<>(userSelected);
                boolean isCorrect = !correctIds.isEmpty() && correctIds.equals(userSelectedSet);

                BigDecimal awarded = isCorrect ? qPoints : BigDecimal.ZERO;
                totalScore = totalScore.add(awarded);

                if (ans != null) {
                    ans.setAutoPoints(awarded);
                    ans.setPoints(awarded);
                    answerRepository.save(ans);
                }
            }

            submission.setMarks(totalScore);
            submission.setTotalPoints(totalScore);
            submission.setAutoGraded(true);
            submission.setStatus(SubmissionStatus.GRADED);
            submission.setGradedAt(now);
        } else {
            submission.setStatus(SubmissionStatus.SUBMITTED);
        }

        submission = submissionRepository.save(submission);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("submissionId", submission.getId());
        payload.put("assignmentId", assignment.getId());
        payload.put("courseId", assignment.getCourseId());
        payload.put("userId", userId);
        payload.put("learnerId", userId);
        payload.put("status", submission.getStatus().name());
        payload.put("submittedAt", now.toString());

        outboxEventWriter.writeEvent(
                "peergeek.learning.submission-submitted.v1",
                "SubmissionSubmitted",
                payload
        );

        if (submission.getStatus() == SubmissionStatus.GRADED) {
            Map<String, Object> gradePayload = new LinkedHashMap<>(payload);
            gradePayload.put("marks", submission.getMarks());
            gradePayload.put("totalPoints", submission.getTotalPoints());
            outboxEventWriter.writeEvent(
                    "peergeek.learning.submission-graded.v1",
                    "SubmissionGraded",
                    gradePayload
            );
        }

        return toDetailDto(submission, assignment, questions, answerRepository.findBySubmissionId(submission.getId()));
    }

    @Transactional(readOnly = true)
    public AssignmentDtos.LearnerSubmissionDetailDto getLearnerSubmission(long assignmentId, Long userId) {
        AssignmentEntity assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new NotFoundException("Assignment not found: " + assignmentId));

        SubmissionEntity submission = submissionRepository.findByAssignmentIdAndUserId(assignmentId, userId)
                .orElseThrow(() -> new NotFoundException("Submission not found for assignment: " + assignmentId));

        List<AssignmentQuestionEntity> questions = questionRepository.findByAssignmentIdOrderByPositionAsc(assignmentId);
        List<SubmissionAnswerEntity> answers = answerRepository.findBySubmissionId(submission.getId());

        return toDetailDto(submission, assignment, questions, answers);
    }

    private AssignmentDtos.LearnerSubmissionDetailDto toDetailDto(
            SubmissionEntity s,
            AssignmentEntity a,
            List<AssignmentQuestionEntity> questions,
            List<SubmissionAnswerEntity> answers) {

        Map<Long, SubmissionAnswerEntity> answersByQId = new HashMap<>();
        answers.forEach(ans -> answersByQId.put(ans.getQuestionId(), ans));

        boolean showAnswers = a.isShowCorrectAnswers() || s.getStatus() == SubmissionStatus.GRADED;

        List<AssignmentDtos.LearnerSubmissionDetailDto.LearnerSubmissionQuestionDto> qDtos = questions.stream()
                .map(q -> {
                    List<QuestionOption> options = List.of();
                    if (q.getOptions() != null) {
                        try {
                            options = objectMapper.readValue(q.getOptions(), new TypeReference<List<QuestionOption>>() {});
                        } catch (Exception ignored) {}
                    }

                    List<AssignmentDtos.LearnerSubmissionDetailDto.SubmissionQuestionOptionDto> optionDtos = options.stream()
                            .map(opt -> new AssignmentDtos.LearnerSubmissionDetailDto.SubmissionQuestionOptionDto(
                                    opt.id(),
                                    opt.text(),
                                    showAnswers ? opt.correct() : null
                            ))
                            .toList();

                    SubmissionAnswerEntity ans = answersByQId.get(q.getId());
                    AssignmentDtos.LearnerSubmissionDetailDto.LearnerQuestionAnswerDto studentAns = null;
                    if (ans != null) {
                        List<String> selected = List.of();
                        if (ans.getSelectedOptionIds() != null) {
                            try {
                                selected = objectMapper.readValue(ans.getSelectedOptionIds(), new TypeReference<List<String>>() {});
                            } catch (Exception ignored) {}
                        }
                        studentAns = new AssignmentDtos.LearnerSubmissionDetailDto.LearnerQuestionAnswerDto(
                                ans.getAnswerText(),
                                selected,
                                ans.getPoints(),
                                ans.getFeedback()
                        );
                    }

                    return new AssignmentDtos.LearnerSubmissionDetailDto.LearnerSubmissionQuestionDto(
                            q.getId(),
                            q.getPosition(),
                            q.getType(),
                            q.getPrompt(),
                            q.getPoints(),
                            q.getCodeLanguage(),
                            optionDtos,
                            studentAns
                    );
                })
                .toList();

        List<SubmissionFileEntity> files = fileRepository.findBySubmissionId(s.getId());
        List<AssignmentDtos.LearnerSubmissionDetailDto.SubmissionFileInfoDto> fileDtos = files.stream()
                .map(f -> new AssignmentDtos.LearnerSubmissionDetailDto.SubmissionFileInfoDto(
                        f.getId(), f.getOriginalFilename(), f.getMimeType(), f.getSizeBytes(), f.getStatus()))
                .toList();

        return new AssignmentDtos.LearnerSubmissionDetailDto(
                s.getId(),
                a.getId(),
                a.getTitle(),
                a.getKind(),
                s.getStatus().name(),
                s.getStartedAt(),
                s.getDeadlineAt(),
                s.getSubmittedAt(),
                s.isLate(),
                s.isAutoSubmitted(),
                s.isAutoGraded(),
                s.getMarks(),
                s.getTotalPoints(),
                a.getMaxPoints(),
                s.getOverallFeedback(),
                s.getTextAnswer(),
                fileDtos,
                qDtos
        );
    }
}

