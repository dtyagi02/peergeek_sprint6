package com.peergeek.doubtsession.controller;

import com.peergeek.doubtsession.dto.CourseDoubtSessionAvailabilityDto;
import com.peergeek.doubtsession.dto.CreateDoubtSessionRequest;
import com.peergeek.doubtsession.dto.DoubtSessionDto;
import com.peergeek.doubtsession.dto.LiveKitTokenDto;
import com.peergeek.doubtsession.security.CurrentUser;
import com.peergeek.doubtsession.security.CurrentUserProvider;
import com.peergeek.doubtsession.service.DoubtSessionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class DoubtSessionController {

	private final DoubtSessionService doubtSessionService;
	private final CurrentUserProvider currentUserProvider;

	public DoubtSessionController(
			DoubtSessionService doubtSessionService,
			CurrentUserProvider currentUserProvider) {
		this.doubtSessionService = doubtSessionService;
		this.currentUserProvider = currentUserProvider;
	}

	@GetMapping("/api/v1/public/courses/{courseId}/doubt-session-availability")
	public CourseDoubtSessionAvailabilityDto getCourseAvailability(@PathVariable Long courseId) {
		Long currentUserId = currentUserProvider.currentUser()
				.map(CurrentUser::id)
				.orElse(null);
		return doubtSessionService.getCourseAvailability(courseId, currentUserId);
	}

	@PostMapping("/api/v1/doubt-sessions/request")
	public DoubtSessionDto requestSession(@RequestBody CreateDoubtSessionRequest request) {
		Long studentUserId = currentUserProvider.requireCurrentUser().id();
		return doubtSessionService.requestSession(studentUserId, request);
	}

	@PostMapping("/api/v1/doubt-sessions/{sessionId}/cancel")
	public DoubtSessionDto cancelSession(@PathVariable String sessionId) {
		Long studentUserId = currentUserProvider.requireCurrentUser().id();
		return doubtSessionService.cancelSession(studentUserId, sessionId);
	}

	@GetMapping("/api/v1/doubt-sessions/{sessionId}")
	public DoubtSessionDto getSession(@PathVariable String sessionId) {
		Long userId = currentUserProvider.requireCurrentUser().id();
		return doubtSessionService.getSession(userId, sessionId);
	}

	@GetMapping("/api/v1/doubt-sessions/active")
	public ResponseEntity<DoubtSessionDto> getActiveSession() {
		Long userId = currentUserProvider.requireCurrentUser().id();
		DoubtSessionDto session = doubtSessionService.getActiveSessionForUser(userId);
		if (session == null) {
			return ResponseEntity.noContent().build();
		}
		return ResponseEntity.ok(session);
	}

	@GetMapping("/api/v1/doubt-sessions/{sessionId}/token")
	public LiveKitTokenDto getJoinToken(@PathVariable String sessionId) {
		Long userId = currentUserProvider.requireCurrentUser().id();
		return doubtSessionService.getJoinToken(userId, sessionId);
	}

	@PostMapping("/api/v1/doubt-sessions/{sessionId}/end")
	public DoubtSessionDto endSession(@PathVariable String sessionId) {
		Long userId = currentUserProvider.requireCurrentUser().id();
		return doubtSessionService.endSession(userId, sessionId);
	}
}

