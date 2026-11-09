package com.peergeek.community.domain.dto;

import com.peergeek.community.domain.MessageType;
import com.peergeek.community.dto.CommunityAuthorDto;
import java.time.Instant;

public record DomainChatMessageDto(
		Long id,
		Long domainId,
		CommunityAuthorDto sender,
		MessageType messageType,
		String content,
		Instant createdAt,
		boolean isSender
) {
}
