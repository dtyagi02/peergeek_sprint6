package com.peergeek.enrollment;

import com.peergeek.security.CurrentUser;
import com.peergeek.security.CurrentUserProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/learning")
public class LearningContentController {

	private final LearningContentService learningContentService;
	private final ProgressService progressService;
	private final CurrentUserProvider currentUserProvider;

	public LearningContentController(
			LearningContentService learningContentService,
			ProgressService progressService,
			CurrentUserProvider currentUserProvider) {
		this.learningContentService = learningContentService;
		this.progressService = progressService;
		this.currentUserProvider = currentUserProvider;
	}

	@GetMapping("/courses/{courseId}")
	public ResponseEntity<LearnerCourseOutlineDto> getLearnerOutline(@PathVariable("courseId") long courseId) {
		CurrentUser user = currentUserProvider.requireCurrentUser();
		LearnerCourseOutlineDto dto = learningContentService.getLearnerOutline(courseId, user.id());
		return ResponseEntity.ok()
				.header(HttpHeaders.CACHE_CONTROL, "no-store")
				.body(dto);
	}

	@GetMapping("/courses/{courseId}/lessons/{lessonId}")
	public ResponseEntity<LearnerLessonDto> getLearnerLesson(
			@PathVariable("courseId") long courseId,
			@PathVariable("lessonId") long lessonId) {
		CurrentUser user = currentUserProvider.requireCurrentUser();
		LearnerLessonDto dto = learningContentService.getLearnerLesson(courseId, lessonId, user.id());
		return ResponseEntity.ok()
				.header(HttpHeaders.CACHE_CONTROL, "no-store")
				.body(dto);
	}

	@GetMapping("/resources/{resourceId}/url")
	public ResponseEntity<PresignedUrlDto> getFreshResourceUrl(
			@PathVariable("resourceId") long resourceId,
			@RequestParam(value = "disposition", required = false) String disposition) {
		CurrentUser user = currentUserProvider.requireCurrentUser();
		PresignedUrlDto dto = learningContentService.getFreshResourceUrl(resourceId, disposition, user.id());
		return ResponseEntity.ok()
				.header(HttpHeaders.CACHE_CONTROL, "no-store")
				.body(dto);
	}

	@PostMapping("/courses/{courseId}/lessons/{lessonId}/visit")
	public ResponseEntity<Void> recordVisit(
			@PathVariable("courseId") long courseId,
			@PathVariable("lessonId") long lessonId) {
		CurrentUser user = currentUserProvider.requireCurrentUser();
		learningContentService.recordVisit(courseId, lessonId, user.id());
		return ResponseEntity.noContent()
				.header(HttpHeaders.CACHE_CONTROL, "no-store")
				.build();
	}

	@PutMapping("/courses/{courseId}/lessons/{lessonId}/completion")
	public ResponseEntity<CourseProgressDto> setLessonCompletion(
			@PathVariable("courseId") long courseId,
			@PathVariable("lessonId") long lessonId,
			@RequestBody LessonCompletionRequest request) {
		CurrentUser user = currentUserProvider.requireCurrentUser();
		CourseProgressDto dto = progressService.setLessonCompletion(courseId, lessonId, user.id(), request.completed());
		return ResponseEntity.ok()
				.header(HttpHeaders.CACHE_CONTROL, "no-store")
				.body(dto);
	}

	@GetMapping("/courses/{courseId}/continue")
	public ResponseEntity<ContinueLessonDto> getContinueLesson(@PathVariable("courseId") long courseId) {
		CurrentUser user = currentUserProvider.requireCurrentUser();
		ContinueLessonDto dto = learningContentService.getContinueLesson(courseId, user.id());
		return ResponseEntity.ok()
				.header(HttpHeaders.CACHE_CONTROL, "no-store")
				.body(dto);
	}
}
