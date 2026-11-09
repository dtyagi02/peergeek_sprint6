package com.peergeek.courselearning.readiness;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.peergeek.courselearning.common.ResourceStatus;
import com.peergeek.courselearning.course.CategoryEntity;
import com.peergeek.courselearning.course.CategoryRepository;
import com.peergeek.courselearning.course.CourseEntity;
import com.peergeek.courselearning.course.CourseRepository;
import com.peergeek.courselearning.course.CourseTopicEntity;
import com.peergeek.courselearning.course.CourseTopicRepository;
import com.peergeek.courselearning.exception.ConflictException;
import com.peergeek.courselearning.exception.ForbiddenException;
import com.peergeek.courselearning.exception.NotFoundException;
import com.peergeek.courselearning.lesson.LessonContentEntity;
import com.peergeek.courselearning.lesson.LessonContentRepository;
import com.peergeek.courselearning.lesson.LessonEntity;
import com.peergeek.courselearning.lesson.LessonRepository;
import com.peergeek.courselearning.module.CourseModuleEntity;
import com.peergeek.courselearning.module.CourseModuleRepository;
import com.peergeek.courselearning.resource.LessonExpectedResourceEntity;
import com.peergeek.courselearning.resource.LessonExpectedResourceRepository;
import com.peergeek.courselearning.resource.ResourceEntity;
import com.peergeek.courselearning.resource.ResourceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

@Service
public class ReadinessService {

    private final CourseRepository courseRepository;
    private final CourseTopicRepository topicRepository;
    private final CategoryRepository categoryRepository;
    private final CourseModuleRepository moduleRepository;
    private final LessonRepository lessonRepository;
    private final LessonContentRepository lessonContentRepository;
    private final ResourceRepository resourceRepository;
    private final LessonExpectedResourceRepository expectedResourceRepository;
    private final ReadinessRunRepository runRepository;
    private final ReadinessIssueRepository issueRepository;
    private final ObjectMapper objectMapper;

    public ReadinessService(
            CourseRepository courseRepository,
            CourseTopicRepository topicRepository,
            CategoryRepository categoryRepository,
            CourseModuleRepository moduleRepository,
            LessonRepository lessonRepository,
            LessonContentRepository lessonContentRepository,
            ResourceRepository resourceRepository,
            LessonExpectedResourceRepository expectedResourceRepository,
            ReadinessRunRepository runRepository,
            ReadinessIssueRepository issueRepository,
            ObjectMapper objectMapper) {
        this.courseRepository = courseRepository;
        this.topicRepository = topicRepository;
        this.categoryRepository = categoryRepository;
        this.moduleRepository = moduleRepository;
        this.lessonRepository = lessonRepository;
        this.lessonContentRepository = lessonContentRepository;
        this.resourceRepository = resourceRepository;
        this.expectedResourceRepository = expectedResourceRepository;
        this.runRepository = runRepository;
        this.issueRepository = issueRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ReadinessRunView start(long courseId, long actorId) {
        CourseEntity course = requireOwnedCourse(courseId, actorId);
        List<ReadinessIssue> issues = evaluate(course);

        int blockingCount = 0;
        int warningCount = 0;
        for (ReadinessIssue issue : issues) {
            if (issue.severity() == ReadinessIssue.Severity.BLOCKING_ERROR) {
                blockingCount++;
            } else {
                warningCount++;
            }
        }

        String fingerprint = calculateFingerprint(course);
        Instant now = Instant.now();

        ReadinessRunEntity run = new ReadinessRunEntity(courseId, actorId, fingerprint, blockingCount, warningCount);
        run.setStartedAt(now);
        run.setFinishedAt(now);
        run.setStatus("COMPLETED");
        run = runRepository.save(run);

        for (ReadinessIssue issue : issues) {
            String detailsJson = null;
            if (issue.details() != null && !issue.details().isEmpty()) {
                try {
                    detailsJson = objectMapper.writeValueAsString(issue.details());
                } catch (Exception ignored) {}
            }
            ReadinessIssueEntity entity = new ReadinessIssueEntity(
                    run.getId(),
                    issue.ruleCode(),
                    issue.severity().name(),
                    issue.scope().name(),
                    issue.message(),
                    issue.fixHint()
            );
            entity.setModuleId(issue.moduleId());
            entity.setLessonId(issue.lessonId());
            entity.setResourceId(issue.resourceId());
            entity.setModuleTitle(issue.moduleTitle());
            entity.setLessonTitle(issue.lessonTitle());
            entity.setResourceTitle(issue.resourceTitle());
            entity.setDetails(detailsJson);
            issueRepository.save(entity);
        }

        return toView(run, issues);
    }

    @Transactional(readOnly = true)
    public ReadinessRunView get(long courseId, long runId, long actorId) {
        requireOwnedCourse(courseId, actorId);
        ReadinessRunEntity run = runRepository.findById(runId)
                .filter(r -> r.getCourseId().equals(courseId))
                .orElseThrow(() -> new NotFoundException("Readiness run not found: " + runId));
        return toView(run, loadIssues(run.getId()));
    }

    @Transactional(readOnly = true)
    public ReadinessRunView getLatest(long courseId, long actorId) {
        requireOwnedCourse(courseId, actorId);
        return runRepository.findFirstByCourseIdOrderByCreatedAtDesc(courseId)
                .map(run -> toView(run, loadIssues(run.getId())))
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public void assertFreshPassingRun(long courseId, long actorId) {
        CourseEntity course = requireOwnedCourse(courseId, actorId);
        ReadinessRunEntity run = runRepository.findFirstByCourseIdOrderByCreatedAtDesc(courseId)
                .orElseThrow(() -> new ConflictException("Run a readiness check before publishing."));

        if (!"COMPLETED".equalsIgnoreCase(run.getStatus())) {
            throw new ConflictException("The latest readiness check has not completed.");
        }
        if (run.getBlockingCount() > 0) {
            throw new ConflictException("The latest readiness check contains blocking issues.");
        }

        String currentFingerprint = calculateFingerprint(course);
        if (!currentFingerprint.equals(run.getFingerprint())) {
            throw new ConflictException("The course has changed since its latest readiness check. Run readiness again.");
        }
    }

    private List<ReadinessIssue> evaluate(CourseEntity course) {
        List<ReadinessIssue> issues = new ArrayList<>();

        if (course.getTitle() == null || course.getTitle().isBlank()) {
            issues.add(new ReadinessIssue("COURSE_TITLE_REQUIRED", ReadinessIssue.Severity.BLOCKING_ERROR,
                    ReadinessIssue.Scope.COURSE, null, null, null, null, null, null,
                    "A course title is required.", "Add a course title.", null));
        }

        if (course.getDescription() == null || course.getDescription().isBlank()) {
            issues.add(new ReadinessIssue("COURSE_DESCRIPTION_REQUIRED", ReadinessIssue.Severity.BLOCKING_ERROR,
                    ReadinessIssue.Scope.COURSE, null, null, null, null, null, null,
                    "A course description is required.", "Add a course description.", null));
        } else if (course.getDescription().strip().length() < 30) {
            issues.add(new ReadinessIssue("COURSE_DESCRIPTION_SHORT", ReadinessIssue.Severity.WARNING,
                    ReadinessIssue.Scope.COURSE, null, null, null, null, null, null,
                    "The course description is brief.", "Consider adding more detail.", null));
        }

        if (course.getCategoryId() == null) {
            issues.add(new ReadinessIssue("COURSE_CATEGORY_REQUIRED", ReadinessIssue.Severity.BLOCKING_ERROR,
                    ReadinessIssue.Scope.COURSE, null, null, null, null, null, null,
                    "An active course category is required.", "Choose an active category.", null));
        } else {
            CategoryEntity cat = categoryRepository.findById(course.getCategoryId()).orElse(null);
            if (cat == null || !cat.isActive()) {
                issues.add(new ReadinessIssue("COURSE_CATEGORY_REQUIRED", ReadinessIssue.Severity.BLOCKING_ERROR,
                        ReadinessIssue.Scope.COURSE, null, null, null, null, null, null,
                        "An active course category is required.", "Choose an active category.", null));
            }
        }

        if (course.getDifficulty() == null) {
            issues.add(new ReadinessIssue("COURSE_DIFFICULTY_REQUIRED", ReadinessIssue.Severity.BLOCKING_ERROR,
                    ReadinessIssue.Scope.COURSE, null, null, null, null, null, null,
                    "A course difficulty is required.", "Set the course difficulty.", null));
        }

        List<CourseTopicEntity> topics = topicRepository.findByCourseId(course.getId());
        if (topics.isEmpty()) {
            issues.add(new ReadinessIssue("COURSE_TOPIC_REQUIRED", ReadinessIssue.Severity.BLOCKING_ERROR,
                    ReadinessIssue.Scope.COURSE, null, null, null, null, null, null,
                    "At least one course topic is required.", "Add one or more course topics.", null));
        }

        List<CourseModuleEntity> modules = moduleRepository.findByCourseIdOrderByPositionAsc(course.getId());
        if (modules.isEmpty()) {
            issues.add(new ReadinessIssue("COURSE_MODULE_REQUIRED", ReadinessIssue.Severity.BLOCKING_ERROR,
                    ReadinessIssue.Scope.COURSE, null, null, null, null, null, null,
                    "The course must contain at least one module.", "Add a module and lessons.", null));
        }

        for (CourseModuleEntity module : modules) {
            if (module.getTitle() == null || module.getTitle().isBlank()) {
                issues.add(new ReadinessIssue("MODULE_TITLE_REQUIRED", ReadinessIssue.Severity.BLOCKING_ERROR,
                        ReadinessIssue.Scope.MODULE, module.getId(), null, null, module.getTitle(), null, null,
                        "A module title is required.", "Add a title to this module.", null));
            }

            List<LessonEntity> lessons = lessonRepository.findByModuleIdOrderByPositionAsc(module.getId());
            if (lessons.isEmpty()) {
                issues.add(new ReadinessIssue("MODULE_LESSON_REQUIRED", ReadinessIssue.Severity.BLOCKING_ERROR,
                        ReadinessIssue.Scope.MODULE, module.getId(), null, null, module.getTitle(), null, null,
                        "Module has no lessons.", "Add at least one lesson to this module.", null));
            }

            for (LessonEntity lesson : lessons) {
                if (lesson.getTitle() == null || lesson.getTitle().isBlank()) {
                    issues.add(new ReadinessIssue("LESSON_TITLE_REQUIRED", ReadinessIssue.Severity.BLOCKING_ERROR,
                            ReadinessIssue.Scope.LESSON, module.getId(), lesson.getId(), null, module.getTitle(),
                            lesson.getTitle(), null, "A lesson title is required.", "Add a title to this lesson.", null));
                }

                LessonContentEntity content = lessonContentRepository.findById(lesson.getId()).orElse(null);
                if (content == null || content.getWordCount() == 0) {
                    issues.add(new ReadinessIssue("LESSON_CONTENT_REQUIRED", ReadinessIssue.Severity.BLOCKING_ERROR,
                            ReadinessIssue.Scope.LESSON, module.getId(), lesson.getId(), null, module.getTitle(),
                            lesson.getTitle(), null, "Lesson has no required authored content.",
                            "Add content to this lesson.", null));
                }

                List<LessonExpectedResourceEntity> expectedList = expectedResourceRepository.findByLessonId(lesson.getId());
                List<ResourceEntity> actualResources = resourceRepository.findByLessonIdAndDeletedAtIsNullOrderByPositionAsc(lesson.getId());

                for (LessonExpectedResourceEntity exp : expectedList) {
                    long count = actualResources.stream()
                            .filter(r -> exp.getResourceType().equalsIgnoreCase(r.getType())
                                    && ResourceStatus.READY.name().equalsIgnoreCase(r.getStatus()))
                            .count();
                    if (count < exp.getMinCount()) {
                        issues.add(new ReadinessIssue("EXPECTED_RESOURCE_MISSING", ReadinessIssue.Severity.BLOCKING_ERROR,
                                ReadinessIssue.Scope.LESSON, module.getId(), lesson.getId(), null, module.getTitle(),
                                lesson.getTitle(), null, "Expected resource requirement is not satisfied: " + exp.getLabel(),
                                "Add the expected " + exp.getResourceType().toLowerCase() + " resource.",
                                Map.of("required", exp.getMinCount(), "ready", count)));
                    }
                }

                for (ResourceEntity res : actualResources) {
                    if (ResourceStatus.FAILED.name().equalsIgnoreCase(res.getStatus())) {
                        issues.add(new ReadinessIssue("RESOURCE_NOT_READY", ReadinessIssue.Severity.BLOCKING_ERROR,
                                ReadinessIssue.Scope.RESOURCE, module.getId(), lesson.getId(), res.getId(),
                                module.getTitle(), lesson.getTitle(), res.getTitle(),
                                "Resource failed processing: " + res.getFailureReason(),
                                "Replace or remove failed resource.", null));
                    }
                }
            }
        }

        return issues;
    }

    private CourseEntity requireOwnedCourse(long courseId, long actorId) {
        CourseEntity course = courseRepository.findByIdAndDeletedAtIsNull(courseId)
                .orElseThrow(() -> new NotFoundException("Course not found: " + courseId));
        if (!course.getOwnerId().equals(actorId)) {
            throw new ForbiddenException("You do not own course: " + courseId);
        }
        return course;
    }

    private String calculateFingerprint(CourseEntity course) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String data = course.getId() + ":" + course.getVersion() + ":"
                    + (course.getContentUpdatedAt() != null ? course.getContentUpdatedAt().toEpochMilli() : 0);
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return UUID.randomUUID().toString();
        }
    }

    private ReadinessRunView toView(ReadinessRunEntity run, List<ReadinessIssue> issues) {
        return new ReadinessRunView(
                run.getId(),
                run.getCourseId(),
                run.getStatus(),
                run.getFingerprint(),
                run.getBlockingCount(),
                run.getWarningCount(),
                run.getFailureReason(),
                run.getStartedAt(),
                run.getFinishedAt(),
                issues
        );
    }

    private List<ReadinessIssue> loadIssues(long runId) {
        return issueRepository.findByRunId(runId).stream()
                .map(e -> {
                    Map<String, Object> details = Map.of();
                    if (e.getDetails() != null) {
                        try {
                            details = objectMapper.readValue(e.getDetails(), Map.class);
                        } catch (Exception ignored) {}
                    }
                    return new ReadinessIssue(
                            e.getRuleCode(),
                            ReadinessIssue.Severity.valueOf(e.getSeverity()),
                            ReadinessIssue.Scope.valueOf(e.getScope()),
                            e.getModuleId(),
                            e.getLessonId(),
                            e.getResourceId(),
                            e.getModuleTitle(),
                            e.getLessonTitle(),
                            e.getResourceTitle(),
                            e.getMessage(),
                            e.getFixHint(),
                            details
                    );
                })
                .toList();
    }
}

