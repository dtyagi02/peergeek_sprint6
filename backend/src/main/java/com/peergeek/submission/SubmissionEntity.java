package com.peergeek.submission;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import com.peergeek.common.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

@Entity
@Table(
		name = "submissions",
		uniqueConstraints = @UniqueConstraint(name = "uq_submissions_assignment_user", columnNames = {"assignment_id", "user_id"})
)
public class SubmissionEntity extends BaseEntity {

	@Column(name = "assignment_id", nullable = false)
	private Long assignmentId;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 32)
	private SubmissionStatus status = SubmissionStatus.IN_PROGRESS;

	@Column(name = "started_at", nullable = false)
	private Instant startedAt;

	@Column(name = "deadline_at")
	private Instant deadlineAt;

	@Column(name = "submitted_at")
	private Instant submittedAt;

	@Column(name = "late", nullable = false)
	private boolean late = false;

	@Column(name = "auto_submitted", nullable = false)
	private boolean autoSubmitted = false;

	@Column(name = "auto_graded", nullable = false)
	private boolean autoGraded = false;

	@Column(name = "text_answer", columnDefinition = "TEXT")
	private String textAnswer;

	@Column(name = "marks", precision = 7, scale = 2)
	private BigDecimal marks;

	@Column(name = "total_points", precision = 7, scale = 2)
	private BigDecimal totalPoints;

	@Column(name = "overall_feedback", columnDefinition = "TEXT")
	private String overallFeedback;

	@Column(name = "graded_by")
	private Long gradedBy;

	@Column(name = "graded_at")
	private Instant gradedAt;

	@Version
	@Column(name = "version", nullable = false)
	private long version;

	@OneToMany(mappedBy = "submission", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<SubmissionAnswerEntity> answers = new ArrayList<>();

	@OneToMany(mappedBy = "submission", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<SubmissionFileEntity> files = new ArrayList<>();

	protected SubmissionEntity() {
	}

	public SubmissionEntity(Long assignmentId, Long userId, Instant startedAt, Instant deadlineAt) {
		this.assignmentId = assignmentId;
		this.userId = userId;
		this.startedAt = startedAt;
		this.deadlineAt = deadlineAt;
		this.status = SubmissionStatus.IN_PROGRESS;
	}

	public Long getAssignmentId() {
		return assignmentId;
	}

	public Long getUserId() {
		return userId;
	}

	public SubmissionStatus getStatus() {
		return status;
	}

	public void setStatus(SubmissionStatus status) {
		this.status = status;
	}

	public Instant getStartedAt() {
		return startedAt;
	}

	public Instant getDeadlineAt() {
		return deadlineAt;
	}

	public void setDeadlineAt(Instant deadlineAt) {
		this.deadlineAt = deadlineAt;
	}

	public Instant getSubmittedAt() {
		return submittedAt;
	}

	public void setSubmittedAt(Instant submittedAt) {
		this.submittedAt = submittedAt;
	}

	public boolean isLate() {
		return late;
	}

	public void setLate(boolean late) {
		this.late = late;
	}

	public boolean isAutoSubmitted() {
		return autoSubmitted;
	}

	public void setAutoSubmitted(boolean autoSubmitted) {
		this.autoSubmitted = autoSubmitted;
	}

	public boolean isAutoGraded() {
		return autoGraded;
	}

	public void setAutoGraded(boolean autoGraded) {
		this.autoGraded = autoGraded;
	}

	public String getTextAnswer() {
		return textAnswer;
	}

	public void setTextAnswer(String textAnswer) {
		this.textAnswer = textAnswer;
	}

	public BigDecimal getMarks() {
		return marks;
	}

	public void setMarks(BigDecimal marks) {
		this.marks = marks;
	}

	public BigDecimal getTotalPoints() {
		return totalPoints;
	}

	public void setTotalPoints(BigDecimal totalPoints) {
		this.totalPoints = totalPoints;
	}

	public String getOverallFeedback() {
		return overallFeedback;
	}

	public void setOverallFeedback(String overallFeedback) {
		this.overallFeedback = overallFeedback;
	}

	public Long getGradedBy() {
		return gradedBy;
	}

	public void setGradedBy(Long gradedBy) {
		this.gradedBy = gradedBy;
	}

	public Instant getGradedAt() {
		return gradedAt;
	}

	public void setGradedAt(Instant gradedAt) {
		this.gradedAt = gradedAt;
	}

	public long getVersion() {
		return version;
	}

	public void setVersion(long version) {
		this.version = version;
	}

	public List<SubmissionAnswerEntity> getAnswers() {
		return answers;
	}

	public List<SubmissionFileEntity> getFiles() {
		return files;
	}
}
