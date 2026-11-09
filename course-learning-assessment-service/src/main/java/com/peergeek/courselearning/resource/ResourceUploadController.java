package com.peergeek.courselearning.resource;

import com.peergeek.courselearning.security.CurrentUserProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/instructor")
public class ResourceUploadController {

    private final ResourceUploadService uploadService;
    private final CurrentUserProvider currentUserProvider;

    public ResourceUploadController(ResourceUploadService uploadService, CurrentUserProvider currentUserProvider) {
        this.uploadService = uploadService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/resources/limits")
    public ResponseEntity<Map<String, Object>> limits() {
        return ResponseEntity.ok(uploadService.limits());
    }

    @PostMapping("/lessons/{lessonId}/resources/uploads")
    public ResponseEntity<ResourceRequests.UploadCreated> createLessonUpload(
            @PathVariable("lessonId") long lessonId,
            @RequestBody ResourceRequests.LessonUpload request) {
        long actorId = currentUserProvider.requireCurrentUser().id();
        return ResponseEntity.status(HttpStatus.CREATED).body(uploadService.createLessonUpload(lessonId, actorId, request));
    }

    @PostMapping("/resources/{resourceId}/upload-url")
    public ResponseEntity<ResourceRequests.UploadInstruction> uploadUrl(@PathVariable("resourceId") long resourceId) {
        long actorId = currentUserProvider.requireCurrentUser().id();
        return ResponseEntity.ok(uploadService.refreshUploadUrl(resourceId, actorId));
    }

    @PostMapping("/resources/{resourceId}/confirm")
    public ResponseEntity<ResourceDto> confirm(@PathVariable("resourceId") long resourceId) {
        long actorId = currentUserProvider.requireCurrentUser().id();
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(uploadService.confirm(resourceId, actorId));
    }

    @GetMapping("/resources/{resourceId}")
    public ResponseEntity<ResourceDto> get(@PathVariable("resourceId") long resourceId) {
        long actorId = currentUserProvider.requireCurrentUser().id();
        return ResponseEntity.ok(uploadService.get(resourceId, actorId));
    }
}

