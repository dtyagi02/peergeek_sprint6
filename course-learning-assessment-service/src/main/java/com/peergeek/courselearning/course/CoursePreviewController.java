package com.peergeek.courselearning.course;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.peergeek.courselearning.common.CourseDifficulty;
import com.peergeek.courselearning.exception.NotFoundException;
import com.peergeek.courselearning.lesson.LessonContentEntity;
import com.peergeek.courselearning.lesson.LessonContentRepository;
import com.peergeek.courselearning.lesson.LessonEntity;
import com.peergeek.courselearning.lesson.LessonRepository;
import com.peergeek.courselearning.module.CourseModuleEntity;
import com.peergeek.courselearning.module.CourseModuleRepository;
import com.peergeek.courselearning.resource.ResourceEntity;
import com.peergeek.courselearning.resource.ResourceRepository;
import com.peergeek.courselearning.security.CurrentUserProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1/instructor/courses/{courseId}/preview")
public class CoursePreviewController {

    private final CourseRepository courseRepository;
    private final CategoryRepository categoryRepository;
    private final CourseTopicRepository topicRepository;
    private final CourseModuleRepository moduleRepository;
    private final LessonRepository lessonRepository;
    private final LessonContentRepository lessonContentRepository;
    private final ResourceRepository resourceRepository;
    private final ObjectMapper objectMapper;
    private final CurrentUserProvider currentUserProvider;

    public CoursePreviewController(
            CourseRepository courseRepository,
            CategoryRepository categoryRepository,
            CourseTopicRepository topicRepository,
            CourseModuleRepository moduleRepository,
            LessonRepository lessonRepository,
            LessonContentRepository lessonContentRepository,
            ResourceRepository resourceRepository,
            ObjectMapper objectMapper,
            CurrentUserProvider currentUserProvider) {
        this.courseRepository = courseRepository;
        this.categoryRepository = categoryRepository;
        this.topicRepository = topicRepository;
        this.moduleRepository = moduleRepository;
        this.lessonRepository = lessonRepository;
        this.lessonContentRepository = lessonContentRepository;
        this.resourceRepository = resourceRepository;
        this.objectMapper = objectMapper;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    public ResponseEntity<CoursePreview> getCoursePreview(@PathVariable("courseId") Long courseId) {
        currentUserProvider.requireCurrentUser();

        CourseEntity course = courseRepository.findByIdAndDeletedAtIsNull(courseId)
                .orElseThrow(() -> new NotFoundException("Course not found: " + courseId));

        String categoryName = course.getCategoryId() != null
                ? categoryRepository.findById(course.getCategoryId()).map(CategoryEntity::getName).orElse(null)
                : null;

        List<String> topics = topicRepository.findByCourseIdOrderByPositionAsc(courseId).stream()
                .map(CourseTopicEntity::getName)
                .toList();

        List<CourseModuleEntity> modules = moduleRepository.findByCourseIdOrderByPositionAsc(courseId);
        List<CoursePreview.Module> previewModules = new ArrayList<>();

        for (CourseModuleEntity mod : modules) {
            List<LessonEntity> lessons = lessonRepository.findByModuleIdOrderByPositionAsc(mod.getId());
            List<CoursePreview.Lesson> previewLessons = new ArrayList<>();
            for (LessonEntity les : lessons) {
                var doc = lessonContentRepository.findById(les.getId())
                        .map(c -> {
                            try {
                                return objectMapper.readTree(c.getContentDocument());
                            } catch (Exception e) {
                                return objectMapper.createObjectNode();
                            }
                        })
                        .orElse(objectMapper.createObjectNode());

                List<ResourceEntity> resEntities = resourceRepository.findByLessonIdAndDeletedAtIsNullOrderByPositionAsc(les.getId());
                List<CoursePreview.Resource> previewResources = resEntities.stream().map(r ->
                        new CoursePreview.Resource(
                                r.getId(),
                                r.getType(),
                                r.getTitle(),
                                r.getMimeType(),
                                r.getSizeBytes(),
                                null,
                                r.getExternalUrl() != null ? URI.create(r.getExternalUrl()) : null
                        )
                ).toList();

                previewLessons.add(new CoursePreview.Lesson(les.getId(), les.getTitle(), les.getSummary(), doc, previewResources));
            }
            previewModules.add(new CoursePreview.Module(mod.getId(), mod.getTitle(), mod.getDescription(), previewLessons));
        }

        CoursePreview preview = new CoursePreview(
                course.getId(),
                course.getTitle(),
                course.getDescription(),
                categoryName,
                course.getDifficulty(),
                topics,
                previewModules
        );

        return ResponseEntity.ok(preview);
    }
}

