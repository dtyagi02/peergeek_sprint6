package com.peergeek.doubtsession.controller;

import java.util.Map;
import com.peergeek.doubtsession.dto.DoubtSessionDto;
import com.peergeek.doubtsession.dto.DoubtSessionSettingsDto;
import com.peergeek.doubtsession.dto.UpdateDoubtSessionSettingsRequest;
import com.peergeek.doubtsession.security.CurrentUserProvider;
import com.peergeek.doubtsession.service.DoubtSessionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/instructor")
public class InstructorDoubtSessionController {

	private final DoubtSessionService doubtSessionService;
	private final CurrentUserProvider currentUserProvider;

	public InstructorDoubtSessionController(
			DoubtSessionService doubtSessionService,
			CurrentUserProvider currentUserProvider) {
		this.doubtSessionService = doubtSessionService;
		this.currentUserProvider = currentUserProvider;
	}

	@GetMapping("/courses/{courseId}/doubt-session-settings")
	public DoubtSessionSettingsDto getSettings(@PathVariable Long courseId) {
		Long instructorId = currentUserProvider.requireCurrentUser().id();
		return doubtSessionService.getOrCreateSettings(courseId, instructorId);
	}

	@PutMapping("/courses/{courseId}/doubt-session-settings")
	public DoubtSessionSettingsDto updateSettings(
			@PathVariable Long courseId,
			@Valid @RequestBody UpdateDoubtSessionSettingsRequest request) {
		Long instructorId = currentUserProvider.requireCurrentUser().id();
		return doubtSessionService.updateSettings(courseId, instructorId, request);
	}

	@PatchMapping("/doubt-sessions/availability")
	public Map<String, Object> updateAvailability(@RequestBody Map<String, Boolean> body) {
		Long instructorId = currentUserProvider.requireCurrentUser().id();
		boolean available = body != null && Boolean.TRUE.equals(body.get("available"));
		doubtSessionService.updateInstructorAvailability(instructorId, available);
		return Map.of("available", available);
	}

	@PutMapping("/doubt-sessions/availability")
	public Map<String, Object> putAvailability(@RequestBody Map<String, Boolean> body) {
		return updateAvailability(body);
	}

	@GetMapping("/doubt-sessions/active")
	public ResponseEntity<DoubtSessionDto> getActiveSession() {
		Long instructorId = currentUserProvider.requireCurrentUser().id();
		DoubtSessionDto session = doubtSessionService.getActiveInstructorRequest(instructorId);
		if (session == null) {
			return ResponseEntity.noContent().build();
		}
		return ResponseEntity.ok(session);
	}

	@PostMapping("/doubt-sessions/{sessionId}/accept")
	public DoubtSessionDto acceptSession(@PathVariable String sessionId) {
		Long instructorId = currentUserProvider.requireCurrentUser().id();
		return doubtSessionService.acceptSession(instructorId, sessionId);
	}

	@PostMapping("/doubt-sessions/{sessionId}/reject")
	public DoubtSessionDto rejectSession(@PathVariable String sessionId) {
		Long instructorId = currentUserProvider.requireCurrentUser().id();
		return doubtSessionService.rejectSession(instructorId, sessionId);
	}
}

