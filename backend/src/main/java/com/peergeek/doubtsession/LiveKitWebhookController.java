package com.peergeek.doubtsession;

import livekit.LivekitWebhook.WebhookEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/doubt-sessions")
public class LiveKitWebhookController {
	private static final Logger log = LoggerFactory.getLogger(LiveKitWebhookController.class);

	private final LiveKitService liveKitService;
	private final DoubtSessionService doubtSessionService;

	public LiveKitWebhookController(
			LiveKitService liveKitService,
			DoubtSessionService doubtSessionService) {
		this.liveKitService = liveKitService;
		this.doubtSessionService = doubtSessionService;
	}

	@PostMapping("/webhook")
	public ResponseEntity<String> handleLiveKitWebhook(
			@RequestBody String rawBody,
			@RequestHeader(value = "Authorization", required = false) String authHeader) {
		try {
			WebhookEvent event = liveKitService.receiveWebhook(rawBody, authHeader);
			String eventType = event.getEvent();
			String roomName = event.hasRoom() ? event.getRoom().getName() : null;

			log.info("Received LiveKit webhook event={} room={}", eventType, roomName);

			if (roomName != null) {
				switch (eventType) {
					case "participant_joined" -> {
						if (event.hasParticipant()) {
							doubtSessionService.handleParticipantJoined(roomName, event.getParticipant().getIdentity());
						}
					}
					case "participant_left" -> {
						if (event.hasParticipant()) {
							doubtSessionService.handleParticipantLeft(roomName, event.getParticipant().getIdentity());
						}
					}
					case "room_finished" -> doubtSessionService.handleRoomFinished(roomName);
					default -> log.debug("Unhandled LiveKit webhook event: {}", eventType);
				}
			}

			return ResponseEntity.ok("OK");
		} catch (IllegalArgumentException ex) {
			log.warn("LiveKit webhook signature validation failed: {}", ex.getMessage());
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid signature");
		} catch (Exception ex) {
			log.error("Error processing LiveKit webhook: {}", ex.getMessage(), ex);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error processing event");
		}
	}
}
