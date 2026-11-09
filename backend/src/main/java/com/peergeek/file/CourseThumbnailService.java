package com.peergeek.file;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import com.peergeek.audit.AuditEvent;
import com.peergeek.audit.AuditService;
import com.peergeek.config.ResourceSettings;
import com.peergeek.course.CourseThumbnailAccessor;
import com.peergeek.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CourseThumbnailService {
	private final CourseThumbnailAccessor thumbnails;
	private final ResourceRepository resources;
	private final ResourceAccessService access;
	private final ResourceDownloadService downloads;
	private final ResourceSettings settings;
	private final AuditService audit;
	private final ResourceEventPublisher events;

	public CourseThumbnailService(
			CourseThumbnailAccessor thumbnails,
			ResourceRepository resources,
			ResourceAccessService access,
			ResourceDownloadService downloads,
			ResourceSettings settings,
			AuditService audit,
			ResourceEventPublisher events) {
		this.thumbnails = thumbnails;
		this.resources = resources;
		this.access = access;
		this.downloads = downloads;
		this.settings = settings;
		this.audit = audit;
		this.events = events;
	}

	@Transactional(readOnly = true)
	public ThumbnailDto get(long courseId, long actorId) {
		authorizeCourse(courseId, actorId, null);
		ResourceEntity resource = thumbnails.getThumbnailResourceId(courseId)
				.flatMap(resources::findByIdAndDeletedAtIsNull)
				.orElseThrow(() -> new NotFoundException("The requested resource was not found"));
		URI url = resource.getStatus() == ResourceStatus.READY
				? downloads.presignedGetUrl(resource.getId(), ObjectStorageService.Disposition.INLINE)
				: null;
		return new ThumbnailDto(resource.getId(), resource.getStatus(), url,
				url == null ? null : Instant.now().plus(settings.getUrlTtl()));
	}

	@Transactional
	public void delete(long courseId, long actorId) {
		ResourceEntity resource = thumbnails.getThumbnailResourceId(courseId)
				.flatMap(resources::findByIdAndDeletedAtIsNull)
				.orElseThrow(() -> new NotFoundException("The requested resource was not found"));
		authorizeCourse(courseId, actorId, resource);
		thumbnails.setThumbnailResourceId(courseId, null);
		resource.softDelete(Instant.now());
		audit.record(new AuditEvent(actorId, ResourceAuditActions.COURSE_THUMBNAIL_CHANGED,
				"course", Long.toString(courseId),
				Map.of("courseId", courseId, "resourceId", resource.getId(), "removed", true)));
		events.publishAfterCommit(courseId, null, resource.getId(), ResourceChangeType.DELETED);
	}

	private void authorizeCourse(long courseId, long actorId, ResourceEntity resource) {
		ResourceSummary summary = resource == null
				? new ResourceSummary(null, courseId, null, ResourceType.IMAGE,
						ResourcePurpose.COURSE_THUMBNAIL, ResourceStatus.READY)
				: ResourceSummary.from(resource);
		access.authorize(new ResourceAccessContext(actorId, summary, ResourceAction.MANAGE),
				ResourceAccessDeniedMode.NOT_FOUND);
	}

	public record ThumbnailDto(Long resourceId, ResourceStatus status, URI url, Instant expiresAt) {
	}
}
