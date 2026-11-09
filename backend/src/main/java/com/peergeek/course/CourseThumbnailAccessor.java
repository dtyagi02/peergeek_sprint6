package com.peergeek.course;

import java.util.Optional;
import com.peergeek.exception.NotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class CourseThumbnailAccessor {
	private final CourseRepository courses;

	public CourseThumbnailAccessor(CourseRepository courses) {
		this.courses = courses;
	}

	@Transactional
	public void setThumbnailResourceId(long courseId, Long resourceId) {
		if (courses.updateThumbnailResourceId(courseId, resourceId) == 0) {
			throw new NotFoundException("The requested resource was not found");
		}
	}

	@Transactional(readOnly = true)
	public Optional<Long> getThumbnailResourceId(long courseId) {
		return courses.findThumbnailResourceId(courseId);
	}
}
