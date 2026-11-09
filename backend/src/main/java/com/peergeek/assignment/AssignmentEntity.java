package com.peergeek.assignment;

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
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "assignments")
public class AssignmentEntity extends BaseEntity {

	@Column(name = "course_id", nullable = false)
	private Long courseId;

	@Column(name = "module_id")
	private Long moduleId;

	@Column(name = "lesson_id")
	private Long lessonId;

	@Enumerated(EnumType.STRING)
	@Column(name = "kind", nullable = false, length = 32)
	private AssignmentKind kind;

	@Column(name = "title", nullable = false)
	private String title;

	@Column(name = "instructions", nullable = false, columnDefinition = "TEXT")
	private String instructions;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 32)
	private AssignmentStatus status = AssignmentStatus.DRAFT;

	@Column(name = "max_points", nullable = false, precision = 7, scale = 2)
	private BigDecimal maxPoints = BigDecimal.ZERO;

	@Column(name = "opens_at")
	private Instant opensAt;

	@Column(name = "due_at")
	private Instant dueAt;

	@Column(name = "time_limit_minutes")
	private Integer timeLimitMinutes;

	@Column(name = "allow_late", nullable = false)
	private boolean allowLate = false;

	@Column(name = "allow_text", nullable = false)
	private boolean allowText = false;

	@Column(name = "allow_files", nullable = false)
	private boolean allowFiles = false;

	@Column(name = "show_correct_answers", nullable = false)
	private boolean showCorrectAnswers = false;

	@Column(name = "attempt_count", nullable = false)
	private int attemptCount = 0;

	@Column(name = "published_at")
	private Instant publishedAt;

	@Version
	@Column(name = "version", nullable = false)
	private long version;

	@Column(name = "created_by", nullable = false)
	private Long createdBy;

	@OneToMany(mappedBy = "assignment", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("position ASC")
	private List<AssignmentQuestionEntity> questions = new ArrayList<>();

	protected AssignmentEntity() {
	}

	public AssignmentEntity(
			Long courseId,
			Long moduleId,
			Long lessonId,
			AssignmentKind kind,
			String title,
			String instructions,
			BigDecimal maxPoints,
			Long createdBy) {
		this.courseId = courseId;
		this.moduleId = moduleId;
		this.lessonId = lessonId;
		this.kind = kind;
		this.title = title;
		this.instructions = instructions;
		this.maxPoints = maxPoints != null ? maxPoints : BigDecimal.ZERO;
		this.status = AssignmentStatus.DRAFT;
		this.createdBy = createdBy;
	}

	public Long getCourseId() {
		return courseId;
	}

	public Long getModuleId() {
		return moduleId;
	}

	public void setModuleId(Long moduleId) {
		this.moduleId = moduleId;
	}

	public Long getLessonId() {
		return lessonId;
	}

	public void setLessonId(Long lessonId) {
		this.lessonId = lessonId;
	}

	public AssignmentKind getKind() {
		return kind;
	}

	public void setKind(AssignmentKind kind) {
		this.kind = kind;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getInstructions() {
		return instructions;
	}

	public void setInstructions(String instructions) {
		this.instructions = instructions;
	}

	public AssignmentStatus getStatus() {
		return status;
	}

	public void setStatus(AssignmentStatus status) {
		this.status = status;
	}

	public BigDecimal getMaxPoints() {
		return maxPoints;
	}

	public void setMaxPoints(BigDecimal maxPoints) {
		this.maxPoints = maxPoints;
	}

	public Instant getOpensAt() {
		return opensAt;
	}

	public void setOpensAt(Instant opensAt) {
		this.opensAt = opensAt;
	}

	public Instant getDueAt() {
		return dueAt;
	}

	public void setDueAt(Instant dueAt) {
		this.dueAt = dueAt;
	}

	public Integer getTimeLimitMinutes() {
		return timeLimitMinutes;
	}

	public void setTimeLimitMinutes(Integer timeLimitMinutes) {
		this.timeLimitMinutes = timeLimitMinutes;
	}

	public boolean isAllowLate() {
		return allowLate;
	}

	public void setAllowLate(boolean allowLate) {
		this.allowLate = allowLate;
	}

	public boolean isAllowText() {
		return allowText;
	}

	public void setAllowText(boolean allowText) {
		this.allowText = allowText;
	}

	public boolean isAllowFiles() {
		return allowFiles;
	}

	public void setAllowFiles(boolean allowFiles) {
		this.allowFiles = allowFiles;
	}

	public boolean isShowCorrectAnswers() {
		return showCorrectAnswers;
	}

	public void setShowCorrectAnswers(boolean showCorrectAnswers) {
		this.showCorrectAnswers = showCorrectAnswers;
	}

	public int getAttemptCount() {
		return attemptCount;
	}

	public void setAttemptCount(int attemptCount) {
		this.attemptCount = attemptCount;
	}

	public void incrementAttemptCount() {
		this.attemptCount++;
	}

	public Instant getPublishedAt() {
		return publishedAt;
	}

	public void setPublishedAt(Instant publishedAt) {
		this.publishedAt = publishedAt;
	}

	public long getVersion() {
		return version;
	}

	public void setVersion(long version) {
		this.version = version;
	}

	public Long getCreatedBy() {
		return createdBy;
	}

	public List<AssignmentQuestionEntity> getQuestions() {
		return questions;
	}

	public void setQuestions(List<AssignmentQuestionEntity> questions) {
		this.questions.clear();
		if (questions != null) {
			this.questions.addAll(questions);
		}
	}
}
