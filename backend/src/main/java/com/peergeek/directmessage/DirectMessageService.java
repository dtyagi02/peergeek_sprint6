package com.peergeek.directmessage;

import com.peergeek.common.PageResponse;
import com.peergeek.directmessage.dto.*;
import java.util.List;

public interface DirectMessageService {
	List<DirectConversationDto> listConversations(Long userId);
	DirectConversationDto getOrCreateConversation(Long userId, Long otherUserId);
	PageResponse<DirectMessageDto> getMessagesByConversation(Long userId, Long conversationId, int page, int size);
	PageResponse<DirectMessageDto> getMessages(Long userId, Long otherUserId, int page, int size);
	DirectMessageDto send(Long userId, Long otherUserId, SendDirectMessageRequest request);
	DirectMessageDto sendToConversation(Long userId, Long conversationId, SendDirectMessageRequest request);
	void markRead(Long userId, Long otherUserId, Long messageId);
	void markReadLatest(Long userId, Long conversationId);
	boolean isParticipant(Long userId, Long conversationId);
}
