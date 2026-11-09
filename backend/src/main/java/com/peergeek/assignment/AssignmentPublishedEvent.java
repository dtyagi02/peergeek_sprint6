package com.peergeek.assignment;

import com.peergeek.common.DomainEvent;

public final class AssignmentPublishedEvent extends DomainEvent {

	private final Long courseId;
	private final Long assignmentId;
	private final String title;

	public AssignmentPublishedEvent(Long courseId, Long assignmentId, String title) {
		super(assignmentId);
		this.courseId = courseId;
		this.assignmentId = assignmentId;
		this.title = title;
	}

	public Long getCourseId() {
		return courseId;
	}

	public Long courseId() {
		return courseId;
	}

	public Long getAssignmentId() {
		return assignmentId;
	}

	public Long assignmentId() {
		return assignmentId;
	}

	public String getTitle() {
		return title;
	}

	public String title() {
		return title;
	}
}
