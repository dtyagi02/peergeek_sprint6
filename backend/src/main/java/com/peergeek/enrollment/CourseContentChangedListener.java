package com.peergeek.enrollment;

import com.peergeek.course.CourseContentChangedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class CourseContentChangedListener {

	private final ProgressService progressService;

	public CourseContentChangedListener(ProgressService progressService) {
		this.progressService = progressService;
	}

	@EventListener
	public void onCourseContentChanged(CourseContentChangedEvent event) {
		progressService.markCourseProgressStale(event.getCourseId());
	}
}
