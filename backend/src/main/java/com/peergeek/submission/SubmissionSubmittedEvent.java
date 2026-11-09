package com.peergeek.submission;

import com.peergeek.common.DomainEvent;

public final class SubmissionSubmittedEvent extends DomainEvent {

	private final Long courseId;
	private final Long assignmentId;
	private final Long submissionId;
	private final Long userId;
	private final boolean late;
	private final boolean autoSubmitted;

	public SubmissionSubmittedEvent(
			Long courseId,
			Long assignmentId,
			Long submissionId,
			Long userId,
			boolean late,
			boolean autoSubmitted) {
		super(submissionId);
		this.courseId = courseId;
		this.assignmentId = assignmentId;
		this.submissionId = submissionId;
		this.userId = userId;
		this.late = late;
		this.autoSubmitted = autoSubmitted;
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

	public Long getSubmissionId() {
		return submissionId;
	}

	public Long submissionId() {
		return submissionId;
	}

	public Long getUserId() {
		return userId;
	}

	public Long userId() {
		return userId;
	}

	public boolean isLate() {
		return late;
	}

	public boolean late() {
		return late;
	}

	public boolean isAutoSubmitted() {
		return autoSubmitted;
	}

	public boolean autoSubmitted() {
		return autoSubmitted;
	}
}
