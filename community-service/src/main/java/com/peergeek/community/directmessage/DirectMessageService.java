package com.peergeek.community.directmessage;

import com.peergeek.community.common.PageResponse;
import com.peergeek.community.directmessage.dto.DirectConversationDto;
import com.peergeek.community.directmessage.dto.DirectMessageDto;
import com.peergeek.community.directmessage.dto.SendDirectMessageRequest;

import java.util.List;

public interface DirectMessageService {
	DirectConversationDto getOrCreateConversation(Long userId, Long otherUserId);
	PageResponse<DirectMessageDto> getMessages(Long userId, Long otherUserId, int page, int size);
	PageResponse<DirectMessageDto> getMessagesByConversation(Long userId, Long conversationId, int page, int size);
	DirectMessageDto send(Long userId, Long otherUserId, SendDirectMessageRequest request);
	DirectMessageDto sendToConversation(Long userId, Long conversationId, SendDirectMessageRequest request);
	List<DirectConversationDto> listConversations(Long userId);
	void markRead(Long userId, Long otherUserId, Long messageId);
	void markReadLatest(Long userId, Long conversationId);
	boolean isParticipant(Long userId, Long conversationId);
}

