package com.peergeek.analytics;

import com.peergeek.analytics.dto.InstructorCourseAnalyticsDto;
import com.peergeek.security.CurrentUser;
import com.peergeek.security.CurrentUserProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/instructor")
public class InstructorAnalyticsController {

	private final AnalyticsService analyticsService;
	private final CurrentUserProvider currentUserProvider;

	public InstructorAnalyticsController(
			AnalyticsService analyticsService,
			CurrentUserProvider currentUserProvider) {
		this.analyticsService = analyticsService;
		this.currentUserProvider = currentUserProvider;
	}

	@GetMapping("/courses/{courseId}/analytics")
	public ResponseEntity<InstructorCourseAnalyticsDto> getCourseAnalytics(@PathVariable long courseId) {
		CurrentUser currentUser = currentUserProvider.requireCurrentUser();
		InstructorCourseAnalyticsDto analytics = analyticsService.getInstructorCourseAnalytics(courseId, currentUser.id());
		return ResponseEntity.ok()
				.header(HttpHeaders.CACHE_CONTROL, "no-store")
				.body(analytics);
	}
}
