package com.peergeek.submission;

import java.math.BigDecimal;
import com.peergeek.common.DomainEvent;

public final class SubmissionGradedEvent extends DomainEvent {

	private final Long courseId;
	private final Long assignmentId;
	private final Long submissionId;
	private final Long learnerId;
	private final Long graderId;
	private final BigDecimal totalPoints;
	private final BigDecimal maxPoints;
	private final boolean autoGraded;
	private final boolean regrade;

	public SubmissionGradedEvent(
			Long courseId,
			Long assignmentId,
			Long submissionId,
			Long learnerId,
			Long graderId,
			BigDecimal totalPoints,
			BigDecimal maxPoints,
			boolean autoGraded,
			boolean regrade) {
		super(submissionId);
		this.courseId = courseId;
		this.assignmentId = assignmentId;
		this.submissionId = submissionId;
		this.learnerId = learnerId;
		this.graderId = graderId;
		this.totalPoints = totalPoints;
		this.maxPoints = maxPoints;
		this.autoGraded = autoGraded;
		this.regrade = regrade;
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

	public Long getLearnerId() {
		return learnerId;
	}

	public Long learnerId() {
		return learnerId;
	}

	public Long getGraderId() {
		return graderId;
	}

	public Long graderId() {
		return graderId;
	}

	public BigDecimal getTotalPoints() {
		return totalPoints;
	}

	public BigDecimal totalPoints() {
		return totalPoints;
	}

	public BigDecimal getMaxPoints() {
		return maxPoints;
	}

	public BigDecimal maxPoints() {
		return maxPoints;
	}

	public boolean isAutoGraded() {
		return autoGraded;
	}

	public boolean autoGraded() {
		return autoGraded;
	}

	public boolean isRegrade() {
		return regrade;
	}

	public boolean regrade() {
		return regrade;
	}
}
