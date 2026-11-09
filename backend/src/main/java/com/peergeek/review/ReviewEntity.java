package com.peergeek.review;

import java.time.Instant;
import com.peergeek.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
		name = "reviews",
		uniqueConstraints = @UniqueConstraint(name = "uq_reviews_course_user", columnNames = {"course_id", "user_id"})
)
public class ReviewEntity extends BaseEntity {

	@Column(name = "course_id", nullable = false)
	private Long courseId;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@org.hibernate.annotations.JdbcTypeCode(java.sql.Types.TINYINT)
	@Column(name = "rating", nullable = false)
	private int rating;

	@Column(name = "review_text", columnDefinition = "TEXT")
	private String reviewText;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	public ReviewEntity() {
	}

	public ReviewEntity(Long courseId, Long userId, int rating, String reviewText) {
		this.courseId = courseId;
		this.userId = userId;
		this.rating = rating;
		this.reviewText = reviewText;
	}

	public Long getCourseId() {
		return courseId;
	}

	public void setCourseId(Long courseId) {
		this.courseId = courseId;
	}

	public Long getUserId() {
		return userId;
	}

	public void setUserId(Long userId) {
		this.userId = userId;
	}

	public int getRating() {
		return rating;
	}

	public void setRating(int rating) {
		this.rating = rating;
	}

	public String getReviewText() {
		return reviewText;
	}

	public void setReviewText(String reviewText) {
		this.reviewText = reviewText;
	}

	public Instant getDeletedAt() {
		return deletedAt;
	}

	public void setDeletedAt(Instant deletedAt) {
		this.deletedAt = deletedAt;
	}
}
