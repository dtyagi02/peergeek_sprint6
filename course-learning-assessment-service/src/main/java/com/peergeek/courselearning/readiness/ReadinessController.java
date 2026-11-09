package com.peergeek.courselearning.readiness;

import com.peergeek.courselearning.security.CurrentUserProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/instructor/courses/{courseId}/readiness")
public class ReadinessController {

    private final ReadinessService readinessService;
    private final CurrentUserProvider currentUserProvider;

    public ReadinessController(ReadinessService readinessService, CurrentUserProvider currentUserProvider) {
        this.readinessService = readinessService;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping("/runs")
    public ResponseEntity<ReadinessRunView> start(@PathVariable("courseId") long courseId) {
        long actorId = currentUserProvider.requireCurrentUser().id();
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(readinessService.start(courseId, actorId));
    }

    @GetMapping("/runs/{runId}")
    public ResponseEntity<ReadinessRunView> get(
            @PathVariable("courseId") long courseId,
            @PathVariable("runId") long runId) {
        long actorId = currentUserProvider.requireCurrentUser().id();
        return ResponseEntity.ok(readinessService.get(courseId, runId, actorId));
    }

    @GetMapping("/runs/latest")
    public ResponseEntity<ReadinessRunView> getLatest(@PathVariable("courseId") long courseId) {
        long actorId = currentUserProvider.requireCurrentUser().id();
        ReadinessRunView latest = readinessService.getLatest(courseId, actorId);
        return latest != null ? ResponseEntity.ok(latest) : ResponseEntity.noContent().build();
    }
}

