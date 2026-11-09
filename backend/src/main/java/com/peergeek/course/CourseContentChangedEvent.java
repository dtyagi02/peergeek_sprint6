package com.peergeek.course;

import com.peergeek.common.DomainEvent;

public final class CourseContentChangedEvent extends DomainEvent {
	private final Long courseId;

	public CourseContentChangedEvent(Object source, Long courseId) {
		super(source);
		this.courseId = courseId;
	}

	public Long getCourseId() { return courseId; }
}
