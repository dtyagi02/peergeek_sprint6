package com.peergeek.enrollment;

import com.peergeek.common.DomainEvent;

public final class LearnerWithdrawnEvent extends DomainEvent {
	private final Long courseId;
	private final Long userId;

	public LearnerWithdrawnEvent(Object source, Long courseId, Long userId) {
		super(source);
		this.courseId = courseId;
		this.userId = userId;
	}

	public LearnerWithdrawnEvent(Long courseId, Long userId) {
		super(courseId);
		this.courseId = courseId;
		this.userId = userId;
	}

	public Long getCourseId() {
		return courseId;
	}

	public Long courseId() {
		return courseId;
	}

	public Long getUserId() {
		return userId;
	}

	public Long userId() {
		return userId;
	}
}
