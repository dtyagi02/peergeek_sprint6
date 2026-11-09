package com.peergeek.courselearning.analytics;

import com.peergeek.courselearning.security.CurrentUser;
import com.peergeek.courselearning.security.CurrentUserProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
