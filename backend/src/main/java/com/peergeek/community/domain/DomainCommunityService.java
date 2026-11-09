package com.peergeek.community.domain;

import com.peergeek.common.PageResponse;
import com.peergeek.community.domain.dto.DomainChatMessageDto;
import com.peergeek.community.domain.dto.DomainCommunityDto;
import com.peergeek.community.domain.dto.DomainMemberDto;
import com.peergeek.community.domain.dto.SendDomainMessageRequest;
import java.util.List;
import org.springframework.data.domain.Pageable;

public interface DomainCommunityService {

	DomainCommunityDto getDomainCommunity(String slug, Long currentUserId);

	List<DomainCommunityDto> listDomainCommunities(Long currentUserId);

	DomainCommunityDto joinCommunity(String slug, Long userId);

	DomainCommunityDto leaveCommunity(String slug, Long userId);

	PageResponse<DomainMemberDto> getMembers(String slug, Pageable pageable);

	List<DomainChatMessageDto> getMessages(String slug, Long beforeId, int limit, Long currentUserId);

	List<DomainChatMessageDto> getNewMessagesAfter(String slug, Long afterId, Long currentUserId);

	DomainChatMessageDto sendMessage(String slug, Long userId, SendDomainMessageRequest request);
	void deleteMessage(String slug, Long messageId, Long currentUserId, boolean isAdmin);
}
