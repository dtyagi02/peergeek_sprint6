package com.peergeek.doubtsession.dto;

public record LiveKitTokenDto(
		String token,
		String serverUrl,
		String roomName
) {
}
