package com.peergeek.courselearning.resource;

import com.peergeek.courselearning.security.CurrentUserProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/instructor")
public class InstructorResourceController {

    private final InstructorResourceService resourceService;
    private final ResourceUploadService uploadService;
    private final CurrentUserProvider currentUserProvider;

    public InstructorResourceController(
            InstructorResourceService resourceService,
            ResourceUploadService uploadService,
            CurrentUserProvider currentUserProvider) {
        this.resourceService = resourceService;
        this.uploadService = uploadService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/lessons/{lessonId}/resources")
    public ResponseEntity<List<ResourceRequests.ResourceSnapshot>> list(@PathVariable("lessonId") long lessonId) {
        long actorId = currentUserProvider.requireCurrentUser().id();
        return ResponseEntity.ok(resourceService.list(lessonId, actorId));
    }

    @PostMapping("/lessons/{lessonId}/resources/links")
    public ResponseEntity<ResourceDto> createLink(
            @PathVariable("lessonId") long lessonId,
            @RequestBody ResourceRequests.Link request) {
        long actorId = currentUserProvider.requireCurrentUser().id();
        return ResponseEntity.status(HttpStatus.CREATED).body(resourceService.createLink(lessonId, actorId, request));
    }

    @PatchMapping("/resources/{resourceId}")
    public ResponseEntity<ResourceDto> update(
            @PathVariable("resourceId") long resourceId,
            @RequestBody ResourceRequests.Update request) {
        long actorId = currentUserProvider.requireCurrentUser().id();
        return ResponseEntity.ok(resourceService.update(resourceId, actorId, request));
    }

    @DeleteMapping("/resources/{resourceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("resourceId") long resourceId) {
        long actorId = currentUserProvider.requireCurrentUser().id();
        resourceService.delete(resourceId, actorId);
    }

    @PutMapping("/lessons/{lessonId}/resources/order")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reorder(
            @PathVariable("lessonId") long lessonId,
            @RequestBody ResourceRequests.Order request) {
        long actorId = currentUserProvider.requireCurrentUser().id();
        resourceService.reorder(lessonId, actorId, request.resourceIds());
    }

    @GetMapping("/lessons/{lessonId}/expected-resources")
    public ResponseEntity<List<ResourceRequests.ExpectedResourceSnapshot>> listExpected(@PathVariable("lessonId") long lessonId) {
        long actorId = currentUserProvider.requireCurrentUser().id();
        return ResponseEntity.ok(resourceService.listExpected(lessonId, actorId));
    }

    @PutMapping("/lessons/{lessonId}/expected-resources")
    public ResponseEntity<List<ResourceRequests.ExpectedResourceSnapshot>> replaceExpected(
            @PathVariable("lessonId") long lessonId,
            @RequestBody List<ResourceRequests.Expected> request) {
        long actorId = currentUserProvider.requireCurrentUser().id();
        return ResponseEntity.ok(resourceService.replaceExpected(lessonId, actorId, request));
    }

    @GetMapping("/resources/{resourceId}/download-url")
    public ResponseEntity<ResourceRequests.DownloadUrl> downloadUrl(@PathVariable("resourceId") long resourceId) {
        long actorId = currentUserProvider.requireCurrentUser().id();
        return ResponseEntity.ok(resourceService.downloadUrl(resourceId, actorId));
    }

    @PostMapping("/courses/{courseId}/thumbnail/uploads")
    public ResponseEntity<ResourceRequests.UploadCreated> uploadThumbnail(
            @PathVariable("courseId") long courseId,
            @RequestBody ResourceRequests.ThumbnailUpload request) {
        long actorId = currentUserProvider.requireCurrentUser().id();
        return ResponseEntity.status(HttpStatus.CREATED).body(uploadService.createThumbnailUpload(courseId, actorId, request));
    }

    @GetMapping("/courses/{courseId}/thumbnail")
    public ResponseEntity<ResourceRequests.ThumbnailDto> getThumbnail(@PathVariable("courseId") long courseId) {
        long actorId = currentUserProvider.requireCurrentUser().id();
        return ResponseEntity.ok(resourceService.getThumbnail(courseId, actorId));
    }

    @DeleteMapping("/courses/{courseId}/thumbnail")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteThumbnail(@PathVariable("courseId") long courseId) {
        long actorId = currentUserProvider.requireCurrentUser().id();
        resourceService.deleteThumbnail(courseId, actorId);
    }
}

