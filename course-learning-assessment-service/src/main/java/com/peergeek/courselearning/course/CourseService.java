package com.peergeek.courselearning.course;

import com.peergeek.courselearning.common.CourseDifficulty;
import com.peergeek.courselearning.common.CourseStatus;
import com.peergeek.courselearning.exception.BadRequestException;
import com.peergeek.courselearning.exception.ForbiddenException;
import com.peergeek.courselearning.exception.NotFoundException;
import com.peergeek.courselearning.lesson.LessonContentEntity;
import com.peergeek.courselearning.lesson.LessonContentRepository;
import com.peergeek.courselearning.lesson.LessonEntity;
import com.peergeek.courselearning.lesson.LessonRepository;
import com.peergeek.courselearning.module.CourseModuleEntity;
import com.peergeek.courselearning.module.CourseModuleRepository;
import com.peergeek.courselearning.outbox.OutboxEventWriter;
import com.peergeek.courselearning.readiness.ReadinessRunEntity;
import com.peergeek.courselearning.readiness.ReadinessRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class CourseService {

    private static final Logger log = LoggerFactory.getLogger(CourseService.class);

    private final CourseRepository courseRepository;
    private final CourseTopicRepository topicRepository;
    private final CategoryRepository categoryRepository;
    private final CourseModuleRepository moduleRepository;
    private final LessonRepository lessonRepository;
    private final LessonContentRepository lessonContentRepository;
    private final ReadinessRunRepository readinessRunRepository;
    private final OutboxEventWriter outboxEventWriter;

    public CourseService(
            CourseRepository courseRepository,
            CourseTopicRepository topicRepository,
            CategoryRepository categoryRepository,
            CourseModuleRepository moduleRepository,
            LessonRepository lessonRepository,
            LessonContentRepository lessonContentRepository,
            ReadinessRunRepository readinessRunRepository,
            OutboxEventWriter outboxEventWriter) {
        this.courseRepository = courseRepository;
        this.topicRepository = topicRepository;
        this.categoryRepository = categoryRepository;
        this.moduleRepository = moduleRepository;
        this.lessonRepository = lessonRepository;
        this.lessonContentRepository = lessonContentRepository;
        this.readinessRunRepository = readinessRunRepository;
        this.outboxEventWriter = outboxEventWriter;
    }

    @Transactional
    public CourseDto createCourse(Long ownerId, CourseCreateRequest request) {
        if (request.title() == null || request.title().isBlank()) {
            throw new BadRequestException("Course title cannot be blank");
        }

        CourseEntity course = new CourseEntity(
                ownerId,
                request.title().trim(),
                request.description(),
                request.categoryId(),
                request.difficulty() != null ? request.difficulty() : CourseDifficulty.BEGINNER
        );
        course = courseRepository.save(course);

        if (request.topics() != null && !request.topics().isEmpty()) {
            int pos = 0;
            for (String t : request.topics()) {
                if (t != null && !t.isBlank()) {
                    topicRepository.save(new CourseTopicEntity(course.getId(), pos++, t.trim()));
                }
            }
        }

        log.info("Created course id={} by ownerId={}", course.getId(), ownerId);
        return toDto(course);
    }

    @Transactional(readOnly = true)
    public List<CourseListItem> getInstructorCourses(Long ownerId) {
        return courseRepository.findByOwnerIdAndDeletedAtIsNullOrderByCreatedAtDesc(ownerId)
                .stream()
                .map(this::toDto)
                .map(CourseListItem::new)
                .toList();
    }

    @Transactional(readOnly = true)
    public CourseDto getCourseById(Long courseId, Long currentUserId) {
        CourseEntity course = courseRepository.findByIdAndDeletedAtIsNull(courseId)
                .orElseThrow(() -> new NotFoundException("Course not found: " + courseId));
        return toDto(course);
    }

    @Transactional
    public CourseDto updateCourse(Long courseId, Long ownerId, CoursePatchRequest request) {
        CourseEntity course = findOwnedCourse(courseId, ownerId);

        if (request.title() != null) {
            if (request.title().isBlank()) {
                throw new BadRequestException("Course title cannot be blank");
            }
            course.setTitle(request.title().trim());
        }
        if (request.description() != null) {
            course.setDescription(request.description());
        }
        if (request.categoryId() != null) {
            course.setCategoryId(request.categoryId());
        }
        if (request.difficulty() != null) {
            course.setDifficulty(request.difficulty());
        }

        if (request.topics() != null) {
            topicRepository.deleteByCourseId(course.getId());
            int pos = 0;
            for (String t : request.topics()) {
                if (t != null && !t.isBlank()) {
                    topicRepository.save(new CourseTopicEntity(course.getId(), pos++, t.trim()));
                }
            }
        }

        course = courseRepository.save(course);
        return toDto(course);
    }

    @Transactional
    public void deleteCourse(Long courseId, Long ownerId) {
        CourseEntity course = findOwnedCourse(courseId, ownerId);
        course.setDeletedAt(Instant.now());
        courseRepository.save(course);
        log.info("Soft deleted course id={} by ownerId={}", courseId, ownerId);
    }

    @Transactional(readOnly = true)
    public CourseOutline getCourseOutline(Long courseId, Long currentUserId) {
        CourseEntity course = courseRepository.findByIdAndDeletedAtIsNull(courseId)
                .orElseThrow(() -> new NotFoundException("Course not found: " + courseId));

        List<CourseModuleEntity> modules = moduleRepository.findByCourseIdOrderByPositionAsc(courseId);
        List<CourseOutline.ModuleOutline> moduleOutlines = new ArrayList<>();

        for (CourseModuleEntity mod : modules) {
            List<LessonEntity> lessons = lessonRepository.findByModuleIdOrderByPositionAsc(mod.getId());
            List<CourseOutline.LessonOutline> lessonOutlines = new ArrayList<>();
            for (LessonEntity les : lessons) {
                Optional<LessonContentEntity> content = lessonContentRepository.findById(les.getId());
                boolean hasContent = content.isPresent();
                int wordCount = content.map(LessonContentEntity::getWordCount).orElse(0);
                lessonOutlines.add(new CourseOutline.LessonOutline(
                        les.getId(), les.getTitle(), les.getSummary(), les.getPosition(), hasContent, wordCount
                ));
            }
            moduleOutlines.add(new CourseOutline.ModuleOutline(
                    mod.getId(), mod.getTitle(), mod.getDescription(), mod.getPosition(), lessonOutlines
            ));
        }

        return new CourseOutline(course.getId(), moduleOutlines);
    }

    @Transactional
    public CourseDto publishCourse(Long courseId, Long ownerId) {
        CourseEntity course = findOwnedCourse(courseId, ownerId);
        if (course.getStatus() == CourseStatus.PUBLISHED) {
            return toDto(course);
        }

        // Verify readiness run passes with zero blocking issues
        Optional<ReadinessRunEntity> latestRun = readinessRunRepository.findFirstByCourseIdOrderByCreatedAtDesc(courseId);
        if (latestRun.isPresent() && latestRun.get().getBlockingCount() > 0) {
            throw new BadRequestException("Course cannot be published: readiness check found " +
                    latestRun.get().getBlockingCount() + " blocking issues");
        }

        course.setStatus(CourseStatus.PUBLISHED);
        course.setPublishedAt(Instant.now());
        course = courseRepository.save(course);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("courseId", course.getId());
        payload.put("ownerId", course.getOwnerId());
        payload.put("instructorId", course.getOwnerId());
        payload.put("title", course.getTitle());
        payload.put("publishedAt", course.getPublishedAt().toString());

        outboxEventWriter.writeEvent(
                "peergeek.course.course-published.v1",
                "CoursePublished",
                payload
        );

        log.info("Course id={} published by ownerId={}", courseId, ownerId);
        return toDto(course);
    }

    @Transactional
    public CourseDto unpublishCourse(Long courseId, Long ownerId) {
        CourseEntity course = findOwnedCourse(courseId, ownerId);
        if (course.getStatus() != CourseStatus.PUBLISHED) {
            return toDto(course);
        }

        course.setStatus(CourseStatus.DRAFT);
        course = courseRepository.save(course);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("courseId", course.getId());
        payload.put("ownerId", course.getOwnerId());
        payload.put("instructorId", course.getOwnerId());
        payload.put("title", course.getTitle());
        payload.put("unpublishedAt", Instant.now().toString());

        outboxEventWriter.writeEvent(
                "peergeek.course.course-unpublished.v1",
                "CourseUnpublished",
                payload
        );

        log.info("Course id={} unpublished by ownerId={}", courseId, ownerId);
        return toDto(course);
    }

    @Transactional
    public CourseDto updateCourseStatusByAdmin(Long courseId, CourseStatus status) {
        CourseEntity course = courseRepository.findByIdAndDeletedAtIsNull(courseId)
                .orElseThrow(() -> new NotFoundException("Course not found: " + courseId));
        course.setStatus(status);
        if (status == CourseStatus.PUBLISHED && course.getPublishedAt() == null) {
            course.setPublishedAt(Instant.now());
        }
        course = courseRepository.save(course);
        log.info("Admin updated course id={} status to {}", courseId, status);
        return toDto(course);
    }

    private CourseEntity findOwnedCourse(Long courseId, Long ownerId) {
        CourseEntity course = courseRepository.findByIdAndDeletedAtIsNull(courseId)
                .orElseThrow(() -> new NotFoundException("Course not found: " + courseId));
        if (!course.getOwnerId().equals(ownerId)) {
            throw new ForbiddenException("You are not the owner of this course");
        }
        return course;
    }

    public CourseDto toDto(CourseEntity course) {
        CategoryDto categoryDto = null;
        if (course.getCategoryId() != null) {
            categoryDto = categoryRepository.findById(course.getCategoryId())
                    .map(CategoryDto::fromEntity)
                    .orElse(null);
        }

        List<String> topics = topicRepository.findByCourseIdOrderByPositionAsc(course.getId())
                .stream()
                .map(CourseTopicEntity::getName)
                .toList();

        int moduleCount = moduleRepository.countByCourseId(course.getId());
        int lessonCount = lessonRepository.countByCourseId(course.getId());

        return new CourseDto(
                course.getId(),
                course.getOwnerId(),
                course.getTitle(),
                course.getDescription(),
                categoryDto,
                course.getDifficulty(),
                course.getStatus(),
                topics,
                moduleCount,
                lessonCount,
                0L,
                course.getContentUpdatedAt(),
                course.getCreatedAt(),
                course.getUpdatedAt(),
                course.getVersion()
        );
    }
}

