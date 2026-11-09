package com.peergeek.courselearning.assignment;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.peergeek.courselearning.common.AssignmentStatus;
import com.peergeek.courselearning.common.EnrollmentStatus;
import com.peergeek.courselearning.common.PageResponse;
import com.peergeek.courselearning.common.SubmissionStatus;
import com.peergeek.courselearning.course.CourseEntity;
import com.peergeek.courselearning.course.CourseRepository;
import com.peergeek.courselearning.enrollment.EnrollmentEntity;
import com.peergeek.courselearning.enrollment.EnrollmentRepository;
import com.peergeek.courselearning.exception.BadRequestException;
import com.peergeek.courselearning.exception.ConflictException;
import com.peergeek.courselearning.exception.EnrollmentRequiredException;
import com.peergeek.courselearning.exception.NotFoundException;
import com.peergeek.courselearning.submission.SubmissionAnswerEntity;
import com.peergeek.courselearning.submission.SubmissionAnswerRepository;
import com.peergeek.courselearning.submission.SubmissionEntity;
import com.peergeek.courselearning.submission.SubmissionRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class LearnerAssignmentService {

    private final AssignmentRepository assignmentRepository;
    private final AssignmentQuestionRepository questionRepository;
    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final SubmissionRepository submissionRepository;
    private final SubmissionAnswerRepository answerRepository;
    private final ObjectMapper objectMapper;

    public LearnerAssignmentService(
            AssignmentRepository assignmentRepository,
            AssignmentQuestionRepository questionRepository,
            CourseRepository courseRepository,
            EnrollmentRepository enrollmentRepository,
            SubmissionRepository submissionRepository,
            SubmissionAnswerRepository answerRepository,
            ObjectMapper objectMapper) {
        this.assignmentRepository = assignmentRepository;
        this.questionRepository = questionRepository;
        this.courseRepository = courseRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.submissionRepository = submissionRepository;
        this.answerRepository = answerRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<AssignmentDtos.LearnerAssignmentSummaryDto> getAssignmentsForCourse(
            long courseId, Long lessonId, Long moduleId, Long userId) {
        requireActiveEnrollment(courseId, userId);
        CourseEntity course = courseRepository.findByIdAndDeletedAtIsNull(courseId).orElseThrow();

        List<AssignmentEntity> assignments = assignmentRepository.findByCourseIdAndStatusOrderByCreatedAtDesc(
                courseId, AssignmentStatus.PUBLISHED);

        List<AssignmentDtos.LearnerAssignmentSummaryDto> result = new ArrayList<>();
        for (AssignmentEntity a : assignments) {
            if (lessonId != null && !Objects.equals(a.getLessonId(), lessonId)) continue;
            if (moduleId != null && !Objects.equals(a.getModuleId(), moduleId)) continue;

            Optional<SubmissionEntity> subOpt = submissionRepository.findByAssignmentIdAndUserId(a.getId(), userId);
            String status = subOpt.map(s -> s.getStatus().name()).orElse("TODO");
            boolean late = subOpt.map(SubmissionEntity::isLate).orElse(false);
            var points = subOpt.map(SubmissionEntity::getTotalPoints).orElse(null);

            result.add(new AssignmentDtos.LearnerAssignmentSummaryDto(
                    a.getId(),
                    course.getId(),
                    course.getTitle(),
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
                    points
            ));
        }

        return result;
    }

    @Transactional(readOnly = true)
    public PageResponse<AssignmentDtos.LearnerAssignmentSummaryDto> getAssignmentsCrossCourse(
            String filter, Pageable pageable, Long userId) {
        List<EnrollmentEntity> enrollments = enrollmentRepository.findByUserIdAndStatusOrderByEnrolledAtDesc(
                userId, EnrollmentStatus.ACTIVE);

        List<AssignmentDtos.LearnerAssignmentSummaryDto> all = new ArrayList<>();
        for (EnrollmentEntity en : enrollments) {
            all.addAll(getAssignmentsForCourse(en.getCourseId(), null, null, userId));
        }

        if ("todo".equalsIgnoreCase(filter)) {
            all = all.stream().filter(a -> "TODO".equalsIgnoreCase(a.status()) || "IN_PROGRESS".equalsIgnoreCase(a.status())).toList();
        } else if ("submitted".equalsIgnoreCase(filter)) {
            all = all.stream().filter(a -> "SUBMITTED".equalsIgnoreCase(a.status()) || "GRADED".equalsIgnoreCase(a.status())).toList();
        }

        int page = pageable.getPageNumber();
        int size = pageable.getPageSize();
        int total = all.size();
        int from = Math.min(page * size, total);
        int to = Math.min(from + size, total);

        return PageResponse.of(all.subList(from, to), page, size, total);
    }

    @Transactional(readOnly = true)
    public AssignmentDtos.LearnerAssignmentDetailDto getAssignmentDetail(long assignmentId, Long userId) {
        AssignmentEntity assignment = requireAssignment(assignmentId);
        requireActiveEnrollment(assignment.getCourseId(), userId);

        Optional<SubmissionEntity> subOpt = submissionRepository.findByAssignmentIdAndUserId(assignmentId, userId);
        AssignmentDtos.LearnerAttemptSummaryDto attemptSummary = subOpt.map(s -> new AssignmentDtos.LearnerAttemptSummaryDto(
                s.getId(),
                s.getStatus().name(),
                s.getStartedAt(),
                s.getDeadlineAt(),
                s.getSubmittedAt(),
                s.getMarks(),
                s.getTotalPoints()
        )).orElse(null);

        return new AssignmentDtos.LearnerAssignmentDetailDto(
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
                assignment.getModuleId(),
                assignment.getLessonId(),
                attemptSummary
        );
    }

    @Transactional
    public AssignmentDtos.LearnerAttemptDto startAttempt(long assignmentId, Long userId) {
        AssignmentEntity assignment = requireAssignment(assignmentId);
        requireActiveEnrollment(assignment.getCourseId(), userId);

        if (assignment.getStatus() != AssignmentStatus.PUBLISHED) {
            throw new BadRequestException("Assignment is not open for submissions.");
        }

        Instant now = Instant.now();
        if (assignment.getOpensAt() != null && now.isBefore(assignment.getOpensAt())) {
            throw new BadRequestException("Assignment has not opened yet.");
        }

        Optional<SubmissionEntity> existing = submissionRepository.findByAssignmentIdAndUserId(assignmentId, userId);
        if (existing.isPresent()) {
            SubmissionEntity s = existing.get();
            if (s.getStatus() != SubmissionStatus.IN_PROGRESS) {
                throw new ConflictException("Assignment attempt has already been submitted.");
            }
            return toAttemptDto(s, assignment);
        }

        Instant deadline = null;
        if (assignment.getTimeLimitMinutes() != null && assignment.getTimeLimitMinutes() > 0) {
            deadline = now.plus(Duration.ofMinutes(assignment.getTimeLimitMinutes()));
            if (assignment.getDueAt() != null && !assignment.isAllowLate() && assignment.getDueAt().isBefore(deadline)) {
                deadline = assignment.getDueAt();
            }
        } else if (assignment.getDueAt() != null) {
            deadline = assignment.getDueAt();
        }

        SubmissionEntity submission = new SubmissionEntity(assignmentId, userId);
        submission.setStartedAt(now);
        submission.setDeadlineAt(deadline);
        submission.setStatus(SubmissionStatus.IN_PROGRESS);
        submission = submissionRepository.save(submission);

        assignment.setAttemptCount(assignment.getAttemptCount() + 1);
        assignmentRepository.save(assignment);

        return toAttemptDto(submission, assignment);
    }

    @Transactional(readOnly = true)
    public AssignmentDtos.LearnerAttemptDto getAttempt(long assignmentId, Long userId) {
        AssignmentEntity assignment = requireAssignment(assignmentId);
        requireActiveEnrollment(assignment.getCourseId(), userId);

        SubmissionEntity submission = submissionRepository.findByAssignmentIdAndUserId(assignmentId, userId)
                .orElseThrow(() -> new NotFoundException("Attempt not found for assignment: " + assignmentId));

        return toAttemptDto(submission, assignment);
    }

    private AssignmentEntity requireAssignment(long id) {
        return assignmentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Assignment not found: " + id));
    }

    private void requireActiveEnrollment(long courseId, long userId) {
        if (!enrollmentRepository.existsByUserIdAndCourseIdAndStatus(userId, courseId, EnrollmentStatus.ACTIVE)) {
            throw new EnrollmentRequiredException();
        }
    }

    private AssignmentDtos.LearnerAttemptDto toAttemptDto(SubmissionEntity s, AssignmentEntity a) {
        List<AssignmentQuestionEntity> questions = questionRepository.findByAssignmentIdOrderByPositionAsc(a.getId());
        List<AssignmentDtos.LearnerQuestionDto> questionDtos = questions.stream()
                .map(q -> {
                    List<QuestionOption> allOptions = List.of();
                    if (q.getOptions() != null) {
                        try {
                            allOptions = objectMapper.readValue(q.getOptions(), new TypeReference<List<QuestionOption>>() {});
                        } catch (Exception ignored) {}
                    }
                    List<AssignmentDtos.LearnerQuestionOptionDto> safeOptions = allOptions.stream()
                            .map(opt -> new AssignmentDtos.LearnerQuestionOptionDto(opt.id(), opt.text()))
                            .toList();

                    return new AssignmentDtos.LearnerQuestionDto(
                            q.getId(),
                            q.getPosition(),
                            q.getType(),
                            q.getPrompt(),
                            q.getPoints(),
                            safeOptions,
                            q.getCodeLanguage()
                    );
                })
                .toList();

        List<SubmissionAnswerEntity> answers = answerRepository.findBySubmissionId(s.getId());
        List<AssignmentDtos.LearnerAnswerDto> answerDtos = answers.stream()
                .map(ans -> {
                    List<String> selected = List.of();
                    if (ans.getSelectedOptionIds() != null) {
                        try {
                            selected = objectMapper.readValue(ans.getSelectedOptionIds(), new TypeReference<List<String>>() {});
                        } catch (Exception ignored) {}
                    }
                    return new AssignmentDtos.LearnerAnswerDto(ans.getQuestionId(), ans.getAnswerText(), selected);
                })
                .toList();

        return new AssignmentDtos.LearnerAttemptDto(
                s.getId(),
                s.getAssignmentId(),
                s.getStatus().name(),
                s.getStartedAt(),
                s.getDeadlineAt(),
                Instant.now(),
                s.getVersion(),
                s.getTextAnswer(),
                questionDtos,
                answerDtos
        );
    }
}

