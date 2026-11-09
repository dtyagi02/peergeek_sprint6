package com.peergeek.submission;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
		name = "submission_answers",
		uniqueConstraints = @UniqueConstraint(name = "uq_submission_answers_submission_question", columnNames = {"submission_id", "question_id"})
)
public class SubmissionAnswerEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "submission_id", nullable = false)
	private SubmissionEntity submission;

	@Column(name = "question_id", nullable = false)
	private Long questionId;

	@Column(name = "answer_text", columnDefinition = "TEXT")
	private String answerText;

	@Convert(converter = SelectedOptionIdsConverter.class)
	@Column(name = "selected_option_ids", columnDefinition = "json")
	private List<String> selectedOptionIds = new ArrayList<>();

	@Column(name = "auto_points", precision = 7, scale = 2)
	private BigDecimal autoPoints;

	@Column(name = "points", precision = 7, scale = 2)
	private BigDecimal points;

	@Column(name = "feedback", columnDefinition = "TEXT")
	private String feedback;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected SubmissionAnswerEntity() {
	}

	public SubmissionAnswerEntity(SubmissionEntity submission, Long questionId, Instant updatedAt) {
		this.submission = submission;
		this.questionId = questionId;
		this.updatedAt = updatedAt;
	}

	public Long getId() {
		return id;
	}

	public SubmissionEntity getSubmission() {
		return submission;
	}

	public Long getQuestionId() {
		return questionId;
	}

	public String getAnswerText() {
		return answerText;
	}

	public void setAnswerText(String answerText) {
		this.answerText = answerText;
	}

	public List<String> getSelectedOptionIds() {
		return selectedOptionIds;
	}

	public void setSelectedOptionIds(List<String> selectedOptionIds) {
		this.selectedOptionIds = selectedOptionIds != null ? new ArrayList<>(selectedOptionIds) : new ArrayList<>();
	}

	public BigDecimal getAutoPoints() {
		return autoPoints;
	}

	public void setAutoPoints(BigDecimal autoPoints) {
		this.autoPoints = autoPoints;
	}

	public BigDecimal getPoints() {
		return points;
	}

	public void setPoints(BigDecimal points) {
		this.points = points;
	}

	public String getFeedback() {
		return feedback;
	}

	public void setFeedback(String feedback) {
		this.feedback = feedback;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(Instant updatedAt) {
		this.updatedAt = updatedAt;
	}
}
