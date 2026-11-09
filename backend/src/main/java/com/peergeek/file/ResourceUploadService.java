package com.peergeek.file;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.peergeek.config.ResourceSettings;
import com.peergeek.course.CourseStructureQueryService;
import com.peergeek.course.LessonLocation;
import com.peergeek.exception.ConflictException;
import com.peergeek.exception.NotFoundException;
import com.peergeek.user.UserEntity;
import com.peergeek.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResourceUploadService {
	private final ResourceRepository resources;
	private final UserRepository users;
	private final CourseStructureQueryService structure;
	private final ResourceAccessService access;
	private final ResourceUploadPolicy policy;
	private final ResourceSettings settings;
	private final ObjectStorageService storage;
	private final ResourceEventPublisher events;
	private final ResourceProcessingDispatcher processing;

	public ResourceUploadService(
			ResourceRepository resources,
			UserRepository users,
			CourseStructureQueryService structure,
			ResourceAccessService access,
			ResourceUploadPolicy policy,
			ResourceSettings settings,
			ObjectStorageService storage,
			ResourceEventPublisher events,
			ResourceProcessingDispatcher processing) {
		this.resources = resources;
		this.users = users;
		this.structure = structure;
		this.access = access;
		this.policy = policy;
		this.settings = settings;
		this.storage = storage;
		this.events = events;
		this.processing = processing;
	}

	@Transactional
	public UploadCreated createLessonUpload(long lessonId, long actorId, UploadRequest request) {
		LessonLocation lesson = requireOwnedLesson(lessonId, actorId, request.type());
		ResourceUploadPolicy.ValidatedUpload validated = policy.validate(request.filename(), request.mimeType(),
				request.sizeBytes(), request.type(), ResourcePurpose.LESSON_RESOURCE);
		List<ResourceEntity> current = resources.lockByLessonAndPurpose(
				lesson.lessonId(), ResourcePurpose.LESSON_RESOURCE);
		if (current.size() >= settings.maxResourcesPerLesson()) {
			throw new ConflictException("The lesson has reached its resource limit.");
		}
		String key = "courses/" + lesson.courseId() + "/lessons/" + lessonId + "/" + UUID.randomUUID();
		String title = normalizeTitle(request.title(), validated.defaultTitle());
		ResourceEntity resource = resources.saveAndFlush(new ResourceEntity(
				lesson.courseId(), lessonId, user(actorId), ResourcePurpose.LESSON_RESOURCE,
				validated.type(), title, validated.filename(), key, validated.mimeType(),
				validated.sizeBytes(), null, current.size(), ResourceStatus.PENDING));
		events.publishAfterCommit(lesson.courseId(), lessonId, resource.getId(), ResourceChangeType.ADDED);
		return new UploadCreated(resource.getId(), uploadInstruction(resource));
	}

	@Transactional
	public UploadCreated createThumbnailUpload(long courseId, long actorId, UploadRequest request) {
		ResourceUploadPolicy.ValidatedUpload validated = policy.validate(request.filename(), request.mimeType(),
				request.sizeBytes(), ResourceType.IMAGE, ResourcePurpose.COURSE_THUMBNAIL);
		ResourceSummary summary = new ResourceSummary(null, courseId, null, ResourceType.IMAGE,
				ResourcePurpose.COURSE_THUMBNAIL, ResourceStatus.PENDING);
		access.authorize(new ResourceAccessContext(actorId, summary, ResourceAction.MANAGE),
				ResourceAccessDeniedMode.NOT_FOUND);
		String key = "courses/" + courseId + "/thumbnail/" + UUID.randomUUID();
		String title = normalizeTitle(request.title(), validated.defaultTitle());
		ResourceEntity resource = resources.saveAndFlush(new ResourceEntity(
				courseId, null, user(actorId), ResourcePurpose.COURSE_THUMBNAIL,
				ResourceType.IMAGE, title, validated.filename(), key, validated.mimeType(),
				validated.sizeBytes(), null, 0, ResourceStatus.PENDING));
		events.publishAfterCommit(courseId, null, resource.getId(), ResourceChangeType.ADDED);
		return new UploadCreated(resource.getId(), uploadInstruction(resource));
	}

	@Transactional
	public UploadInstruction refreshUploadUrl(long resourceId, long actorId) {
		ResourceEntity resource = findOwned(resourceId, actorId, ResourceAction.MANAGE);
		if (resource.getStatus() != ResourceStatus.PENDING) {
			throw new ConflictException("A new upload URL is available only while the resource is pending.");
		}
		return uploadInstruction(resource);
	}

	@Transactional
	public ResourceDto confirm(long resourceId, long actorId) {
		ResourceEntity resource = findOwned(resourceId, actorId, ResourceAction.MANAGE);
		if (resource.getStatus() != ResourceStatus.PENDING) {
			return ResourceDto.from(resource);
		}
		ObjectStorageService.ObjectHead head = storage.head(resource.getObjectKey()).orElse(null);
		if (head == null) {
			resource.markFailed("UPLOAD_MISSING");
			events.publishAfterCommit(resource.getCourseId(), resource.getLessonId(),
					resource.getId(), ResourceChangeType.FAILED);
			return ResourceDto.from(resources.save(resource));
		}
		if (head.sizeBytes() != resource.getSizeBytes()) {
			resource.markFailed("SIZE_MISMATCH");
			events.publishAfterCommit(resource.getCourseId(), resource.getLessonId(),
					resource.getId(), ResourceChangeType.FAILED);
			return ResourceDto.from(resources.save(resource));
		}
		resource.markUploaded();
		resource.markProcessing();
		ResourceEntity saved = resources.save(resource);
		processing.dispatchAfterCommit(saved.getId());
		return ResourceDto.from(saved);
	}

	@Transactional(readOnly = true)
	public ResourceDto get(long resourceId, long actorId) {
		return ResourceDto.from(findOwned(resourceId, actorId, ResourceAction.VIEW));
	}

	@Transactional(readOnly = true)
	public Map<String, Object> limits() {
		return Map.of(
				"maxBytes", Map.of(
						"PDF", policy.maximumBytes(ResourceType.PDF, ResourcePurpose.LESSON_RESOURCE),
						"DOCUMENT", policy.maximumBytes(ResourceType.DOCUMENT, ResourcePurpose.LESSON_RESOURCE),
						"IMAGE", policy.maximumBytes(ResourceType.IMAGE, ResourcePurpose.LESSON_RESOURCE),
						"VIDEO", policy.maximumBytes(ResourceType.VIDEO, ResourcePurpose.LESSON_RESOURCE),
						"THUMBNAIL", policy.maximumBytes(ResourceType.IMAGE, ResourcePurpose.COURSE_THUMBNAIL)),
				"maxResourcesPerLesson", settings.maxResourcesPerLesson(),
				"extensions", policy.limits(),
				"mimeTypes", Map.of(
						"PDF", settings.allowedPdfMimeTypes(),
						"DOCUMENT", settings.allowedDocumentMimeTypes(),
						"IMAGE", settings.allowedImageMimeTypes(),
						"VIDEO", settings.allowedVideoMimeTypes()));
	}

	private ResourceEntity findOwned(long resourceId, long actorId, ResourceAction action) {
		ResourceEntity resource = resources.findByIdAndDeletedAtIsNull(resourceId)
				.orElseThrow(() -> new NotFoundException("The requested resource was not found"));
		access.authorize(new ResourceAccessContext(actorId, ResourceSummary.from(resource), action),
				ResourceAccessDeniedMode.NOT_FOUND);
		return resource;
	}

	private LessonLocation requireOwnedLesson(long lessonId, long actorId, ResourceType type) {
		LessonLocation lesson = structure.getLessonLocation(lessonId)
				.filter(location -> !location.courseDeleted())
				.orElseThrow(() -> new NotFoundException("The requested resource was not found"));
		ResourceSummary summary = new ResourceSummary(null, lesson.courseId(), lesson.lessonId(),
				type, ResourcePurpose.LESSON_RESOURCE, ResourceStatus.PENDING);
		access.authorize(new ResourceAccessContext(actorId, summary, ResourceAction.MANAGE),
				ResourceAccessDeniedMode.NOT_FOUND);
		return lesson;
	}

	private UserEntity user(long userId) {
		return users.findById(userId)
				.orElseThrow(() -> new NotFoundException("The requested resource was not found"));
	}

	private UploadInstruction uploadInstruction(ResourceEntity resource) {
		ObjectStorageService.PresignedPut url = storage.presignPut(
				resource.getObjectKey(), resource.getMimeType(), resource.getSizeBytes(), settings.putUrlTtl());
		return new UploadInstruction(url.url(), "PUT", url.headers(), url.expiresAt());
	}

	private String normalizeTitle(String title, String fallback) {
		String value = title == null || title.isBlank() ? fallback : title.trim();
		value = value.replaceAll("[\\p{Cntrl}]", "").trim();
		if (value.isBlank() || value.length() > 255) {
			throw new com.peergeek.exception.BadRequestException("The resource title must be between 1 and 255 characters.");
		}
		return value;
	}

	public record UploadRequest(String filename, String mimeType, long sizeBytes, ResourceType type, String title) {
	}

	public record UploadCreated(Long resourceId, UploadInstruction upload) {
	}

	public record UploadInstruction(
			java.net.URI url, String method, Map<String, String> headers, Instant expiresAt) {
		public UploadInstruction {
			headers = Map.copyOf(headers);
		}
	}

	public record ResourceDto(
			Long id,
			ResourceType type,
			String title,
			String originalFilename,
			String mimeType,
			Long sizeBytes,
			ResourceStatus status,
			String failureReason,
			String externalUrl,
			int position,
			Instant createdAt) {
		static ResourceDto from(ResourceEntity resource) {
			return new ResourceDto(resource.getId(), resource.getType(), resource.getTitle(),
					resource.getOriginalFilename(), resource.getMimeType(), resource.getSizeBytes(),
					resource.getStatus(), resource.getFailureReason(), resource.getExternalUrl(),
					resource.getPosition(), resource.getCreatedAt());
		}
	}
}
