package com.peergeek.discovery;

import java.math.BigDecimal;
import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "course_public_stats")
public class CoursePublicStatsEntity {
	@Id
	@Column(name = "course_id")
	private Long courseId;

	@Column(name = "enrollment_count")
	private Integer enrollmentCount;

	@Column(name = "completion_count")
	private Integer completionCount;

	@Column(name = "rating_average", precision = 3, scale = 2)
	private BigDecimal ratingAverage;

	@Column(name = "rating_count")
	private Integer ratingCount;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected CoursePublicStatsEntity() {}

	public CoursePublicStatsEntity(
			Long courseId,
			Integer enrollmentCount,
			Integer completionCount,
			BigDecimal ratingAverage,
			Integer ratingCount,
			Instant updatedAt) {
		this.courseId = courseId;
		this.enrollmentCount = enrollmentCount;
		this.completionCount = completionCount;
		this.ratingAverage = ratingAverage;
		this.ratingCount = ratingCount;
		this.updatedAt = updatedAt;
	}

	public Long getCourseId() { return courseId; }
	public Integer getEnrollmentCount() { return enrollmentCount; }
	public void setEnrollmentCount(Integer enrollmentCount) { this.enrollmentCount = enrollmentCount; }
	public Integer getCompletionCount() { return completionCount; }
	public void setCompletionCount(Integer completionCount) { this.completionCount = completionCount; }
	public BigDecimal getRatingAverage() { return ratingAverage; }
	public void setRatingAverage(BigDecimal ratingAverage) { this.ratingAverage = ratingAverage; }
	public Integer getRatingCount() { return ratingCount; }
	public void setRatingCount(Integer ratingCount) { this.ratingCount = ratingCount; }
	public Instant getUpdatedAt() { return updatedAt; }
	public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
