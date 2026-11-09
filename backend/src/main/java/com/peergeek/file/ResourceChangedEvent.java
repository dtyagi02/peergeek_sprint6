package com.peergeek.file;

import com.peergeek.common.DomainEvent;

public final class ResourceChangedEvent extends DomainEvent {
	private final Long courseId;
	private final Long lessonId;
	private final Long resourceId;
	private final ResourceChangeType changeType;

	public ResourceChangedEvent(
			Object source, Long courseId, Long lessonId, Long resourceId, ResourceChangeType changeType) {
		super(source);
		this.courseId = courseId;
		this.lessonId = lessonId;
		this.resourceId = resourceId;
		this.changeType = changeType;
	}

	public Long getCourseId() { return courseId; }
	public Long getLessonId() { return lessonId; }
	public Long getResourceId() { return resourceId; }
	public ResourceChangeType getChangeType() { return changeType; }
}
