package com.peergeek.community.websocket;

public record CommunityRealtimeEvent<T>(
		String type,
		T payload
) {
	public static final String MESSAGE_CREATED = "MESSAGE_CREATED";
	public static final String MESSAGE_DELETED = "MESSAGE_DELETED";
	public static final String USER_BLOCKED = "USER_BLOCKED";
	public static final String USER_UNBLOCKED = "USER_UNBLOCKED";
}
