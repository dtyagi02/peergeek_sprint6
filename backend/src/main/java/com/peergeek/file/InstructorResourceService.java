package com.peergeek.file;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.peergeek.audit.AuditEvent;
import com.peergeek.audit.AuditService;
import com.peergeek.course.CourseStructureQueryService;
import com.peergeek.course.LessonLocation;
import com.peergeek.config.ResourceSettings;
import com.peergeek.exception.BadRequestException;
import com.peergeek.exception.ConflictException;
import com.peergeek.exception.NotFoundException;
import com.peergeek.user.UserEntity;
import com.peergeek.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InstructorResourceService {
	private final ResourceRepository resources;
	private final LessonExpectedResourceRepository expected;
	private final UserRepository users;
	private final CourseStructureQueryService structure;
	private final LessonResourceQueryService queries;
	private final ResourceAccessService access;
	private final ResourceEventPublisher events;
	private final ResourceDownloadService downloads;
	private final ResourceSettings settings;
	private final AuditService audit;

	public InstructorResourceService(
			ResourceRepository resources,
			LessonExpectedResourceRepository expected,
			UserRepository users,
			CourseStructureQueryService structure,
			LessonResourceQueryService queries,
			ResourceAccessService access,
			ResourceEventPublisher events,
			ResourceDownloadService downloads,
			ResourceSettings settings,
			AuditService audit) {
		this.resources = resources;
		this.expected = expected;
		this.users = users;
		this.structure = structure;
		this.queries = queries;
		this.access = access;
		this.events = events;
		this.downloads = downloads;
		this.settings = settings;
		this.audit = audit;
	}

	@Transactional(readOnly = true)
	public List<LessonResourceQueryService.ResourceSnapshot> list(long lessonId, long actorId) {
		requireOwnedLesson(lessonId, actorId, ResourceType.LINK);
		return queries.listResources(lessonId);
	}

	@Transactional
	public ResourceUploadService.ResourceDto createLink(long lessonId, long actorId, LinkRequest request) {
		LessonLocation lesson = requireOwnedLesson(lessonId, actorId, ResourceType.LINK);
		String title = validateTitle(request.title());
		String url = validateExternalUrl(request.url());
		List<ResourceEntity> current = resources.lockByLessonAndPurpose(
				lesson.lessonId(), ResourcePurpose.LESSON_RESOURCE);
		if (current.size() >= settings.maxResourcesPerLesson()) {
			throw new ConflictException("The lesson has reached its resource limit.");
		}
		ResourceEntity resource = resources.saveAndFlush(new ResourceEntity(
				lesson.courseId(), lessonId, user(actorId), ResourcePurpose.LESSON_RESOURCE,
				ResourceType.LINK, title, null, null, null, null, url, current.size(), ResourceStatus.READY));
		audit.record(new AuditEvent(actorId, ResourceAuditActions.RESOURCE_ADDED, "resource",
				Long.toString(resource.getId()),
				Map.of("courseId", lesson.courseId(), "type", ResourceType.LINK.name(), "title", title)));
		events.publishAfterCommit(lesson.courseId(), lessonId, resource.getId(), ResourceChangeType.ADDED);
		return ResourceUploadService.ResourceDto.from(resource);
	}

	@Transactional
	public ResourceUploadService.ResourceDto update(long resourceId, long actorId, UpdateRequest request) {
		ResourceEntity resource = findOwned(resourceId, actorId, ResourceAction.MANAGE);
		boolean changed = false;
		if (request.title() != null) {
			resource.rename(validateTitle(request.title()));
			changed = true;
		}
		if (request.url() != null) {
			if (resource.getType() != ResourceType.LINK) {
				throw new BadRequestException("Only link resources can change their URL.");
			}
			resource.updateExternalUrl(validateExternalUrl(request.url()));
			changed = true;
		}
		if (!changed) {
			throw new BadRequestException("At least one resource field must be supplied.");
		}
		ResourceEntity saved = resources.save(resource);
		events.publishAfterCommit(saved.getCourseId(), saved.getLessonId(), saved.getId(), ResourceChangeType.UPDATED);
		return ResourceUploadService.ResourceDto.from(saved);
	}

	@Transactional
	public void delete(long resourceId, long actorId) {
		ResourceEntity resource = findOwned(resourceId, actorId, ResourceAction.MANAGE);
		if (resource.getLessonId() == null) {
			throw new BadRequestException("Course thumbnails must be removed through the course thumbnail endpoint.");
		}
		List<ResourceEntity> ordered = resources.lockByLessonAndPurpose(
				resource.getLessonId(), ResourcePurpose.LESSON_RESOURCE);
		resource.softDelete(java.time.Instant.now());
		for (int index = 0, position = 0; index < ordered.size(); index++) {
			ResourceEntity candidate = ordered.get(index);
			if (!candidate.getId().equals(resourceId)) {
				candidate.changePosition(position++);
			}
		}
		audit.record(new AuditEvent(actorId, ResourceAuditActions.RESOURCE_DELETED,
				"resource", Long.toString(resourceId),
				Map.of("courseId", resource.getCourseId(), "type", resource.getType().name())));
		events.publishAfterCommit(resource.getCourseId(), resource.getLessonId(),
				resourceId, ResourceChangeType.DELETED);
	}

	@Transactional
	public void reorder(long lessonId, long actorId, List<Long> resourceIds) {
		LessonLocation lesson = requireOwnedLesson(lessonId, actorId, ResourceType.LINK);
		List<ResourceEntity> ordered = resources.lockByLessonAndPurpose(lessonId, ResourcePurpose.LESSON_RESOURCE);
		Set<Long> expectedIds = ordered.stream().map(ResourceEntity::getId).collect(java.util.stream.Collectors.toSet());
		Set<Long> requestedIds = new HashSet<>(resourceIds);
		if (requestedIds.size() != resourceIds.size() || !requestedIds.equals(expectedIds)) {
			throw new BadRequestException("The resource IDs must be the exact current set without duplicates.");
		}
		Map<Long, ResourceEntity> byId = ordered.stream()
				.collect(java.util.stream.Collectors.toMap(ResourceEntity::getId, resource -> resource));
		for (int index = 0; index < resourceIds.size(); index++) {
			ResourceEntity resource = byId.get(resourceIds.get(index));
			if (resource.getPosition() != index) {
				resource.changePosition(index);
				events.publishAfterCommit(lesson.courseId(), lessonId, resource.getId(), ResourceChangeType.REORDERED);
			}
		}
	}

	@Transactional(readOnly = true)
	public List<LessonResourceQueryService.ExpectedResourceSnapshot> listExpected(long lessonId, long actorId) {
		requireOwnedLesson(lessonId, actorId, ResourceType.LINK);
		return queries.listExpected(lessonId);
	}

	@Transactional
	public List<LessonResourceQueryService.ExpectedResourceSnapshot> replaceExpected(
			long lessonId, long actorId, List<ExpectedRequest> entries) {
		LessonLocation lesson = requireOwnedLesson(lessonId, actorId, ResourceType.LINK);
		Set<ResourceType> types = new HashSet<>();
		for (ExpectedRequest entry : entries) {
			if (entry.minCount() < 1 || entry.minCount() > 20) {
				throw new BadRequestException("Expected resources require a minimum count from 1 to 20.");
			}
			if (!types.add(entry.resourceType())) {
				throw new BadRequestException("Expected resource types must be unique.");
			}
			if (entry.label() != null && entry.label().length() > 80) {
				throw new BadRequestException("Expected resource labels must be at most 80 characters.");
			}
		}
		expected.deleteByLessonId(lessonId);
		for (ExpectedRequest entry : entries) {
			expected.save(new LessonExpectedResourceEntity(
					lessonId, entry.resourceType(), entry.minCount(),
					entry.label() == null || entry.label().isBlank() ? null : entry.label().trim()));
		}
		events.publishAfterCommit(lesson.courseId(), lessonId, null, ResourceChangeType.EXPECTATIONS_CHANGED);
		return queries.listExpected(lessonId);
	}

	@Transactional(readOnly = true)
	public DownloadUrl downloadUrl(long resourceId, long actorId) {
		ResourceEntity resource = findOwned(resourceId, actorId, ResourceAction.DOWNLOAD);
		if (resource.getStatus() != ResourceStatus.READY) {
			throw new ConflictException("The resource is not ready to download.");
		}
		if (resource.getType() == ResourceType.LINK) {
			return new DownloadUrl(URI.create(resource.getExternalUrl()), null);
		}
		return new DownloadUrl(downloads.presignedGetUrl(resourceId, ObjectStorageService.Disposition.ATTACHMENT),
				java.time.Instant.now().plus(settings.getUrlTtl()));
	}

	private LessonLocation requireOwnedLesson(long lessonId, long actorId, ResourceType type) {
		LessonLocation lesson = structure.getLessonLocation(lessonId)
				.filter(location -> !location.courseDeleted())
				.orElseThrow(() -> new NotFoundException("The requested resource was not found"));
		access.authorize(new ResourceAccessContext(actorId,
				new ResourceSummary(null, lesson.courseId(), lesson.lessonId(),
						type, ResourcePurpose.LESSON_RESOURCE, ResourceStatus.READY),
				ResourceAction.MANAGE), ResourceAccessDeniedMode.NOT_FOUND);
		return lesson;
	}

	private ResourceEntity findOwned(long resourceId, long actorId, ResourceAction action) {
		ResourceEntity resource = resources.findByIdAndDeletedAtIsNull(resourceId)
				.orElseThrow(() -> new NotFoundException("The requested resource was not found"));
		access.authorize(new ResourceAccessContext(actorId, ResourceSummary.from(resource), action),
				ResourceAccessDeniedMode.NOT_FOUND);
		return resource;
	}

	private UserEntity user(long userId) {
		return users.findById(userId)
				.orElseThrow(() -> new NotFoundException("The requested resource was not found"));
	}

	private String validateTitle(String title) {
		if (title == null || title.isBlank()) {
			throw new BadRequestException("The resource title must not be blank.");
		}
		String normalized = title.replaceAll("[\\p{Cntrl}]", "").trim();
		if (normalized.isBlank() || normalized.length() > 255) {
			throw new BadRequestException("The resource title must be between 1 and 255 characters.");
		}
		return normalized;
	}

	private String validateExternalUrl(String value) {
		if (value == null || value.isBlank() || value.length() > 2048) {
			throw new BadRequestException("The link URL must be between 1 and 2048 characters.");
		}
		try {
			URI uri = new URI(value.trim());
			String scheme = uri.getScheme();
			if (scheme == null
					|| !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
					|| uri.getHost() == null
					|| uri.getUserInfo() != null) {
				throw new BadRequestException("Links must use HTTP or HTTPS, have a valid host, and contain no credentials.");
			}
			return uri.toASCIIString();
		} catch (URISyntaxException exception) {
			throw new BadRequestException("The link URL is invalid.");
		}
	}

	public record LinkRequest(String url, String title) {
	}

	public record UpdateRequest(String title, String url) {
	}

	public record ExpectedRequest(ResourceType resourceType, int minCount, String label) {
	}

	public record DownloadUrl(URI url, java.time.Instant expiresAt) {
	}
}
