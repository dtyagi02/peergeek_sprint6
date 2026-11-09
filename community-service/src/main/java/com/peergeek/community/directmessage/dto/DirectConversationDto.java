package com.peergeek.community.directmessage.dto;

import java.time.Instant;
import com.peergeek.community.posts.dto.CommunityAuthorDto;

public record DirectConversationDto(
		Long id,
		CommunityAuthorDto participant,
		DirectMessageDto lastMessage,
		long unreadCount,
		Instant updatedAt) {
}

