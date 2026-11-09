package com.peergeek.courselearning.course;

import com.peergeek.courselearning.security.CurrentUserProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/instructor/courses")
public class CourseController {

    private final CourseService courseService;
    private final CurrentUserProvider currentUserProvider;

    public CourseController(CourseService courseService, CurrentUserProvider currentUserProvider) {
        this.courseService = courseService;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping
    public ResponseEntity<CourseDto> createCourse(@RequestBody CourseCreateRequest request) {
        Long ownerId = currentUserProvider.requireCurrentUser().id();
        return ResponseEntity.status(HttpStatus.CREATED).body(courseService.createCourse(ownerId, request));
    }

    @GetMapping
    public ResponseEntity<List<CourseListItem>> getInstructorCourses() {
        Long ownerId = currentUserProvider.requireCurrentUser().id();
        return ResponseEntity.ok(courseService.getInstructorCourses(ownerId));
    }

    @GetMapping("/{courseId}")
    public ResponseEntity<CourseDto> getCourseById(@PathVariable("courseId") Long courseId) {
        Long userId = currentUserProvider.requireCurrentUser().id();
        return ResponseEntity.ok(courseService.getCourseById(courseId, userId));
    }

    @PatchMapping("/{courseId}")
    public ResponseEntity<CourseDto> updateCourse(
            @PathVariable("courseId") Long courseId,
            @RequestBody CoursePatchRequest request) {
        Long ownerId = currentUserProvider.requireCurrentUser().id();
        return ResponseEntity.ok(courseService.updateCourse(courseId, ownerId, request));
    }

    @DeleteMapping("/{courseId}")
    public ResponseEntity<Void> deleteCourse(@PathVariable("courseId") Long courseId) {
        Long ownerId = currentUserProvider.requireCurrentUser().id();
        courseService.deleteCourse(courseId, ownerId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{courseId}/outline")
    public ResponseEntity<CourseOutline> getCourseOutline(@PathVariable("courseId") Long courseId) {
        Long userId = currentUserProvider.requireCurrentUser().id();
        return ResponseEntity.ok(courseService.getCourseOutline(courseId, userId));
    }

    @PostMapping("/{courseId}/publish")
    public ResponseEntity<CourseDto> publishCourse(@PathVariable("courseId") Long courseId) {
        Long ownerId = currentUserProvider.requireCurrentUser().id();
        return ResponseEntity.ok(courseService.publishCourse(courseId, ownerId));
    }

    @PostMapping("/{courseId}/unpublish")
    public ResponseEntity<CourseDto> unpublishCourse(@PathVariable("courseId") Long courseId) {
        Long ownerId = currentUserProvider.requireCurrentUser().id();
        return ResponseEntity.ok(courseService.unpublishCourse(courseId, ownerId));
    }
}

