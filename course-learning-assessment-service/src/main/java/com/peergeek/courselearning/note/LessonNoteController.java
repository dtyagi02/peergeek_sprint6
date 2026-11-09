package com.peergeek.courselearning.note;

import com.peergeek.courselearning.security.CurrentUser;
import com.peergeek.courselearning.security.CurrentUserProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
    public ResponseEntity<LessonNoteDtos.LessonNoteDto> getNote(
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
    public ResponseEntity<LessonNoteDtos.LessonNoteDto> saveNoteWithPut(
            @PathVariable("courseId") long courseId,
            @PathVariable("lessonId") long lessonId,
            @RequestBody LessonNoteDtos.UpsertNoteRequest request) {
        CurrentUser user = currentUserProvider.requireCurrentUser();
        LessonNoteDtos.LessonNoteDto dto = lessonNoteService.saveNote(courseId, lessonId, user.id(), request.content());
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(dto);
    }

    @PostMapping
    public ResponseEntity<LessonNoteDtos.LessonNoteDto> saveNoteWithPost(
            @PathVariable("courseId") long courseId,
            @PathVariable("lessonId") long lessonId,
            @RequestBody LessonNoteDtos.UpsertNoteRequest request) {
        CurrentUser user = currentUserProvider.requireCurrentUser();
        LessonNoteDtos.LessonNoteDto dto = lessonNoteService.saveNote(courseId, lessonId, user.id(), request.content());
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

