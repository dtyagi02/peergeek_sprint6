package com.peergeek.enrollment;

import java.time.Instant;
import com.peergeek.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
		name = "lesson_progress",
		uniqueConstraints = @UniqueConstraint(name = "uq_lesson_progress_enrollment_lesson", columnNames = {"enrollment_id", "lesson_id"})
)
public class LessonProgressEntity extends BaseEntity {

	@Column(name = "enrollment_id", nullable = false)
	private Long enrollmentId;

	@Column(name = "lesson_id", nullable = false)
	private Long lessonId;

	@Column(name = "completed_at", nullable = false)
	private Instant completedAt;

	protected LessonProgressEntity() {
	}

	public LessonProgressEntity(Long enrollmentId, Long lessonId, Instant completedAt) {
		this.enrollmentId = enrollmentId;
		this.lessonId = lessonId;
		this.completedAt = completedAt;
	}

	public Long getEnrollmentId() {
		return enrollmentId;
	}

	public Long getLessonId() {
		return lessonId;
	}

	public Instant getCompletedAt() {
		return completedAt;
	}

	public void setCompletedAt(Instant completedAt) {
		this.completedAt = completedAt;
	}
}
