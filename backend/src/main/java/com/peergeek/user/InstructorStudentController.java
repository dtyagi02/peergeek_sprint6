package com.peergeek.user;

import com.peergeek.security.CurrentUserProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/instructor")
public class InstructorStudentController {
	private final UserService userService;
	private final CurrentUserProvider currentUserProvider;

	public InstructorStudentController(UserService userService, CurrentUserProvider currentUserProvider) {
		this.userService = userService;
		this.currentUserProvider = currentUserProvider;
	}

	@GetMapping("/courses/{courseId}/students/{studentId}")
	public CourseStudentDetailDto getCourseStudentDetail(
			@PathVariable Long courseId, @PathVariable Long studentId) {
		return userService.getCourseStudentDetail(
				courseId, studentId, currentUserProvider.requireCurrentUser().id());
	}
}
