package com.peergeek.courselearning.submission;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "submission_answers")
public class SubmissionAnswerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "submission_id", nullable = false)
    private Long submissionId;

    @Column(name = "question_id", nullable = false)
    private Long questionId;

    @Column(name = "answer_text", columnDefinition = "TEXT")
    private String answerText;

    @Column(name = "selected_option_ids", columnDefinition = "JSON")
    private String selectedOptionIds;

    @Column(name = "auto_points", precision = 6, scale = 2)
    private BigDecimal autoPoints;

    @Column(name = "points", precision = 6, scale = 2)
    private BigDecimal points;

    @Column(name = "feedback", columnDefinition = "TEXT")
    private String feedback;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected SubmissionAnswerEntity() {}

    public SubmissionAnswerEntity(Long submissionId, Long questionId, String answerText, String selectedOptionIds) {
        this.submissionId = submissionId;
        this.questionId = questionId;
        this.answerText = answerText;
        this.selectedOptionIds = selectedOptionIds;
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSubmissionId() {
        return submissionId;
    }

    public Long getQuestionId() {
        return questionId;
    }

    public String getAnswerText() {
        return answerText;
    }

    public void setAnswerText(String answerText) {
        this.answerText = answerText;
        this.updatedAt = Instant.now();
    }

    public String getSelectedOptionIds() {
        return selectedOptionIds;
    }

    public void setSelectedOptionIds(String selectedOptionIds) {
        this.selectedOptionIds = selectedOptionIds;
        this.updatedAt = Instant.now();
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
