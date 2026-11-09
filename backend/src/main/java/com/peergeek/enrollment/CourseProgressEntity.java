package com.peergeek.enrollment;

import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "course_progress")
@EntityListeners(AuditingEntityListener.class)
public class CourseProgressEntity {

	@Id
	@Column(name = "enrollment_id")
	private Long enrollmentId;

	@Column(name = "course_id", nullable = false)
	private Long courseId;

	@Column(name = "total_lessons", nullable = false)
	private int totalLessons;

	@Column(name = "completed_lessons", nullable = false)
	private int completedLessons;

	@org.hibernate.annotations.JdbcTypeCode(java.sql.Types.TINYINT)
	@Column(name = "percent", nullable = false)
	private int percent;

	@Column(name = "completed_at")
	private Instant completedAt;

	@Column(name = "stale", nullable = false)
	private boolean stale;

	@Column(name = "computed_at", nullable = false)
	private Instant computedAt;

	@LastModifiedDate
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected CourseProgressEntity() {
	}

	public CourseProgressEntity(
			Long enrollmentId,
			Long courseId,
			int totalLessons,
			int completedLessons,
			int percent,
			Instant completedAt,
			boolean stale,
			Instant computedAt) {
		this.enrollmentId = enrollmentId;
		this.courseId = courseId;
		this.totalLessons = totalLessons;
		this.completedLessons = completedLessons;
		this.percent = percent;
		this.completedAt = completedAt;
		this.stale = stale;
		this.computedAt = computedAt;
	}

	public Long getEnrollmentId() {
		return enrollmentId;
	}

	public Long getCourseId() {
		return courseId;
	}

	public int getTotalLessons() {
		return totalLessons;
	}

	public void setTotalLessons(int totalLessons) {
		this.totalLessons = totalLessons;
	}

	public int getCompletedLessons() {
		return completedLessons;
	}

	public void setCompletedLessons(int completedLessons) {
		this.completedLessons = completedLessons;
	}

	public int getPercent() {
		return percent;
	}

	public void setPercent(int percent) {
		this.percent = percent;
	}

	public Instant getCompletedAt() {
		return completedAt;
	}

	public void setCompletedAt(Instant completedAt) {
		this.completedAt = completedAt;
	}

	public boolean isStale() {
		return stale;
	}

	public void setStale(boolean stale) {
		this.stale = stale;
	}

	public Instant getComputedAt() {
		return computedAt;
	}

	public void setComputedAt(Instant computedAt) {
		this.computedAt = computedAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
