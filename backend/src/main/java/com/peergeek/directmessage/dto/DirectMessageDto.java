package com.peergeek.directmessage.dto;
import com.peergeek.community.dto.CommunityAuthorDto;
import java.time.Instant;
public record DirectMessageDto(Long id, Long conversationId, CommunityAuthorDto sender, String content, Instant createdAt, boolean own) {}
