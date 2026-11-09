package com.peergeek.courselearning.instructor;

import com.peergeek.courselearning.security.CurrentUserProvider;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/instructor")
public class InstructorStudentController {

    private final InstructorStudentService studentService;
    private final CurrentUserProvider currentUserProvider;

    public InstructorStudentController(
            InstructorStudentService studentService,
            CurrentUserProvider currentUserProvider) {
        this.studentService = studentService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/courses/{courseId}/students/{studentId}")
    public CourseStudentDetailDto getCourseStudentDetail(
            @PathVariable Long courseId, @PathVariable Long studentId) {
        return studentService.getCourseStudentDetail(
                courseId, studentId, currentUserProvider.requireCurrentUser().id());
    }
}
