package com.peergeek.directmessage.dto;
import com.peergeek.community.dto.CommunityAuthorDto;
import java.time.Instant;
public record DirectConversationDto(Long id, CommunityAuthorDto participant, DirectMessageDto lastMessage, long unreadCount, Instant updatedAt) {}
