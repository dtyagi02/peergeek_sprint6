package com.peergeek.courselearning.course;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.peergeek.courselearning.common.CourseStatus;
import com.peergeek.courselearning.exception.NotFoundException;
import com.peergeek.courselearning.lesson.LessonContentEntity;
import com.peergeek.courselearning.lesson.LessonContentRepository;
import com.peergeek.courselearning.lesson.LessonEntity;
import com.peergeek.courselearning.lesson.LessonRepository;
import com.peergeek.courselearning.module.CourseModuleEntity;
import com.peergeek.courselearning.module.CourseModuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class CourseStructureQueryServiceImpl implements CourseStructureQueryService {

    private final CourseRepository courseRepository;
    private final CourseModuleRepository moduleRepository;
    private final LessonRepository lessonRepository;
    private final LessonContentRepository lessonContentRepository;
    private final ObjectMapper objectMapper;

    public CourseStructureQueryServiceImpl(
            CourseRepository courseRepository,
            CourseModuleRepository moduleRepository,
            LessonRepository lessonRepository,
            LessonContentRepository lessonContentRepository,
            ObjectMapper objectMapper) {
        this.courseRepository = courseRepository;
        this.moduleRepository = moduleRepository;
        this.lessonRepository = lessonRepository;
        this.lessonContentRepository = lessonContentRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public CourseStructure getStructure(long courseId) {
        CourseEntity course = courseRepository.findByIdAndDeletedAtIsNull(courseId)
                .orElseThrow(() -> new NotFoundException("Course not found: " + courseId));

        List<CourseModuleEntity> modules = moduleRepository.findByCourseIdOrderByPositionAsc(courseId);
        List<ModuleSnapshot> moduleSnapshots = new ArrayList<>();

        for (CourseModuleEntity mod : modules) {
            List<LessonEntity> lessons = lessonRepository.findByModuleIdOrderByPositionAsc(mod.getId());
            List<LessonSnapshot> lessonSnapshots = new ArrayList<>();
            for (LessonEntity les : lessons) {
                Optional<LessonContentEntity> content = lessonContentRepository.findById(les.getId());
                boolean hasContent = content.isPresent();
                int wordCount = content.map(LessonContentEntity::getWordCount).orElse(0);
                lessonSnapshots.add(new LessonSnapshot(les.getId(), les.getTitle(), les.getSummary(), les.getPosition(), wordCount, hasContent));
            }
            moduleSnapshots.add(new ModuleSnapshot(mod.getId(), mod.getTitle(), mod.getDescription(), mod.getPosition(), lessonSnapshots));
        }

        CourseSummary summary = new CourseSummary(course.getId(), course.getOwnerId(), course.getTitle(), course.getDescription(), course.getDifficulty(), course.getStatus(), course.getVersion());
        return new CourseStructure(summary, moduleSnapshots);
    }

    @Override
    public LessonContentSnapshot getLessonContent(long lessonId) {
        LessonContentEntity entity = lessonContentRepository.findById(lessonId)
                .orElseThrow(() -> new NotFoundException("Lesson content not found: " + lessonId));
        try {
            JsonNode doc = objectMapper.readTree(entity.getContentDocument());
            return new LessonContentSnapshot(entity.getContentFormat(), doc, entity.getWordCount(), entity.getVersion(), entity.getUpdatedAt());
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse lesson content document", e);
        }
    }

    @Override
    public Long getCourseOwnerId(long courseId) {
        return courseRepository.findByIdAndDeletedAtIsNull(courseId)
                .map(CourseEntity::getOwnerId)
                .orElse(null);
    }

    @Override
    public Optional<LessonLocation> getLessonLocation(long lessonId) {
        return lessonRepository.findById(lessonId).flatMap(lesson ->
                moduleRepository.findById(lesson.getModuleId()).map(module ->
                        new LessonLocation(module.getCourseId(), module.getId(), lesson.getId())
                )
        );
    }

    @Override
    public Set<Long> findExistingLessonIds(Set<Long> lessonIds) {
        if (lessonIds == null || lessonIds.isEmpty()) return Collections.emptySet();
        return lessonRepository.findAllById(lessonIds).stream()
                .map(LessonEntity::getId)
                .collect(Collectors.toSet());
    }

    @Override
    public Optional<CourseMetadataSnapshot> getCourseMetadata(long courseId) {
        return courseRepository.findById(courseId).map(c ->
                new CourseMetadataSnapshot(
                        c.getId(),
                        c.getOwnerId(),
                        c.getTitle(),
                        c.getDescription(),
                        c.getStatus(),
                        c.getDeletedAt() != null,
                        c.getCategoryId(),
                        c.getDifficulty(),
                        c.getPublishedAt()
                )
        );
    }

    @Override
    public Map<Long, CourseMetadataSnapshot> getCourseMetadataBulk(Collection<Long> courseIds) {
        if (courseIds == null || courseIds.isEmpty()) return Collections.emptyMap();
        return courseRepository.findAllById(courseIds).stream()
                .collect(Collectors.toMap(
                        CourseEntity::getId,
                        c -> new CourseMetadataSnapshot(
                                c.getId(),
                                c.getOwnerId(),
                                c.getTitle(),
                                c.getDescription(),
                                c.getStatus(),
                                c.getDeletedAt() != null,
                                c.getCategoryId(),
                                c.getDifficulty(),
                                c.getPublishedAt()
                        )
                ));
    }

    @Override
    public boolean isPublished(long courseId) {
        return courseRepository.findByIdAndDeletedAtIsNull(courseId)
                .map(c -> c.getStatus() == CourseStatus.PUBLISHED)
                .orElse(false);
    }

    @Override
    public List<Long> listPublishedCourseIds(Long afterId, int limit) {
        return courseRepository.findAll().stream()
                .filter(c -> c.getStatus() == CourseStatus.PUBLISHED && c.getDeletedAt() == null)
                .filter(c -> afterId == null || c.getId() > afterId)
                .sorted(Comparator.comparing(CourseEntity::getId))
                .limit(limit)
                .map(CourseEntity::getId)
                .toList();
    }
}

