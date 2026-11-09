package com.peergeek.course;

import com.fasterxml.jackson.databind.JsonNode;
import com.peergeek.common.PageResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/instructor/courses")
public class CourseController {
	private final CourseService courses;
	private final CoursePublishingService publishing;

	public CourseController(CourseService courses, CoursePublishingService publishing) {
		this.courses = courses;
		this.publishing = publishing;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public CourseDto create(@RequestBody CourseCreateRequest request) {
		return courses.create(request);
	}

	@GetMapping
	public PageResponse<CourseDto> list(
			@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size,
			@RequestParam(required = false) String sort,
			@RequestParam(required = false) CourseStatus status,
			@RequestParam(required = false, name = "q") String query) {
		return courses.list(page, size, sort, status, query);
	}

	@GetMapping("/{courseId}")
	public CourseDto get(@PathVariable long courseId) {
		return courses.get(courseId);
	}

	@PatchMapping("/{courseId}")
	public CourseDto patch(@PathVariable long courseId, @RequestBody JsonNode patch) {
		return courses.patch(courseId, patch);
	}

	@DeleteMapping("/{courseId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable long courseId) {
		courses.delete(courseId);
	}

	@GetMapping("/{courseId}/outline")
	public CourseOutline outline(@PathVariable long courseId) {
		return courses.outline(courseId);
	}

	@PostMapping("/{courseId}/publish")
	public CourseStatusSnapshot publish(@PathVariable long courseId) {
		return publishing.publish(courseId);
	}

	@PostMapping("/{courseId}/unpublish")
	public CourseStatusSnapshot unpublish(@PathVariable long courseId) {
		return publishing.unpublish(courseId);
	}
}
