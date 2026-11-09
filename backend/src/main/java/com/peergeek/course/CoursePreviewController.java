package com.peergeek.course;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/instructor/courses/{courseId}/preview")
public class CoursePreviewController {
	private final CoursePreviewService previews;

	public CoursePreviewController(CoursePreviewService previews) {
		this.previews = previews;
	}

	@GetMapping
	public CoursePreview preview(@PathVariable long courseId) {
		return previews.preview(courseId);
	}
}
