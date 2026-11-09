package com.peergeek.courselearning.assignment;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.peergeek.courselearning.common.AssignmentKind;
import com.peergeek.courselearning.common.AssignmentStatus;
import com.peergeek.courselearning.common.PageResponse;
import com.peergeek.courselearning.common.SubmissionStatus;
import com.peergeek.courselearning.course.CourseEntity;
import com.peergeek.courselearning.course.CourseRepository;
import com.peergeek.courselearning.exception.BadRequestException;
import com.peergeek.courselearning.exception.ForbiddenException;
import com.peergeek.courselearning.exception.NotFoundException;
import com.peergeek.courselearning.outbox.OutboxEventWriter;
import com.peergeek.courselearning.submission.SubmissionRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Service
public class InstructorAssignmentService {

    private final AssignmentRepository assignmentRepository;
    private final AssignmentQuestionRepository questionRepository;
    private final CourseRepository courseRepository;
    private final SubmissionRepository submissionRepository;
    private final OutboxEventWriter outboxEventWriter;
    private final ObjectMapper objectMapper;

    public InstructorAssignmentService(
            AssignmentRepository assignmentRepository,
            AssignmentQuestionRepository questionRepository,
            CourseRepository courseRepository,
            SubmissionRepository submissionRepository,
            OutboxEventWriter outboxEventWriter,
            ObjectMapper objectMapper) {
        this.assignmentRepository = assignmentRepository;
        this.questionRepository = questionRepository;
        this.courseRepository = courseRepository;
        this.submissionRepository = submissionRepository;
        this.outboxEventWriter = outboxEventWriter;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AssignmentDtos.InstructorAssignmentDetailDto createAssignment(
            long courseId, Long ownerId, AssignmentDtos.CreateAssignmentRequest request) {
        requireOwnedCourse(courseId, ownerId);

        if (request.title() == null || request.title().isBlank()) {
            throw new BadRequestException("Assignment title cannot be blank");
        }

        AssignmentEntity entity = new AssignmentEntity(
                courseId,
                request.moduleId(),
                request.lessonId(),
                request.kind() != null ? request.kind() : AssignmentKind.MCQ,
                request.title().trim(),
                request.instructions() != null ? request.instructions() : "",
                request.maxPoints() != null ? request.maxPoints() : new BigDecimal("100.00")
        );
        entity.setStatus(AssignmentStatus.DRAFT);
        entity.setOpensAt(request.opensAt());
        entity.setDueAt(request.dueAt());
        entity.setTimeLimitMinutes(request.timeLimitMinutes());
        if (request.allowLate() != null) entity.setAllowLate(request.allowLate());
        if (request.allowText() != null) entity.setAllowText(request.allowText());
        if (request.allowFiles() != null) entity.setAllowFiles(request.allowFiles());
        if (request.showCorrectAnswers() != null) entity.setShowCorrectAnswers(request.showCorrectAnswers());

        entity = assignmentRepository.save(entity);

        return toDetailDto(entity, List.of());
    }

    @Transactional(readOnly = true)
    public PageResponse<AssignmentDtos.InstructorAssignmentSummaryDto> listAssignments(
            long courseId, Long ownerId, AssignmentStatus status, Pageable pageable) {
        requireOwnedCourse(courseId, ownerId);

        List<AssignmentEntity> all = (status != null)
                ? assignmentRepository.findByCourseIdAndStatusOrderByCreatedAtDesc(courseId, status)
                : assignmentRepository.findByCourseIdOrderByCreatedAtDesc(courseId);

        List<AssignmentDtos.InstructorAssignmentSummaryDto> summaries = new ArrayList<>();
        for (AssignmentEntity a : all) {
            int submitted = submissionRepository.countByAssignmentIdAndStatus(a.getId(), SubmissionStatus.SUBMITTED);
            int graded = submissionRepository.countByAssignmentIdAndStatus(a.getId(), SubmissionStatus.GRADED);
            int inProgress = submissionRepository.countByAssignmentIdAndStatus(a.getId(), SubmissionStatus.IN_PROGRESS);
            var counts = new AssignmentDtos.InstructorAssignmentSummaryDto.SubmissionCounts(submitted, graded, inProgress);

            summaries.add(new AssignmentDtos.InstructorAssignmentSummaryDto(
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
                    counts
            ));
        }

        int page = pageable.getPageNumber();
        int size = pageable.getPageSize();
        int total = summaries.size();
        int from = Math.min(page * size, total);
        int to = Math.min(from + size, total);

        return PageResponse.of(summaries.subList(from, to), page, size, total);
    }

    @Transactional(readOnly = true)
    public AssignmentDtos.InstructorAssignmentDetailDto getAssignment(long assignmentId, Long ownerId) {
        AssignmentEntity assignment = requireAssignment(assignmentId);
        requireOwnedCourse(assignment.getCourseId(), ownerId);
        List<AssignmentQuestionEntity> questions = questionRepository.findByAssignmentIdOrderByPositionAsc(assignmentId);
        return toDetailDto(assignment, questions);
    }

    @Transactional
    public AssignmentDtos.InstructorAssignmentDetailDto updateAssignment(
            long assignmentId, Long ownerId, AssignmentDtos.UpdateAssignmentRequest request) {
        AssignmentEntity assignment = requireAssignment(assignmentId);
        requireOwnedCourse(assignment.getCourseId(), ownerId);

        if (request.title() != null && !request.title().isBlank()) assignment.setTitle(request.title().trim());
        if (request.instructions() != null) assignment.setInstructions(request.instructions());
        if (request.moduleId() != null) assignment.setModuleId(request.moduleId());
        if (request.lessonId() != null) assignment.setLessonId(request.lessonId());
        if (request.opensAt() != null) assignment.setOpensAt(request.opensAt());
        if (request.dueAt() != null) assignment.setDueAt(request.dueAt());
        if (request.timeLimitMinutes() != null) assignment.setTimeLimitMinutes(request.timeLimitMinutes());
        if (request.allowLate() != null) assignment.setAllowLate(request.allowLate());
        if (request.allowText() != null) assignment.setAllowText(request.allowText());
        if (request.allowFiles() != null) assignment.setAllowFiles(request.allowFiles());
        if (request.showCorrectAnswers() != null) assignment.setShowCorrectAnswers(request.showCorrectAnswers());
        if (request.maxPoints() != null) assignment.setMaxPoints(request.maxPoints());

        assignment = assignmentRepository.save(assignment);
        List<AssignmentQuestionEntity> questions = questionRepository.findByAssignmentIdOrderByPositionAsc(assignmentId);
        return toDetailDto(assignment, questions);
    }

    @Transactional
    public AssignmentDtos.InstructorAssignmentDetailDto replaceQuestions(
            long assignmentId, Long ownerId, List<AssignmentDtos.QuestionUpsertRequest> questions) {
        AssignmentEntity assignment = requireAssignment(assignmentId);
        requireOwnedCourse(assignment.getCourseId(), ownerId);

        questionRepository.deleteByAssignmentId(assignmentId);

        List<AssignmentQuestionEntity> saved = new ArrayList<>();
        if (questions != null) {
            BigDecimal totalMaxPoints = BigDecimal.ZERO;
            for (int i = 0; i < questions.size(); i++) {
                var q = questions.get(i);
                String optionsJson = null;
                if (q.options() != null) {
                    try {
                        optionsJson = objectMapper.writeValueAsString(q.options());
                    } catch (Exception ignored) {}
                }
                AssignmentQuestionEntity entity = new AssignmentQuestionEntity(
                        assignmentId,
                        i,
                        q.type(),
                        q.prompt(),
                        q.points(),
                        optionsJson,
                        q.codeLanguage()
                );
                saved.add(questionRepository.save(entity));
                if (q.points() != null) {
                    totalMaxPoints = totalMaxPoints.add(q.points());
                }
            }
            if (assignment.getKind() == AssignmentKind.MCQ && !saved.isEmpty()) {
                assignment.setMaxPoints(totalMaxPoints);
                assignment = assignmentRepository.save(assignment);
            }
        }

        return toDetailDto(assignment, saved);
    }

    @Transactional
    public AssignmentDtos.InstructorAssignmentDetailDto publishAssignment(long assignmentId, Long ownerId) {
        AssignmentEntity assignment = requireAssignment(assignmentId);
        requireOwnedCourse(assignment.getCourseId(), ownerId);

        assignment.setStatus(AssignmentStatus.PUBLISHED);
        assignment.setPublishedAt(Instant.now());
        assignment = assignmentRepository.save(assignment);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("assignmentId", assignment.getId());
        payload.put("courseId", assignment.getCourseId());
        payload.put("title", assignment.getTitle());
        outboxEventWriter.writeEvent(
                "peergeek.assignment.assignment-published.v1",
                "AssignmentPublished",
                payload
        );

        List<AssignmentQuestionEntity> questions = questionRepository.findByAssignmentIdOrderByPositionAsc(assignmentId);
        return toDetailDto(assignment, questions);
    }

    @Transactional
    public AssignmentDtos.InstructorAssignmentDetailDto unpublishAssignment(long assignmentId, Long ownerId) {
        AssignmentEntity assignment = requireAssignment(assignmentId);
        requireOwnedCourse(assignment.getCourseId(), ownerId);

        assignment.setStatus(AssignmentStatus.CLOSED);
        assignment = assignmentRepository.save(assignment);

        List<AssignmentQuestionEntity> questions = questionRepository.findByAssignmentIdOrderByPositionAsc(assignmentId);
        return toDetailDto(assignment, questions);
    }

    @Transactional
    public void deleteAssignment(long assignmentId, Long ownerId) {
        AssignmentEntity assignment = requireAssignment(assignmentId);
        requireOwnedCourse(assignment.getCourseId(), ownerId);

        questionRepository.deleteByAssignmentId(assignmentId);
        assignmentRepository.delete(assignment);
    }

    private AssignmentEntity requireAssignment(long id) {
        return assignmentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Assignment not found: " + id));
    }

    private CourseEntity requireOwnedCourse(long courseId, Long ownerId) {
        CourseEntity course = courseRepository.findByIdAndDeletedAtIsNull(courseId)
                .orElseThrow(() -> new NotFoundException("Course not found: " + courseId));
        if (ownerId != null && !course.getOwnerId().equals(ownerId)) {
            throw new ForbiddenException("You do not own this course: " + courseId);
        }
        return course;
    }

    private AssignmentDtos.InstructorAssignmentDetailDto toDetailDto(
            AssignmentEntity a, List<AssignmentQuestionEntity> questions) {
        List<AssignmentDtos.QuestionDto> questionDtos = questions.stream()
                .map(q -> {
                    List<QuestionOption> options = List.of();
                    if (q.getOptions() != null) {
                        try {
                            options = objectMapper.readValue(q.getOptions(), new TypeReference<List<QuestionOption>>() {});
                        } catch (Exception ignored) {}
                    }
                    return new AssignmentDtos.QuestionDto(
                            q.getId(),
                            q.getPosition(),
                            q.getType(),
                            q.getPrompt(),
                            q.getPoints(),
                            options,
                            q.getCodeLanguage()
                    );
                })
                .toList();

        return new AssignmentDtos.InstructorAssignmentDetailDto(
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
                questionDtos
        );
    }
}

