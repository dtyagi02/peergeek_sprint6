package com.peergeek.course;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class CourseStatusAccessor {
	private final CourseRepository courses;

	public CourseStatusAccessor(CourseRepository courses) {
		this.courses = courses;
	}

	@Transactional(readOnly = true)
	public Optional<CourseStatusSnapshot> getStatusSnapshot(long courseId) {
		return courses.findStatusSnapshot(courseId);
	}

	@Transactional
	public int transitionStatus(
			long courseId, Set<CourseStatus> allowedFrom, CourseStatus to, Instant publishedAt) {
		if (allowedFrom.isEmpty()) return 0;
		return courses.transitionStatus(courseId, allowedFrom, to, publishedAt);
	}
}
