package com.peergeek.courselearning.enrollment;

import com.peergeek.courselearning.common.PageResponse;
import com.peergeek.courselearning.security.CurrentUser;
import com.peergeek.courselearning.security.CurrentUserProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/learning/courses")
public class LearningEnrollmentController {

    private final EnrollmentService enrollmentService;
    private final CurrentUserProvider currentUserProvider;

    public LearningEnrollmentController(
            EnrollmentService enrollmentService,
            CurrentUserProvider currentUserProvider) {
        this.enrollmentService = enrollmentService;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping("/{courseId}/enroll")
    public ResponseEntity<EnrollmentDto> enroll(@PathVariable("courseId") long courseId) {
        CurrentUser user = currentUserProvider.requireCurrentUser();
        EnrollmentDto dto = enrollmentService.enroll(courseId, user.id());
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(dto);
    }

    @DeleteMapping("/{courseId}/enrollment")
    public ResponseEntity<Void> withdraw(@PathVariable("courseId") long courseId) {
        CurrentUser user = currentUserProvider.requireCurrentUser();
        enrollmentService.withdraw(courseId, user.id());
        return ResponseEntity.noContent()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .build();
    }

    @GetMapping("/{courseId}/enrollment")
    public ResponseEntity<EnrollmentStatusDto> getEnrollmentStatus(@PathVariable("courseId") long courseId) {
        CurrentUser user = currentUserProvider.requireCurrentUser();
        EnrollmentStatusDto dto = enrollmentService.getEnrollmentStatus(courseId, user.id());
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(dto);
    }

    @GetMapping
    public ResponseEntity<PageResponse<MyCourseCardDto>> getMyCourses(
            @RequestParam(value = "status", required = false, defaultValue = "all") String status,
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "page", required = false, defaultValue = "0") int page,
            @RequestParam(value = "size", required = false, defaultValue = "20") int size,
            @RequestParam(value = "sort", required = false, defaultValue = "lastAccessedAt,desc") String sort) {
        CurrentUser user = currentUserProvider.requireCurrentUser();
        PageResponse<MyCourseCardDto> response = enrollmentService.getMyCourses(
                user.id(), status, q, page, size, sort);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(response);
    }
}

