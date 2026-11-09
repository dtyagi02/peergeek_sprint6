package com.peergeek.courselearning.resource;

import com.peergeek.courselearning.common.ResourcePurpose;
import com.peergeek.courselearning.common.ResourceStatus;
import com.peergeek.courselearning.common.ResourceType;
import com.peergeek.courselearning.course.CourseEntity;
import com.peergeek.courselearning.course.CourseRepository;
import com.peergeek.courselearning.exception.BadRequestException;
import com.peergeek.courselearning.exception.ConflictException;
import com.peergeek.courselearning.exception.ForbiddenException;
import com.peergeek.courselearning.exception.NotFoundException;
import com.peergeek.courselearning.file.ObjectStorageService;
import com.peergeek.courselearning.lesson.LessonEntity;
import com.peergeek.courselearning.lesson.LessonRepository;
import com.peergeek.courselearning.module.CourseModuleEntity;
import com.peergeek.courselearning.module.CourseModuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class ResourceUploadService {

    private final ResourceRepository resourceRepository;
    private final CourseRepository courseRepository;
    private final CourseModuleRepository moduleRepository;
    private final LessonRepository lessonRepository;
    private final ObjectStorageService storageService;

    public ResourceUploadService(
            ResourceRepository resourceRepository,
            CourseRepository courseRepository,
            CourseModuleRepository moduleRepository,
            LessonRepository lessonRepository,
            ObjectStorageService storageService) {
        this.resourceRepository = resourceRepository;
        this.courseRepository = courseRepository;
        this.moduleRepository = moduleRepository;
        this.lessonRepository = lessonRepository;
        this.storageService = storageService;
    }

    @Transactional
    public ResourceRequests.UploadCreated createLessonUpload(
            long lessonId, long actorId, ResourceRequests.LessonUpload request) {
        LessonEntity lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new NotFoundException("Lesson not found: " + lessonId));
        CourseModuleEntity module = moduleRepository.findById(lesson.getModuleId())
                .orElseThrow(() -> new NotFoundException("Module not found: " + lesson.getModuleId()));
        requireOwnedCourse(module.getCourseId(), actorId);

        int position = resourceRepository.countByLessonIdAndDeletedAtIsNull(lessonId);
        validateLessonUpload(request, position);

        String objectKey = "courses/" + module.getCourseId() + "/lessons/" + lessonId + "/" + UUID.randomUUID() + "-" + sanitize(request.filename());
        String title = (request.title() != null && !request.title().isBlank()) ? request.title().trim() : request.filename();

        ResourceEntity resource = new ResourceEntity(
                module.getCourseId(),
                lessonId,
                actorId,
                ResourcePurpose.LESSON_RESOURCE.name(),
                request.type() != null ? request.type().toUpperCase() : ResourceType.DOCUMENT.name(),
                title,
                position,
                ResourceStatus.PENDING.name()
        );
        resource.setOriginalFilename(request.filename());
        resource.setMimeType(request.mimeType());
        resource.setSizeBytes(request.sizeBytes());
        resource.setObjectKey(objectKey);
        resource = resourceRepository.save(resource);

        Duration ttl = Duration.ofMinutes(15);
        String presignedPut = storageService.presignPut(objectKey, request.mimeType(), ttl);

        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", request.mimeType());

        ResourceRequests.UploadInstruction instruction = new ResourceRequests.UploadInstruction("PUT", presignedPut, headers);
        return new ResourceRequests.UploadCreated(resource.getId(), instruction);
    }

    @Transactional
    public ResourceRequests.UploadCreated createThumbnailUpload(
            long courseId, long actorId, ResourceRequests.ThumbnailUpload request) {
        requireOwnedCourse(courseId, actorId);
        validateThumbnailUpload(request);

        String objectKey = "courses/" + courseId + "/thumbnail/" + UUID.randomUUID() + "-" + sanitize(request.filename());
        String title = (request.title() != null && !request.title().isBlank()) ? request.title().trim() : "Thumbnail";

        ResourceEntity resource = new ResourceEntity(
                courseId,
                null,
                actorId,
                ResourcePurpose.COURSE_THUMBNAIL.name(),
                ResourceType.IMAGE.name(),
                title,
                0,
                ResourceStatus.PENDING.name()
        );
        resource.setOriginalFilename(request.filename());
        resource.setMimeType(request.mimeType());
        resource.setSizeBytes(request.sizeBytes());
        resource.setObjectKey(objectKey);
        resource = resourceRepository.save(resource);

        Duration ttl = Duration.ofMinutes(15);
        String presignedPut = storageService.presignPut(objectKey, request.mimeType(), ttl);

        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", request.mimeType());

        ResourceRequests.UploadInstruction instruction = new ResourceRequests.UploadInstruction("PUT", presignedPut, headers);
        return new ResourceRequests.UploadCreated(resource.getId(), instruction);
    }

    @Transactional
    public ResourceRequests.UploadInstruction refreshUploadUrl(long resourceId, long actorId) {
        ResourceEntity resource = resourceRepository.findByIdAndDeletedAtIsNull(resourceId)
                .orElseThrow(() -> new NotFoundException("Resource not found: " + resourceId));
        requireOwnedCourse(resource.getCourseId(), actorId);

        if (!ResourceStatus.PENDING.name().equalsIgnoreCase(resource.getStatus())) {
            throw new ConflictException("A new upload URL is available only while the resource is pending.");
        }

        Duration ttl = Duration.ofMinutes(15);
        String presignedPut = storageService.presignPut(resource.getObjectKey(), resource.getMimeType(), ttl);
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", resource.getMimeType());
        return new ResourceRequests.UploadInstruction("PUT", presignedPut, headers);
    }

    @Transactional
    public ResourceDto confirm(long resourceId, long actorId) {
        ResourceEntity resource = resourceRepository.findByIdAndDeletedAtIsNull(resourceId)
                .orElseThrow(() -> new NotFoundException("Resource not found: " + resourceId));
        requireOwnedCourse(resource.getCourseId(), actorId);

        if (!ResourceStatus.PENDING.name().equalsIgnoreCase(resource.getStatus())) {
            return ResourceDto.fromEntity(resource);
        }

        Optional<ObjectStorageService.ObjectHead> headOpt = storageService.head(resource.getObjectKey());
        if (headOpt.isEmpty()) {
            resource.setStatus(ResourceStatus.FAILED.name());
            resource.setFailureReason("UPLOAD_MISSING");
            resource.setUpdatedAt(Instant.now());
            return ResourceDto.fromEntity(resourceRepository.save(resource));
        }

        resource.setStatus(ResourceStatus.READY.name());
        resource.setUpdatedAt(Instant.now());
        resource = resourceRepository.save(resource);

        if (ResourcePurpose.COURSE_THUMBNAIL.name().equalsIgnoreCase(resource.getPurpose())) {
            CourseEntity course = courseRepository.findByIdAndDeletedAtIsNull(resource.getCourseId()).orElseThrow();
            course.setThumbnailKey(resource.getObjectKey());
            course.setContentUpdatedAt(Instant.now());
            courseRepository.save(course);
        } else {
            CourseEntity course = courseRepository.findByIdAndDeletedAtIsNull(resource.getCourseId()).orElse(null);
            if (course != null) {
                course.setContentUpdatedAt(Instant.now());
                courseRepository.save(course);
            }
        }

        return ResourceDto.fromEntity(resource);
    }

    @Transactional(readOnly = true)
    public ResourceDto get(long resourceId, long actorId) {
        ResourceEntity resource = resourceRepository.findByIdAndDeletedAtIsNull(resourceId)
                .orElseThrow(() -> new NotFoundException("Resource not found: " + resourceId));
        requireOwnedCourse(resource.getCourseId(), actorId);
        return ResourceDto.fromEntity(resource);
    }

    public Map<String, Object> limits() {
        return Map.of(
                "maxBytes", Map.of(
                        "PDF", 100 * 1024 * 1024L,
                        "DOCUMENT", 50 * 1024 * 1024L,
                        "IMAGE", 10 * 1024 * 1024L,
                        "VIDEO", 500 * 1024 * 1024L,
                        "THUMBNAIL", 5 * 1024 * 1024L
                ),
                "maxResourcesPerLesson", 50,
                "mimeTypes", Map.of(
                        "PDF", List.of("application/pdf"),
                        "IMAGE", List.of("image/jpeg", "image/png", "image/webp"),
                        "VIDEO", List.of("video/mp4", "video/webm")
                )
        );
    }

    private CourseEntity requireOwnedCourse(long courseId, long actorId) {
        CourseEntity course = courseRepository.findByIdAndDeletedAtIsNull(courseId)
                .orElseThrow(() -> new NotFoundException("Course not found: " + courseId));
        if (!course.getOwnerId().equals(actorId)) {
            throw new ForbiddenException("You do not own this course: " + courseId);
        }
        return course;
    }

    private void validateLessonUpload(ResourceRequests.LessonUpload request, int currentCount) {
        if (currentCount >= 50) {
            throw new BadRequestException("Maximum of 50 resources per lesson reached");
        }
        if (request.filename() == null || request.filename().isBlank()) {
            throw new BadRequestException("Filename must not be empty");
        }
        String lowerFilename = request.filename().toLowerCase();
        if (lowerFilename.endsWith(".exe") || lowerFilename.endsWith(".bat") || lowerFilename.endsWith(".sh") || lowerFilename.endsWith(".cmd")) {
            throw new BadRequestException("Executable and script files are not allowed: " + request.filename());
        }
        if (request.sizeBytes() <= 0) {
            throw new BadRequestException("File size must be positive");
        }

        String type = request.type() != null ? request.type().toUpperCase() : ResourceType.DOCUMENT.name();
        String mime = request.mimeType() != null ? request.mimeType().toLowerCase() : "";

        switch (type) {
            case "PDF" -> {
                if (request.sizeBytes() > 100 * 1024 * 1024L) {
                    throw new BadRequestException("PDF file exceeds maximum limit of 100MB");
                }
                if (!mime.equals("application/pdf")) {
                    throw new BadRequestException("Mime type must be application/pdf for PDF resources");
                }
            }
            case "IMAGE" -> {
                if (request.sizeBytes() > 10 * 1024 * 1024L) {
                    throw new BadRequestException("Image file exceeds maximum limit of 10MB");
                }
                if (!List.of("image/jpeg", "image/png", "image/webp").contains(mime)) {
                    throw new BadRequestException("Unsupported image mime type: " + mime);
                }
            }
            case "VIDEO" -> {
                if (request.sizeBytes() > 500 * 1024 * 1024L) {
                    throw new BadRequestException("Video file exceeds maximum limit of 500MB");
                }
                if (!List.of("video/mp4", "video/webm").contains(mime)) {
                    throw new BadRequestException("Unsupported video mime type: " + mime);
                }
            }
            case "DOCUMENT" -> {
                if (request.sizeBytes() > 50 * 1024 * 1024L) {
                    throw new BadRequestException("Document file exceeds maximum limit of 50MB");
                }
            }
            default -> throw new BadRequestException("Unsupported resource type: " + type);
        }
    }

    private void validateThumbnailUpload(ResourceRequests.ThumbnailUpload request) {
        if (request.filename() == null || request.filename().isBlank()) {
            throw new BadRequestException("Filename must not be empty");
        }
        if (request.sizeBytes() <= 0) {
            throw new BadRequestException("Thumbnail size must be positive");
        }
        if (request.sizeBytes() > 5 * 1024 * 1024L) {
            throw new BadRequestException("Thumbnail size exceeds maximum of 5MB");
        }
        String mime = request.mimeType() != null ? request.mimeType().toLowerCase() : "";
        if (!List.of("image/jpeg", "image/png", "image/webp").contains(mime)) {
            throw new BadRequestException("Unsupported thumbnail mime type: " + mime);
        }
    }

    private String sanitize(String filename) {
        if (filename == null) return "file";
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}

