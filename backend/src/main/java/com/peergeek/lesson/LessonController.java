package com.peergeek.lesson;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LessonController {
	private final LessonService lessons;

	public LessonController(LessonService lessons) {
		this.lessons = lessons;
	}

	@PostMapping("/api/v1/instructor/modules/{moduleId}/lessons")
	@ResponseStatus(HttpStatus.CREATED)
	public LessonDto create(@PathVariable long moduleId, @RequestBody LessonCreateRequest request) {
		return lessons.create(moduleId, request.title(), request.summary());
	}

	@PatchMapping("/api/v1/instructor/lessons/{lessonId}")
	public LessonDto patch(@PathVariable long lessonId, @RequestBody com.fasterxml.jackson.databind.JsonNode patch) {
		return lessons.patch(lessonId, patch);
	}

	@DeleteMapping("/api/v1/instructor/lessons/{lessonId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable long lessonId) {
		lessons.delete(lessonId);
	}

	@PutMapping("/api/v1/instructor/modules/{moduleId}/lessons/order")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void reorder(@PathVariable long moduleId, @RequestBody LessonOrderRequest request) {
		lessons.reorder(moduleId, request.lessonIds());
	}

	@PostMapping("/api/v1/instructor/lessons/{lessonId}/move")
	public LessonDto move(@PathVariable long lessonId, @RequestBody LessonMoveRequest request) {
		if (request.targetModuleId() == null || request.position() == null) {
			throw new com.peergeek.course.CourseInputValidationException(
					"targetModuleId", "targetModuleId and position are required");
		}
		return lessons.move(lessonId, request.targetModuleId(), request.position());
	}

	@GetMapping("/api/v1/instructor/lessons/{lessonId}/content")
	public LessonContentDto getContent(@PathVariable long lessonId) {
		return lessons.getContent(lessonId);
	}

	@PutMapping("/api/v1/instructor/lessons/{lessonId}/content")
	public LessonContentDto saveContent(
			@PathVariable long lessonId, @RequestBody LessonContentSaveRequest request) {
		return lessons.saveContent(lessonId, request);
	}
}
