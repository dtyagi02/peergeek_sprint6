package com.peergeek.file;

public record ResourceSummary(
		Long id,
		Long courseId,
		Long lessonId,
		ResourceType type,
		ResourcePurpose purpose,
		ResourceStatus status) {
	public static ResourceSummary from(ResourceEntity resource) {
		return new ResourceSummary(resource.getId(), resource.getCourseId(), resource.getLessonId(),
				resource.getType(), resource.getPurpose(), resource.getStatus());
	}
}
