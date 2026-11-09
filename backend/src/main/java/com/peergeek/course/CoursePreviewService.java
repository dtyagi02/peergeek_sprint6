package com.peergeek.course;

import java.net.URI;
import java.util.List;
import com.peergeek.file.LessonResourceQueryService;
import com.peergeek.file.ResourceDownloadService;
import com.peergeek.file.ResourceAction;
import com.peergeek.file.ResourceAccessContext;
import com.peergeek.file.ResourceAccessDeniedMode;
import com.peergeek.file.ResourceAccessService;
import com.peergeek.file.ResourceRepository;
import com.peergeek.file.ResourceSummary;
import com.peergeek.security.CurrentUserProvider;
import com.peergeek.security.OwnershipGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CoursePreviewService {
	private final CourseStructureQueryService structure;
	private final LessonResourceQueryService lessonResources;
	private final ResourceRepository resources;
	private final ResourceAccessService access;
	private final ResourceDownloadService downloads;
	private final CurrentUserProvider users;
	private final OwnershipGuard ownership;

	public CoursePreviewService(CourseStructureQueryService structure,
			LessonResourceQueryService lessonResources, ResourceRepository resources,
			ResourceAccessService access, ResourceDownloadService downloads,
			CurrentUserProvider users, OwnershipGuard ownership) {
		this.structure = structure;
		this.lessonResources = lessonResources;
		this.resources = resources;
		this.access = access;
		this.downloads = downloads;
		this.users = users;
		this.ownership = ownership;
	}

	@Transactional(readOnly = true)
	public CoursePreview preview(long courseId) {
		long userId = users.requireCurrentUser().id();
		CourseMetadataSnapshot metadata = structure.getCourseMetadata(courseId)
				.filter(value -> !value.deleted())
				.orElseThrow(() -> new com.peergeek.exception.NotFoundException(
						"The requested resource was not found"));
		ownership.requireOwnerOrNotFound(metadata.ownerId(), userId);
		CourseStructureQueryService.CourseStructure course = structure.getStructure(courseId);
		List<CoursePreview.Module> modules = course.modules().stream().map(module ->
				new CoursePreview.Module(module.id(), module.title(), module.description(),
						module.lessons().stream().map(lesson -> {
							var content = structure.getLessonContent(lesson.id());
							List<CoursePreview.Resource> lessonResourcesList =
									lessonResources.listResources(lesson.id()).stream()
									.filter(resource -> resource.status() == com.peergeek.file.ResourceStatus.READY)
									.map(resource -> {
										ResourceSummary summary = resources.findByIdAndDeletedAtIsNull(resource.id())
												.map(ResourceSummary::from)
												.orElseThrow(() -> new com.peergeek.exception.NotFoundException(
														"The requested resource was not found"));
										access.authorize(new ResourceAccessContext(userId, summary,
												ResourceAction.VIEW), ResourceAccessDeniedMode.NOT_FOUND);
										URI contentUrl = resource.type() == com.peergeek.file.ResourceType.LINK
												? null : downloads.presignedGetUrl(resource.id(),
														com.peergeek.file.ObjectStorageService.Disposition.INLINE);
										URI externalUrl = resource.externalUrl() == null
												? null : URI.create(resource.externalUrl());
										return new CoursePreview.Resource(resource.id(), resource.type().name(),
												resource.title(), resource.mimeType(), resource.sizeBytes(),
												contentUrl, externalUrl);
									}).toList();
							return new CoursePreview.Lesson(lesson.id(), lesson.title(), lesson.summary(),
									content.document(), lessonResourcesList);
						}).toList()))
				.toList();
		return new CoursePreview(courseId, metadata.title(), metadata.description(), metadata.categoryName(),
				metadata.difficulty(), metadata.topics(), modules);
	}
}
