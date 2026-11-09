package com.peergeek.community.global;

import com.peergeek.community.common.PageResponse;
import com.peergeek.community.global.dto.*;

import java.util.List;

public interface GlobalCommunityService {

	List<GlobalCommunityMessageDto> getMessages(
			Long afterId, Long beforeId, int limit, Long currentUserId, boolean isAdmin);

	GlobalCommunityMessageDto postMessage(Long userId, CreateGlobalMessageRequest request);

	void deleteMessage(Long messageId, Long currentUserId, boolean isAdmin, DeleteGlobalMessageRequest request);

	CommunityUserBlockStatusDto getUserBlockStatus(Long userId);

	PageResponse<AdminGlobalCommunityMessageDto> getAdminMessages(
			Boolean includeDeleted, Long userId, String query, int page, int size);

	void adminDeleteMessage(Long messageId, Long adminUserId, DeleteGlobalMessageRequest request);

	AdminCommunityBlockedUserDto blockUser(Long targetUserId, Long adminUserId, BlockCommunityUserRequest request);

	void unblockUser(Long targetUserId, Long adminUserId, BlockCommunityUserRequest request);

	List<AdminCommunityBlockedUserDto> getBlockedUsers();
}

