package com.peergeek.courselearning.course;

import com.peergeek.courselearning.common.CourseStatus;
import com.peergeek.courselearning.common.PageResponse;
import com.peergeek.courselearning.security.CurrentUserProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/courses")
@PreAuthorize("hasRole('ADMIN')")
public class AdminCourseController {

    private final CourseRepository courseRepository;
    private final CourseService courseService;
    private final CurrentUserProvider currentUserProvider;

    public AdminCourseController(
            CourseRepository courseRepository,
            CourseService courseService,
            CurrentUserProvider currentUserProvider) {
        this.courseRepository = courseRepository;
        this.courseService = courseService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    public ResponseEntity<PageResponse<CourseDto>> getCourses(
            @RequestParam(value = "status", required = false) CourseStatus status,
            @RequestParam(value = "query", required = false) String query,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        currentUserProvider.requireCurrentUser();

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<CourseEntity> coursePage = courseRepository.findForAdmin(status, query, pageRequest);

        var dtoList = coursePage.getContent().stream()
                .map(courseService::toDto)
                .toList();

        return ResponseEntity.ok(new PageResponse<>(
                dtoList,
                coursePage.getNumber(),
                coursePage.getSize(),
                coursePage.getTotalElements(),
                coursePage.getTotalPages()
        ));
    }

    @GetMapping("/{courseId}")
    public ResponseEntity<CourseDto> getCourse(@PathVariable("courseId") Long courseId) {
        currentUserProvider.requireCurrentUser();
        return ResponseEntity.ok(courseService.getCourseById(courseId, null));
    }

    @PatchMapping("/{courseId}/status")
    public ResponseEntity<CourseDto> updateCourseStatusPatch(
            @PathVariable("courseId") Long courseId,
            @RequestBody Map<String, String> request) {
        currentUserProvider.requireCurrentUser();
        String statusStr = request.get("status");
        CourseStatus status = CourseStatus.valueOf(statusStr.toUpperCase());
        return ResponseEntity.ok(courseService.updateCourseStatusByAdmin(courseId, status));
    }

    @PutMapping("/{courseId}/status")
    public ResponseEntity<CourseDto> updateCourseStatusPut(
            @PathVariable("courseId") Long courseId,
            @RequestBody Map<String, String> request) {
        return updateCourseStatusPatch(courseId, request);
    }
}

