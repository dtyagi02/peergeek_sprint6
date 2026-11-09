package com.peergeek.doubtsession;

import io.livekit.server.AccessToken;
import io.livekit.server.RoomJoin;
import io.livekit.server.RoomName;
import io.livekit.server.WebhookReceiver;
import livekit.LivekitWebhook.WebhookEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LiveKitService {
	private static final Logger log = LoggerFactory.getLogger(LiveKitService.class);

	private final LiveKitSettings settings;

	public LiveKitService(LiveKitSettings settings) {
		this.settings = settings;
	}

	public String getLiveKitUrl() {
		return settings.getUrl();
	}

	public String createJoinToken(
			String roomName,
			String participantIdentity,
			String participantName,
			int durationMinutes) {
		AccessToken token = new AccessToken(settings.getApiKey(), settings.getApiSecret());
		token.setIdentity(participantIdentity);
		token.setName(participantName);
		token.addGrants(new RoomJoin(true), new RoomName(roomName));
		// Provide token validity with grace buffer beyond max session duration
		long ttlMillis = (durationMinutes + 15L) * 60L * 1000L;
		token.setTtl(ttlMillis);
		return token.toJwt();
	}

	public WebhookEvent receiveWebhook(String rawBody, String authHeader) {
		WebhookReceiver receiver = new WebhookReceiver(settings.getApiKey(), settings.getApiSecret());
		return receiver.receive(rawBody, authHeader);
	}
}
