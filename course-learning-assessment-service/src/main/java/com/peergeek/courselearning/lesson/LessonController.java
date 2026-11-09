package com.peergeek.courselearning.lesson;

import com.fasterxml.jackson.databind.JsonNode;
import com.peergeek.courselearning.security.CurrentUserProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class LessonController {

    private final LessonService lessonService;
    private final CurrentUserProvider currentUserProvider;

    public LessonController(LessonService lessonService, CurrentUserProvider currentUserProvider) {
        this.lessonService = lessonService;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping("/api/v1/instructor/modules/{moduleId}/lessons")
    public ResponseEntity<LessonDto> create(
            @PathVariable("moduleId") Long moduleId,
            @RequestBody LessonCreateRequest request) {
        Long ownerId = currentUserProvider.requireCurrentUser().id();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(lessonService.create(moduleId, ownerId, request.title(), request.summary()));
    }

    @PatchMapping("/api/v1/instructor/lessons/{lessonId}")
    public ResponseEntity<LessonDto> patch(
            @PathVariable("lessonId") Long lessonId,
            @RequestBody JsonNode patch) {
        Long ownerId = currentUserProvider.requireCurrentUser().id();
        return ResponseEntity.ok(lessonService.patch(lessonId, ownerId, patch));
    }

    @DeleteMapping("/api/v1/instructor/lessons/{lessonId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("lessonId") Long lessonId) {
        Long ownerId = currentUserProvider.requireCurrentUser().id();
        lessonService.delete(lessonId, ownerId);
    }

    @PutMapping("/api/v1/instructor/modules/{moduleId}/lessons/order")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reorder(
            @PathVariable("moduleId") Long moduleId,
            @RequestBody LessonOrderRequest request) {
        Long ownerId = currentUserProvider.requireCurrentUser().id();
        lessonService.reorder(moduleId, ownerId, request.lessonIds());
    }

    @PostMapping("/api/v1/instructor/lessons/{lessonId}/move")
    public ResponseEntity<LessonDto> move(
            @PathVariable("lessonId") Long lessonId,
            @RequestBody LessonMoveRequest request) {
        Long ownerId = currentUserProvider.requireCurrentUser().id();
        return ResponseEntity.ok(lessonService.move(lessonId, ownerId, request.targetModuleId(), request.position()));
    }

    @GetMapping("/api/v1/instructor/lessons/{lessonId}/content")
    public ResponseEntity<LessonContentDto> getContent(@PathVariable("lessonId") Long lessonId) {
        return ResponseEntity.ok(lessonService.getContent(lessonId));
    }

    @PutMapping("/api/v1/instructor/lessons/{lessonId}/content")
    public ResponseEntity<LessonContentDto> saveContent(
            @PathVariable("lessonId") Long lessonId,
            @RequestBody LessonContentSaveRequest request) {
        Long ownerId = currentUserProvider.requireCurrentUser().id();
        return ResponseEntity.ok(lessonService.saveContent(lessonId, ownerId, request));
    }
}

