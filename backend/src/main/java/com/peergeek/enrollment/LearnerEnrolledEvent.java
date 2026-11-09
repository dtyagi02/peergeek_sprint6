package com.peergeek.enrollment;

import com.peergeek.common.DomainEvent;

public final class LearnerEnrolledEvent extends DomainEvent {
	private final Long courseId;
	private final Long userId;
	private final Long enrollmentId;

	public LearnerEnrolledEvent(Object source, Long courseId, Long userId, Long enrollmentId) {
		super(source);
		this.courseId = courseId;
		this.userId = userId;
		this.enrollmentId = enrollmentId;
	}

	public LearnerEnrolledEvent(Long courseId, Long userId, Long enrollmentId) {
		super(courseId);
		this.courseId = courseId;
		this.userId = userId;
		this.enrollmentId = enrollmentId;
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

	public Long getEnrollmentId() {
		return enrollmentId;
	}

	public Long enrollmentId() {
		return enrollmentId;
	}
}
