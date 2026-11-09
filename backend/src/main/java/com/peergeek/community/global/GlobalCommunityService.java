package com.peergeek.community.global;

import java.util.List;
import com.peergeek.common.PageResponse;
import com.peergeek.community.global.dto.AdminCommunityBlockedUserDto;
import com.peergeek.community.global.dto.AdminGlobalCommunityMessageDto;
import com.peergeek.community.global.dto.BlockCommunityUserRequest;
import com.peergeek.community.global.dto.CommunityUserBlockStatusDto;
import com.peergeek.community.global.dto.CreateGlobalMessageRequest;
import com.peergeek.community.global.dto.DeleteGlobalMessageRequest;
import com.peergeek.community.global.dto.GlobalCommunityMessageDto;

public interface GlobalCommunityService {

	List<GlobalCommunityMessageDto> getMessages(Long afterId, Long beforeId, int limit, Long currentUserId, boolean isAdmin);

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
