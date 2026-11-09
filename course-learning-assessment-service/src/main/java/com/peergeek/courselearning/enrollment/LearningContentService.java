package com.peergeek.courselearning.enrollment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.peergeek.courselearning.common.CourseStatus;
import com.peergeek.courselearning.common.EnrollmentStatus;
import com.peergeek.courselearning.common.ResourceStatus;
import com.peergeek.courselearning.common.ResourceType;
import com.peergeek.courselearning.course.CategoryEntity;
import com.peergeek.courselearning.course.CategoryRepository;
import com.peergeek.courselearning.course.CourseEntity;
import com.peergeek.courselearning.course.CourseRepository;
import com.peergeek.courselearning.course.CourseTopicEntity;
import com.peergeek.courselearning.course.CourseTopicRepository;
import com.peergeek.courselearning.exception.EnrollmentRequiredException;
import com.peergeek.courselearning.exception.NotFoundException;
import com.peergeek.courselearning.file.ObjectStorageService;
import com.peergeek.courselearning.lesson.LessonContentEntity;
import com.peergeek.courselearning.lesson.LessonContentRepository;
import com.peergeek.courselearning.lesson.LessonEntity;
import com.peergeek.courselearning.lesson.LessonRepository;
import com.peergeek.courselearning.module.CourseModuleEntity;
import com.peergeek.courselearning.module.CourseModuleRepository;
import com.peergeek.courselearning.profile.LearningUserProfileEntity;
import com.peergeek.courselearning.profile.LearningUserProfileRepository;
import com.peergeek.courselearning.progress.LessonProgressEntity;
import com.peergeek.courselearning.progress.LessonProgressRepository;
import com.peergeek.courselearning.progress.ProgressService;
import com.peergeek.courselearning.resource.ResourceEntity;
import com.peergeek.courselearning.resource.ResourceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class LearningContentService {

    private final CourseRepository courseRepository;
    private final CategoryRepository categoryRepository;
    private final CourseTopicRepository topicRepository;
    private final CourseModuleRepository moduleRepository;
    private final LessonRepository lessonRepository;
    private final LessonContentRepository lessonContentRepository;
    private final ResourceRepository resourceRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final LessonProgressRepository lessonProgressRepository;
    private final LearningUserProfileRepository userProfileRepository;
    private final ProgressService progressService;
    private final ObjectStorageService storageService;
    private final ObjectMapper objectMapper;

    public LearningContentService(
            CourseRepository courseRepository,
            CategoryRepository categoryRepository,
            CourseTopicRepository topicRepository,
            CourseModuleRepository moduleRepository,
            LessonRepository lessonRepository,
            LessonContentRepository lessonContentRepository,
            ResourceRepository resourceRepository,
            EnrollmentRepository enrollmentRepository,
            LessonProgressRepository lessonProgressRepository,
            LearningUserProfileRepository userProfileRepository,
            ProgressService progressService,
            ObjectStorageService storageService,
            ObjectMapper objectMapper) {
        this.courseRepository = courseRepository;
        this.categoryRepository = categoryRepository;
        this.topicRepository = topicRepository;
        this.moduleRepository = moduleRepository;
        this.lessonRepository = lessonRepository;
        this.lessonContentRepository = lessonContentRepository;
        this.resourceRepository = resourceRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.lessonProgressRepository = lessonProgressRepository;
        this.userProfileRepository = userProfileRepository;
        this.progressService = progressService;
        this.storageService = storageService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public LearnerCourseOutlineDto getLearnerOutline(long courseId, long userId) {
        CourseEntity course = requireCourse(courseId);
        EnrollmentEntity enrollment = requireActiveEnrollment(courseId, userId);

        String catName = "";
        if (course.getCategoryId() != null) {
            catName = categoryRepository.findById(course.getCategoryId()).map(CategoryEntity::getName).orElse("");
        }

        List<String> topics = topicRepository.findByCourseId(courseId).stream()
                .map(CourseTopicEntity::getName)
                .toList();

        String instructorName = "Instructor";
        Optional<LearningUserProfileEntity> profileOpt = userProfileRepository.findById(course.getOwnerId());
        if (profileOpt.isPresent()) {
            instructorName = profileOpt.get().getDisplayName() != null ? profileOpt.get().getDisplayName() : profileOpt.get().getUsername();
        }
        InstructorSummaryDto instructor = new InstructorSummaryDto(course.getOwnerId(), instructorName);

        LearnerCourseSummaryDto summary = new LearnerCourseSummaryDto(
                course.getId(),
                course.getTitle(),
                course.getDescription(),
                catName,
                course.getDifficulty(),
                topics,
                instructor,
                course.getStatus().name()
        );

        List<LessonProgressEntity> completedList = lessonProgressRepository.findByEnrollmentId(enrollment.getId());
        Set<Long> completedLessonIds = completedList.stream().map(LessonProgressEntity::getLessonId).collect(Collectors.toSet());

        List<CourseModuleEntity> modules = moduleRepository.findByCourseIdOrderByPositionAsc(courseId);
        List<LearnerModuleDto> moduleDtos = new ArrayList<>();

        for (CourseModuleEntity mod : modules) {
            List<LessonEntity> lessons = lessonRepository.findByModuleIdOrderByPositionAsc(mod.getId());
            List<LearnerLessonSummaryDto> lessonSummaries = new ArrayList<>();
            for (LessonEntity les : lessons) {
                boolean completed = completedLessonIds.contains(les.getId());
                lessonSummaries.add(new LearnerLessonSummaryDto(
                        les.getId(),
                        les.getTitle(),
                        les.getSummary(),
                        les.getPosition(),
                        completed
                ));
            }
            moduleDtos.add(new LearnerModuleDto(
                    mod.getId(),
                    mod.getTitle(),
                    mod.getDescription(),
                    mod.getPosition(),
                    lessonSummaries
            ));
        }

        CourseProgressDto progress = progressService.getOrRecomputeProgress(enrollment);
        ContinueLessonDto continueLesson = progressService.getContinueLesson(enrollment);

        return new LearnerCourseOutlineDto(summary, moduleDtos, progress, continueLesson);
    }

    @Transactional
    public LearnerLessonDto getLearnerLesson(long courseId, long lessonId, long userId) {
        requireCourse(courseId);
        EnrollmentEntity enrollment = requireActiveEnrollment(courseId, userId);

        LessonEntity lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new NotFoundException("Lesson not found: " + lessonId));

        CourseModuleEntity module = moduleRepository.findById(lesson.getModuleId())
                .orElseThrow(() -> new NotFoundException("Module not found: " + lesson.getModuleId()));

        if (!module.getCourseId().equals(courseId)) {
            throw new NotFoundException("Lesson does not belong to course: " + courseId);
        }

        // Update last accessed lesson
        enrollment.setLastLessonId(lessonId);
        enrollment.setLastAccessedAt(Instant.now());
        enrollmentRepository.save(enrollment);

        JsonNode document = emptyDocument();
        Optional<LessonContentEntity> contentOpt = lessonContentRepository.findById(lessonId);
        if (contentOpt.isPresent()) {
            try {
                document = objectMapper.readTree(contentOpt.get().getContentDocument());
            } catch (Exception ignored) {}
        }

        List<ResourceEntity> resources = resourceRepository.findByLessonIdAndDeletedAtIsNullOrderByPositionAsc(lessonId);
        Duration urlTtl = Duration.ofMinutes(30);
        List<LearnerResourceDto> resourceDtos = new ArrayList<>();

        for (ResourceEntity res : resources) {
            if (!ResourceStatus.READY.name().equalsIgnoreCase(res.getStatus())) continue;

            String presignedUrl = null;
            String externalUrl = null;
            if (ResourceType.LINK.name().equalsIgnoreCase(res.getType())) {
                externalUrl = res.getExternalUrl();
            } else if (res.getObjectKey() != null) {
                presignedUrl = storageService.presignGet(res.getObjectKey(), urlTtl);
            }

            resourceDtos.add(new LearnerResourceDto(
                    res.getId(),
                    res.getType(),
                    res.getTitle(),
                    res.getMimeType(),
                    res.getSizeBytes(),
                    presignedUrl,
                    presignedUrl != null ? Instant.now().plus(urlTtl) : null,
                    externalUrl
            ));
        }

        boolean completed = lessonProgressRepository.findByEnrollmentIdAndLessonId(enrollment.getId(), lessonId).isPresent();

        List<Long> orderedLessons = getOrderedLessonIds(courseId);
        int idx = orderedLessons.indexOf(lessonId);
        Long prevId = (idx > 0) ? orderedLessons.get(idx - 1) : null;
        Long nextId = (idx >= 0 && idx < orderedLessons.size() - 1) ? orderedLessons.get(idx + 1) : null;

        LearnerLessonDetailDto detail = new LearnerLessonDetailDto(lesson.getId(), lesson.getTitle(), lesson.getSummary(), module.getId());
        return new LearnerLessonDto(detail, document, resourceDtos, completed, prevId, nextId);
    }

    @Transactional(readOnly = true)
    public PresignedUrlDto getFreshResourceUrl(long resourceId, String disposition, long userId) {
        ResourceEntity res = resourceRepository.findByIdAndDeletedAtIsNull(resourceId)
                .orElseThrow(() -> new NotFoundException("Resource not found: " + resourceId));

        requireActiveEnrollment(res.getCourseId(), userId);

        if (ResourceType.LINK.name().equalsIgnoreCase(res.getType())) {
            return new PresignedUrlDto(res.getExternalUrl(), Instant.now().plus(Duration.ofHours(1)));
        }

        Duration ttl = Duration.ofMinutes(30);
        String url = storageService.presignGet(res.getObjectKey(), ttl);
        return new PresignedUrlDto(url, Instant.now().plus(ttl));
    }

    @Transactional
    public void recordVisit(long courseId, long lessonId, long userId) {
        EnrollmentEntity enrollment = requireActiveEnrollment(courseId, userId);
        enrollment.setLastLessonId(lessonId);
        enrollment.setLastAccessedAt(Instant.now());
        enrollmentRepository.save(enrollment);
    }

    @Transactional(readOnly = true)
    public ContinueLessonDto getContinueLesson(long courseId, long userId) {
        EnrollmentEntity enrollment = requireActiveEnrollment(courseId, userId);
        return progressService.getContinueLesson(enrollment);
    }

    private CourseEntity requireCourse(long courseId) {
        return courseRepository.findByIdAndDeletedAtIsNull(courseId)
                .orElseThrow(() -> new NotFoundException("Course not found: " + courseId));
    }

    private EnrollmentEntity requireActiveEnrollment(long courseId, long userId) {
        CourseEntity course = requireCourse(courseId);
        if (course.getOwnerId().equals(userId)) {
            // Owner is permitted to view learner lessons in preview mode, but let's check enrollment first
        }
        return enrollmentRepository.findByUserIdAndCourseIdAndStatus(userId, courseId, EnrollmentStatus.ACTIVE)
                .orElseThrow(EnrollmentRequiredException::new);
    }

    private List<Long> getOrderedLessonIds(Long courseId) {
        List<CourseModuleEntity> modules = moduleRepository.findByCourseIdOrderByPositionAsc(courseId);
        List<Long> result = new ArrayList<>();
        for (CourseModuleEntity m : modules) {
            List<LessonEntity> lessons = lessonRepository.findByModuleIdOrderByPositionAsc(m.getId());
            lessons.forEach(l -> result.add(l.getId()));
        }
        return result;
    }

    private JsonNode emptyDocument() {
        ObjectNode doc = JsonNodeFactory.instance.objectNode();
        doc.put("type", "doc");
        ObjectNode paragraph = JsonNodeFactory.instance.objectNode();
        paragraph.put("type", "paragraph");
        doc.putArray("content").add(paragraph);
        return doc;
    }
}

