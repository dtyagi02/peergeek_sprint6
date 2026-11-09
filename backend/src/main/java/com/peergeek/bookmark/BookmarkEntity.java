package com.peergeek.bookmark;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "bookmarks")
public class BookmarkEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Enumerated(EnumType.STRING)
	@Column(name = "target_type", nullable = false)
	private BookmarkTargetType targetType;

	@Column(name = "course_id", nullable = false)
	private Long courseId;

	@Column(name = "lesson_id")
	private Long lessonId;

	@Column(name = "target_key", insertable = false, updatable = false)
	private String targetKey;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected BookmarkEntity() {}

	public BookmarkEntity(Long userId, BookmarkTargetType targetType, Long courseId, Long lessonId) {
		this.userId = userId;
		this.targetType = targetType;
		this.courseId = courseId;
		this.lessonId = lessonId;
		this.createdAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public Long getUserId() {
		return userId;
	}

	public BookmarkTargetType getTargetType() {
		return targetType;
	}

	public Long getCourseId() {
		return courseId;
	}

	public Long getLessonId() {
		return lessonId;
	}

	public String getTargetKey() {
		return targetKey;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
