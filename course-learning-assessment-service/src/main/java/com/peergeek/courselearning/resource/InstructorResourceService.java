package com.peergeek.courselearning.resource;

import com.peergeek.courselearning.common.ResourcePurpose;
import com.peergeek.courselearning.common.ResourceStatus;
import com.peergeek.courselearning.common.ResourceType;
import com.peergeek.courselearning.course.CourseEntity;
import com.peergeek.courselearning.course.CourseRepository;
import com.peergeek.courselearning.exception.BadRequestException;
import com.peergeek.courselearning.exception.ForbiddenException;
import com.peergeek.courselearning.exception.NotFoundException;
import com.peergeek.courselearning.file.ObjectStorageService;
import com.peergeek.courselearning.lesson.LessonEntity;
import com.peergeek.courselearning.lesson.LessonRepository;
import com.peergeek.courselearning.module.CourseModuleEntity;
import com.peergeek.courselearning.module.CourseModuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class InstructorResourceService {

    private final ResourceRepository resourceRepository;
    private final LessonExpectedResourceRepository expectedRepository;
    private final CourseRepository courseRepository;
    private final CourseModuleRepository moduleRepository;
    private final LessonRepository lessonRepository;
    private final ObjectStorageService storageService;

    public InstructorResourceService(
            ResourceRepository resourceRepository,
            LessonExpectedResourceRepository expectedRepository,
            CourseRepository courseRepository,
            CourseModuleRepository moduleRepository,
            LessonRepository lessonRepository,
            ObjectStorageService storageService) {
        this.resourceRepository = resourceRepository;
        this.expectedRepository = expectedRepository;
        this.courseRepository = courseRepository;
        this.moduleRepository = moduleRepository;
        this.lessonRepository = lessonRepository;
        this.storageService = storageService;
    }

    @Transactional(readOnly = true)
    public List<ResourceRequests.ResourceSnapshot> list(long lessonId, long actorId) {
        requireOwnedLesson(lessonId, actorId);
        return resourceRepository.findByLessonIdAndDeletedAtIsNullOrderByPositionAsc(lessonId)
                .stream()
                .map(ResourceRequests.ResourceSnapshot::from)
                .toList();
    }

    @Transactional
    public ResourceDto createLink(long lessonId, long actorId, ResourceRequests.Link request) {
        LessonEntity lesson = requireOwnedLesson(lessonId, actorId);
        CourseModuleEntity module = moduleRepository.findById(lesson.getModuleId()).orElseThrow();
        Long courseId = module.getCourseId();

        if (request.title() == null || request.title().isBlank()) {
            throw new BadRequestException("Link title cannot be blank");
        }
        if (request.url() == null || request.url().isBlank()) {
            throw new BadRequestException("Link URL cannot be blank");
        }

        int position = resourceRepository.countByLessonIdAndDeletedAtIsNull(lessonId);
        ResourceEntity resource = new ResourceEntity(
                courseId,
                lessonId,
                actorId,
                ResourcePurpose.LESSON_RESOURCE.name(),
                ResourceType.LINK.name(),
                request.title().trim(),
                position,
                ResourceStatus.READY.name()
        );
        resource.setExternalUrl(request.url().trim());
        resource = resourceRepository.save(resource);

        touchCourse(courseId);
        return ResourceDto.fromEntity(resource);
    }

    @Transactional
    public ResourceDto update(long resourceId, long actorId, ResourceRequests.Update request) {
        ResourceEntity resource = resourceRepository.findByIdAndDeletedAtIsNull(resourceId)
                .orElseThrow(() -> new NotFoundException("Resource not found: " + resourceId));
        requireOwnedCourse(resource.getCourseId(), actorId);

        boolean changed = false;
        if (request.title() != null && !request.title().isBlank()) {
            resource.setTitle(request.title().trim());
            changed = true;
        }
        if (request.url() != null && !request.url().isBlank()) {
            if (!ResourceType.LINK.name().equalsIgnoreCase(resource.getType())) {
                throw new BadRequestException("Only link resources can change their URL.");
            }
            resource.setExternalUrl(request.url().trim());
            changed = true;
        }
        if (!changed) {
            throw new BadRequestException("At least one resource field must be supplied.");
        }

        resource.setUpdatedAt(Instant.now());
        resource = resourceRepository.save(resource);

        touchCourse(resource.getCourseId());
        return ResourceDto.fromEntity(resource);
    }

    @Transactional
    public void delete(long resourceId, long actorId) {
        ResourceEntity resource = resourceRepository.findByIdAndDeletedAtIsNull(resourceId)
                .orElseThrow(() -> new NotFoundException("Resource not found: " + resourceId));
        requireOwnedCourse(resource.getCourseId(), actorId);

        resource.setDeletedAt(Instant.now());
        resourceRepository.save(resource);

        if (resource.getObjectKey() != null) {
            try {
                storageService.deleteObject(resource.getObjectKey());
            } catch (Exception ignored) {}
        }

        touchCourse(resource.getCourseId());
    }

    @Transactional
    public void reorder(long lessonId, long actorId, List<Long> resourceIds) {
        requireOwnedLesson(lessonId, actorId);
        if (resourceIds == null || resourceIds.isEmpty()) return;

        List<ResourceEntity> existing = resourceRepository.findByLessonIdAndDeletedAtIsNullOrderByPositionAsc(lessonId);
        Map<Long, ResourceEntity> byId = new HashMap<>();
        existing.forEach(r -> byId.put(r.getId(), r));

        for (int i = 0; i < resourceIds.size(); i++) {
            ResourceEntity res = byId.get(resourceIds.get(i));
            if (res != null) {
                res.setPosition(i);
                resourceRepository.save(res);
            }
        }
    }

    @Transactional(readOnly = true)
    public List<ResourceRequests.ExpectedResourceSnapshot> listExpected(long lessonId, long actorId) {
        requireOwnedLesson(lessonId, actorId);
        return expectedRepository.findByLessonId(lessonId)
                .stream()
                .map(ResourceRequests.ExpectedResourceSnapshot::from)
                .toList();
    }

    @Transactional
    public List<ResourceRequests.ExpectedResourceSnapshot> replaceExpected(
            long lessonId, long actorId, List<ResourceRequests.Expected> request) {
        requireOwnedLesson(lessonId, actorId);
        expectedRepository.deleteByLessonId(lessonId);

        List<LessonExpectedResourceEntity> created = new ArrayList<>();
        if (request != null) {
            for (ResourceRequests.Expected item : request) {
                LessonExpectedResourceEntity entity = new LessonExpectedResourceEntity(
                        lessonId,
                        item.resourceType(),
                        item.minCount(),
                        item.label()
                );
                created.add(expectedRepository.save(entity));
            }
        }
        return created.stream().map(ResourceRequests.ExpectedResourceSnapshot::from).toList();
    }

    @Transactional(readOnly = true)
    public ResourceRequests.DownloadUrl downloadUrl(long resourceId, long actorId) {
        ResourceEntity resource = resourceRepository.findByIdAndDeletedAtIsNull(resourceId)
                .orElseThrow(() -> new NotFoundException("Resource not found: " + resourceId));
        requireOwnedCourse(resource.getCourseId(), actorId);

        if (ResourceType.LINK.name().equalsIgnoreCase(resource.getType())) {
            return new ResourceRequests.DownloadUrl(resource.getExternalUrl(), Instant.now().plus(Duration.ofHours(1)));
        }

        Duration ttl = Duration.ofMinutes(15);
        String presigned = storageService.presignGet(resource.getObjectKey(), ttl);
        return new ResourceRequests.DownloadUrl(presigned, Instant.now().plus(ttl));
    }

    @Transactional(readOnly = true)
    public ResourceRequests.ThumbnailDto getThumbnail(long courseId, long actorId) {
        CourseEntity course = requireOwnedCourse(courseId, actorId);
        if (course.getThumbnailKey() == null) {
            throw new NotFoundException("Thumbnail not found for course " + courseId);
        }

        Duration ttl = Duration.ofMinutes(30);
        String presigned = storageService.presignGet(course.getThumbnailKey(), ttl);
        return new ResourceRequests.ThumbnailDto(null, "READY", presigned, Instant.now().plus(ttl));
    }

    @Transactional
    public void deleteThumbnail(long courseId, long actorId) {
        CourseEntity course = requireOwnedCourse(courseId, actorId);
        if (course.getThumbnailKey() != null) {
            try {
                storageService.deleteObject(course.getThumbnailKey());
            } catch (Exception ignored) {}
            course.setThumbnailKey(null);
            course.setContentUpdatedAt(Instant.now());
            courseRepository.save(course);
        }
    }

    private LessonEntity requireOwnedLesson(long lessonId, long actorId) {
        LessonEntity lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new NotFoundException("Lesson not found: " + lessonId));
        CourseModuleEntity module = moduleRepository.findById(lesson.getModuleId())
                .orElseThrow(() -> new NotFoundException("Module not found: " + lesson.getModuleId()));
        requireOwnedCourse(module.getCourseId(), actorId);
        return lesson;
    }

    private CourseEntity requireOwnedCourse(long courseId, long actorId) {
        CourseEntity course = courseRepository.findByIdAndDeletedAtIsNull(courseId)
                .orElseThrow(() -> new NotFoundException("Course not found: " + courseId));
        if (!course.getOwnerId().equals(actorId)) {
            throw new ForbiddenException("You do not own this course: " + courseId);
        }
        return course;
    }

    private void touchCourse(long courseId) {
        courseRepository.findByIdAndDeletedAtIsNull(courseId).ifPresent(c -> {
            c.setContentUpdatedAt(Instant.now());
            courseRepository.save(c);
        });
    }
}

