package com.peergeek.courselearning.submission;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.peergeek.courselearning.assignment.AssignmentEntity;
import com.peergeek.courselearning.assignment.AssignmentQuestionEntity;
import com.peergeek.courselearning.assignment.AssignmentQuestionRepository;
import com.peergeek.courselearning.assignment.AssignmentRepository;
import com.peergeek.courselearning.common.PageResponse;
import com.peergeek.courselearning.common.SubmissionStatus;
import com.peergeek.courselearning.course.CourseEntity;
import com.peergeek.courselearning.course.CourseRepository;
import com.peergeek.courselearning.exception.ForbiddenException;
import com.peergeek.courselearning.exception.NotFoundException;
import com.peergeek.courselearning.outbox.OutboxEventWriter;
import com.peergeek.courselearning.profile.LearningUserProfileEntity;
import com.peergeek.courselearning.profile.LearningUserProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Service
public class InstructorGradingService {

    private final AssignmentRepository assignmentRepository;
    private final AssignmentQuestionRepository questionRepository;
    private final SubmissionRepository submissionRepository;
    private final SubmissionAnswerRepository answerRepository;
    private final SubmissionFileRepository fileRepository;
    private final CourseRepository courseRepository;
    private final LearningUserProfileRepository userProfileRepository;
    private final OutboxEventWriter outboxEventWriter;
    private final ObjectMapper objectMapper;

    public InstructorGradingService(
            AssignmentRepository assignmentRepository,
            AssignmentQuestionRepository questionRepository,
            SubmissionRepository submissionRepository,
            SubmissionAnswerRepository answerRepository,
            SubmissionFileRepository fileRepository,
            CourseRepository courseRepository,
            LearningUserProfileRepository userProfileRepository,
            OutboxEventWriter outboxEventWriter,
            ObjectMapper objectMapper) {
        this.assignmentRepository = assignmentRepository;
        this.questionRepository = questionRepository;
        this.submissionRepository = submissionRepository;
        this.answerRepository = answerRepository;
        this.fileRepository = fileRepository;
        this.courseRepository = courseRepository;
        this.userProfileRepository = userProfileRepository;
        this.outboxEventWriter = outboxEventWriter;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public PageResponse<SubmissionDtos.InstructorSubmissionSummaryDto> listSubmissions(
            long courseId, Long assignmentId, SubmissionStatus status, int page, int size, String sort, Long ownerId) {
        requireOwnedCourse(courseId, ownerId);

        List<AssignmentEntity> courseAssignments = (assignmentId != null)
                ? assignmentRepository.findById(assignmentId).stream().filter(a -> a.getCourseId().equals(courseId)).toList()
                : assignmentRepository.findByCourseIdOrderByCreatedAtDesc(courseId);

        List<SubmissionDtos.InstructorSubmissionSummaryDto> result = new ArrayList<>();
        for (AssignmentEntity a : courseAssignments) {
            List<SubmissionEntity> subs = submissionRepository.findByAssignmentIdOrderBySubmittedAtDesc(a.getId());
            for (SubmissionEntity s : subs) {
                if (status != null && s.getStatus() != status) continue;

                String learnerName = "Student";
                Optional<LearningUserProfileEntity> prof = userProfileRepository.findById(s.getUserId());
                if (prof.isPresent()) {
                    learnerName = prof.get().getDisplayName() != null ? prof.get().getDisplayName() : prof.get().getUsername();
                }

                result.add(new SubmissionDtos.InstructorSubmissionSummaryDto(
                        s.getId(),
                        a.getId(),
                        a.getTitle(),
                        new SubmissionDtos.InstructorSubmissionSummaryDto.LearnerSummary(s.getUserId(), learnerName),
                        s.getStatus().name(),
                        s.getSubmittedAt(),
                        s.isLate(),
                        s.isAutoSubmitted(),
                        s.isAutoGraded(),
                        s.getTotalPoints(),
                        a.getMaxPoints()
                ));
            }
        }

        int total = result.size();
        int from = Math.min(page * size, total);
        int to = Math.min(from + size, total);

        return PageResponse.of(result.subList(from, to), page, size, total);
    }

    @Transactional(readOnly = true)
    public SubmissionDtos.InstructorSubmissionDetailDto getSubmission(long submissionId, Long ownerId) {
        SubmissionEntity submission = requireSubmission(submissionId);
        AssignmentEntity assignment = requireAssignment(submission.getAssignmentId());
        requireOwnedCourse(assignment.getCourseId(), ownerId);

        return toDetailDto(submission, assignment);
    }

    @Transactional
    public SubmissionDtos.InstructorSubmissionDetailDto gradeSubmission(
            long submissionId, SubmissionDtos.GradeSubmissionRequest request, Long ownerId) {
        SubmissionEntity submission = requireSubmission(submissionId);
        AssignmentEntity assignment = requireAssignment(submission.getAssignmentId());
        requireOwnedCourse(assignment.getCourseId(), ownerId);

        Instant now = Instant.now();
        BigDecimal totalScore = BigDecimal.ZERO;

        if (request.questionGrades() != null) {
            for (var qg : request.questionGrades()) {
                SubmissionAnswerEntity ans = answerRepository
                        .findBySubmissionIdAndQuestionId(submission.getId(), qg.questionId())
                        .orElse(new SubmissionAnswerEntity(submission.getId(), qg.questionId(), null, null));

                ans.setPoints(qg.points());
                ans.setFeedback(qg.feedback());
                ans.setUpdatedAt(now);
                answerRepository.save(ans);

                if (qg.points() != null) {
                    totalScore = totalScore.add(qg.points());
                }
            }
        }

        if (request.marks() != null) {
            totalScore = request.marks();
        }

        submission.setMarks(totalScore);
        submission.setTotalPoints(totalScore);
        submission.setOverallFeedback(request.overallFeedback());
        submission.setGradedBy(ownerId);
        submission.setGradedAt(now);
        submission.setStatus(SubmissionStatus.GRADED);
        submission = submissionRepository.save(submission);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("submissionId", submission.getId());
        payload.put("assignmentId", assignment.getId());
        payload.put("courseId", assignment.getCourseId());
        payload.put("userId", submission.getUserId());
        payload.put("learnerId", submission.getUserId());
        payload.put("status", submission.getStatus().name());
        payload.put("marks", submission.getMarks());
        payload.put("totalPoints", submission.getTotalPoints());
        payload.put("gradedAt", now.toString());

        outboxEventWriter.writeEvent(
                "peergeek.learning.submission-graded.v1",
                "SubmissionGraded",
                payload
        );

        return toDetailDto(submission, assignment);
    }

    private SubmissionEntity requireSubmission(long id) {
        return submissionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Submission not found: " + id));
    }

    private AssignmentEntity requireAssignment(long id) {
        return assignmentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Assignment not found: " + id));
    }

    private void requireOwnedCourse(long courseId, Long ownerId) {
        CourseEntity course = courseRepository.findByIdAndDeletedAtIsNull(courseId)
                .orElseThrow(() -> new NotFoundException("Course not found: " + courseId));
        if (ownerId != null && !course.getOwnerId().equals(ownerId)) {
            throw new ForbiddenException("You do not own course: " + courseId);
        }
    }

    private SubmissionDtos.InstructorSubmissionDetailDto toDetailDto(
            SubmissionEntity s, AssignmentEntity a) {
        Optional<LearningUserProfileEntity> userOpt = userProfileRepository.findById(s.getUserId());
        String name = userOpt.map(u -> u.getDisplayName() != null ? u.getDisplayName() : u.getUsername()).orElse("Student");
        String email = userOpt.map(LearningUserProfileEntity::getEmail).orElse("");
        var learner = new SubmissionDtos.InstructorSubmissionDetailDto.LearnerSummary(s.getUserId(), name, email);

        List<AssignmentQuestionEntity> questions = questionRepository.findByAssignmentIdOrderByPositionAsc(a.getId());
        List<SubmissionAnswerEntity> answers = answerRepository.findBySubmissionId(s.getId());
        Map<Long, SubmissionAnswerEntity> answersByQId = new HashMap<>();
        answers.forEach(ans -> answersByQId.put(ans.getQuestionId(), ans));

        List<SubmissionDtos.InstructorSubmissionDetailDto.SubmissionQuestionAnswerDto> qAnswers = questions.stream()
                .map(q -> {
                    SubmissionAnswerEntity ans = answersByQId.get(q.getId());
                    List<String> selected = List.of();
                    if (ans != null && ans.getSelectedOptionIds() != null) {
                        try {
                            selected = objectMapper.readValue(ans.getSelectedOptionIds(), new TypeReference<List<String>>() {});
                        } catch (Exception ignored) {}
                    }
                    return new SubmissionDtos.InstructorSubmissionDetailDto.SubmissionQuestionAnswerDto(
                            q.getId(),
                            q.getPosition(),
                            q.getPrompt(),
                            q.getPoints(),
                            ans != null ? ans.getAnswerText() : null,
                            selected,
                            ans != null ? ans.getAutoPoints() : null,
                            ans != null ? ans.getPoints() : null,
                            ans != null ? ans.getFeedback() : null
                    );
                })
                .toList();

        List<SubmissionFileEntity> files = fileRepository.findBySubmissionId(s.getId());
        List<SubmissionDtos.InstructorSubmissionDetailDto.SubmissionFileInfoDto> fileDtos = files.stream()
                .map(f -> new SubmissionDtos.InstructorSubmissionDetailDto.SubmissionFileInfoDto(
                        f.getId(), f.getOriginalFilename(), f.getMimeType(), f.getSizeBytes(), f.getStatus()))
                .toList();

        return new SubmissionDtos.InstructorSubmissionDetailDto(
                s.getId(),
                a.getId(),
                a.getTitle(),
                learner,
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
                s.getGradedBy(),
                s.getGradedAt(),
                s.getVersion(),
                fileDtos,
                qAnswers
        );
    }
}

