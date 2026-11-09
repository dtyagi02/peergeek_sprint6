package com.peergeek.enrollment;

import java.time.Instant;
import com.peergeek.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

@Entity
@Table(
		name = "enrollments",
		uniqueConstraints = @UniqueConstraint(name = "uq_enrollments_user_course", columnNames = {"user_id", "course_id"})
)
public class EnrollmentEntity extends BaseEntity {

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "course_id", nullable = false)
	private Long courseId;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false)
	private EnrollmentStatus status = EnrollmentStatus.ACTIVE;

	@Column(name = "enrolled_at", nullable = false)
	private Instant enrolledAt;

	@Column(name = "withdrawn_at")
	private Instant withdrawnAt;

	@Column(name = "last_lesson_id")
	private Long lastLessonId;

	@Column(name = "last_accessed_at")
	private Instant lastAccessedAt;

	@Version
	@Column(name = "version", nullable = false)
	private Long version;

	protected EnrollmentEntity() {
	}

	public EnrollmentEntity(Long userId, Long courseId, Instant enrolledAt) {
		this.userId = userId;
		this.courseId = courseId;
		this.status = EnrollmentStatus.ACTIVE;
		this.enrolledAt = enrolledAt;
	}

	public Long getUserId() {
		return userId;
	}

	public Long getCourseId() {
		return courseId;
	}

	public EnrollmentStatus getStatus() {
		return status;
	}

	public void setStatus(EnrollmentStatus status) {
		this.status = status;
	}

	public Instant getEnrolledAt() {
		return enrolledAt;
	}

	public void setEnrolledAt(Instant enrolledAt) {
		this.enrolledAt = enrolledAt;
	}

	public Instant getWithdrawnAt() {
		return withdrawnAt;
	}

	public void setWithdrawnAt(Instant withdrawnAt) {
		this.withdrawnAt = withdrawnAt;
	}

	public Long getLastLessonId() {
		return lastLessonId;
	}

	public void setLastLessonId(Long lastLessonId) {
		this.lastLessonId = lastLessonId;
	}

	public Instant getLastAccessedAt() {
		return lastAccessedAt;
	}

	public void setLastAccessedAt(Instant lastAccessedAt) {
		this.lastAccessedAt = lastAccessedAt;
	}

	public Long getVersion() {
		return version;
	}
}
