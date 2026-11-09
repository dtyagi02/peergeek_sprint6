package com.peergeek.note;

import com.peergeek.note.dto.LessonNoteDto;
import com.peergeek.note.dto.UpsertNoteRequest;
import com.peergeek.security.CurrentUser;
import com.peergeek.security.CurrentUserProvider;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/courses/{courseId}/lessons/{lessonId}/notes")
public class LessonNoteController {

	private final LessonNoteService lessonNoteService;
	private final CurrentUserProvider currentUserProvider;

	public LessonNoteController(LessonNoteService lessonNoteService, CurrentUserProvider currentUserProvider) {
		this.lessonNoteService = lessonNoteService;
		this.currentUserProvider = currentUserProvider;
	}

	@GetMapping
	public ResponseEntity<LessonNoteDto> getNote(
			@PathVariable("courseId") long courseId,
			@PathVariable("lessonId") long lessonId) {
		CurrentUser user = currentUserProvider.requireCurrentUser();
		return lessonNoteService.getNote(courseId, lessonId, user.id())
				.map(dto -> ResponseEntity.ok()
						.header(HttpHeaders.CACHE_CONTROL, "no-store")
						.body(dto))
				.orElseGet(() -> ResponseEntity.noContent()
						.header(HttpHeaders.CACHE_CONTROL, "no-store")
						.build());
	}

	@PutMapping
	public ResponseEntity<LessonNoteDto> saveNoteWithPut(
			@PathVariable("courseId") long courseId,
			@PathVariable("lessonId") long lessonId,
			@Valid @RequestBody UpsertNoteRequest request) {
		CurrentUser user = currentUserProvider.requireCurrentUser();
		LessonNoteDto dto = lessonNoteService.saveNote(courseId, lessonId, user.id(), request.content());
		return ResponseEntity.ok()
				.header(HttpHeaders.CACHE_CONTROL, "no-store")
				.body(dto);
	}

	@PostMapping
	public ResponseEntity<LessonNoteDto> saveNoteWithPost(
			@PathVariable("courseId") long courseId,
			@PathVariable("lessonId") long lessonId,
			@Valid @RequestBody UpsertNoteRequest request) {
		CurrentUser user = currentUserProvider.requireCurrentUser();
		LessonNoteDto dto = lessonNoteService.saveNote(courseId, lessonId, user.id(), request.content());
		return ResponseEntity.ok()
				.header(HttpHeaders.CACHE_CONTROL, "no-store")
				.body(dto);
	}

	@DeleteMapping
	public ResponseEntity<Void> deleteNote(
			@PathVariable("courseId") long courseId,
			@PathVariable("lessonId") long lessonId) {
		CurrentUser user = currentUserProvider.requireCurrentUser();
		lessonNoteService.deleteNote(courseId, lessonId, user.id());
		return ResponseEntity.noContent()
				.header(HttpHeaders.CACHE_CONTROL, "no-store")
				.build();
	}
}
