package com.peergeek.enrollment;

import com.peergeek.common.DomainEvent;

public final class LessonCompletedEvent extends DomainEvent {
	private final Long courseId;
	private final Long userId;
	private final Long lessonId;
	private final int completedLessons;
	private final int totalLessons;

	public LessonCompletedEvent(
			Object source, Long courseId, Long userId, Long lessonId, int completedLessons, int totalLessons) {
		super(source);
		this.courseId = courseId;
		this.userId = userId;
		this.lessonId = lessonId;
		this.completedLessons = completedLessons;
		this.totalLessons = totalLessons;
	}

	public LessonCompletedEvent(Long courseId, Long userId, Long lessonId, int completedLessons, int totalLessons) {
		super(courseId);
		this.courseId = courseId;
		this.userId = userId;
		this.lessonId = lessonId;
		this.completedLessons = completedLessons;
		this.totalLessons = totalLessons;
	}

	public Long getCourseId() {
		return courseId;
	}

	public Long courseId() {
		return courseId;
	}

	public Long getUserId() {
		return userId;
	}

	public Long userId() {
		return userId;
	}

	public Long getLessonId() {
		return lessonId;
	}

	public Long lessonId() {
		return lessonId;
	}

	public int getCompletedLessons() {
		return completedLessons;
	}

	public int completedLessons() {
		return completedLessons;
	}

	public int getTotalLessons() {
		return totalLessons;
	}

	public int totalLessons() {
		return totalLessons;
	}
}
