package com.peergeek.community.global;

import com.peergeek.community.common.PageResponse;
import com.peergeek.community.common.UserMode;
import com.peergeek.community.common.UserRole;
import com.peergeek.community.exception.BadRequestException;
import com.peergeek.community.exception.ConflictException;
import com.peergeek.community.exception.ForbiddenException;
import com.peergeek.community.exception.NotFoundException;
import com.peergeek.community.global.dto.*;
import com.peergeek.community.outbox.OutboxEventWriter;
import com.peergeek.community.profile.CommunityUserProfileEntity;
import com.peergeek.community.profile.CommunityUserProfileRepository;
import com.peergeek.community.websocket.CommunityRealtimeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class GlobalCommunityServiceImpl implements GlobalCommunityService {

	private static final Logger log = LoggerFactory.getLogger(GlobalCommunityServiceImpl.class);

	private final GlobalCommunityMessageRepository messageRepository;
	private final GlobalCommunityBlockRepository blockRepository;
	private final CommunityUserProfileRepository userProfileRepository;
	private final OutboxEventWriter outboxEventWriter;
	private final SimpMessagingTemplate messagingTemplate;

	public GlobalCommunityServiceImpl(
			GlobalCommunityMessageRepository messageRepository,
			GlobalCommunityBlockRepository blockRepository,
			CommunityUserProfileRepository userProfileRepository,
			OutboxEventWriter outboxEventWriter,
			SimpMessagingTemplate messagingTemplate) {
		this.messageRepository = messageRepository;
		this.blockRepository = blockRepository;
		this.userProfileRepository = userProfileRepository;
		this.outboxEventWriter = outboxEventWriter;
		this.messagingTemplate = messagingTemplate;
	}

	@Override
	@Transactional(readOnly = true)
	public List<GlobalCommunityMessageDto> getMessages(
			Long afterId, Long beforeId, int limit, Long currentUserId, boolean isAdmin) {
		int safeLimit = Math.max(1, Math.min(100, limit > 0 ? limit : 50));
		Pageable pageable = PageRequest.of(0, safeLimit);

		List<GlobalCommunityMessageEntity> entities;
		if (afterId != null && afterId > 0) {
			entities = messageRepository.findNewerActive(afterId, pageable);
		} else if (beforeId != null && beforeId > 0) {
			List<GlobalCommunityMessageEntity> desc = messageRepository.findOlderActive(beforeId, pageable);
			entities = new ArrayList<>(desc);
			Collections.reverse(entities);
		} else {
			List<GlobalCommunityMessageEntity> desc = messageRepository.findLatestActive(pageable);
			entities = new ArrayList<>(desc);
			Collections.reverse(entities);
		}

		return entities.stream()
				.map(e -> toDto(e, currentUserId, isAdmin))
				.toList();
	}

	@Override
	@Transactional
	public GlobalCommunityMessageDto postMessage(Long userId, CreateGlobalMessageRequest request) {
		if (blockRepository.isUserBlocked(userId)) {
			throw new ForbiddenException("You are blocked from participating in the global community.");
		}

		String trimmedContent = request.content() != null ? request.content().trim() : "";
		if (trimmedContent.isEmpty()) {
			throw new BadRequestException("Message content cannot be blank.");
		}

		CommunityUserProfileEntity user = userProfileRepository.findById(userId).orElse(null);
		if (user != null && !user.isEnabled()) {
			throw new ForbiddenException("Your account is disabled.");
		}

		String userName = user != null ? user.getDisplayName() : "User " + userId;
		String userUsername = user != null ? user.getUsername() : "user_" + userId;
		String userAvatar = user != null ? user.getAvatarUrl() : null;
		UserRole userRole = user != null ? user.getRole() : UserRole.LEARNER;
		UserMode userMode = user != null ? user.getMode() : UserMode.LEARNER;

		GlobalCommunityMessageEntity message = new GlobalCommunityMessageEntity(
				userId,
				userName,
				userUsername,
				userAvatar,
				userRole,
				userMode,
				trimmedContent
		);
		GlobalCommunityMessageEntity saved = messageRepository.save(message);

		GlobalCommunityMessageDto dto = toDto(saved, userId, userRole == UserRole.ADMIN);

		outboxEventWriter.writeEvent(
				"peergeek.community.message-created.v1",
				"GlobalMessageCreated",
				Map.of(
						"channel", "global",
						"messageId", saved.getId(),
						"authorId", userId,
						"authorName", userName,
						"content", trimmedContent,
						"createdAt", saved.getCreatedAt().toString()
				)
		);

		try {
			broadcastGlobal(new CommunityRealtimeEvent<>(CommunityRealtimeEvent.MESSAGE_CREATED, dto));
			messagingTemplate.convertAndSend("/topic/admin.community",
					new CommunityRealtimeEvent<>(CommunityRealtimeEvent.MESSAGE_CREATED, toAdminDto(saved)));
		} catch (Exception e) {
			log.warn("Failed to broadcast global community message id={}: {}", saved.getId(), e.getMessage());
		}
		return dto;
	}

	@Override
	@Transactional
	public void deleteMessage(Long messageId, Long currentUserId, boolean isAdmin, DeleteGlobalMessageRequest request) {
		GlobalCommunityMessageEntity message = messageRepository.findById(messageId)
				.orElseThrow(() -> new NotFoundException("Message not found: " + messageId));

		if (message.isDeleted()) {
			return; // Idempotent
		}

		boolean isAuthor = message.getUserId().equals(currentUserId);
		if (!isAdmin && !isAuthor) {
			throw new ForbiddenException("You do not have permission to delete this message.");
		}

		CommunityUserProfileEntity deletedByUser = userProfileRepository.findById(currentUserId).orElse(null);
		String deletedByName = deletedByUser != null ? deletedByUser.getDisplayName() : (isAdmin ? "Administrator" : "Author");

		String reason = request != null && request.reason() != null && !request.reason().isBlank()
				? request.reason().trim()
				: (isAuthor ? "Deleted by author" : "Deleted by administrator");

		message.setDeletedAt(Instant.now());
		message.setDeletedBy(currentUserId);
		message.setDeletedByName(deletedByName);
		message.setDeleteReason(reason);
		messageRepository.save(message);

		outboxEventWriter.writeEvent(
				"peergeek.community.message-deleted.v1",
				"GlobalMessageDeleted",
				Map.of(
						"channel", "global",
						"messageId", messageId,
						"deletedBy", currentUserId,
						"authorId", message.getUserId(),
						"isAdmin", isAdmin,
						"reason", reason,
						"deletedAt", Instant.now().toString()
				)
		);

		try {
			broadcastGlobal(new CommunityRealtimeEvent<>(CommunityRealtimeEvent.MESSAGE_DELETED, Map.of("messageId", messageId)));
			messagingTemplate.convertAndSend("/topic/admin.community",
					new CommunityRealtimeEvent<>(CommunityRealtimeEvent.MESSAGE_DELETED,
							Map.of("messageId", messageId, "deletedBy", currentUserId, "reason", reason)));
		} catch (Exception e) {
			log.warn("Failed to broadcast message deletion id={}: {}", messageId, e.getMessage());
		}
	}

	@Override
	@Transactional(readOnly = true)
	public CommunityUserBlockStatusDto getUserBlockStatus(Long userId) {
		return blockRepository.findActiveBlockByUserId(userId)
				.map(b -> new CommunityUserBlockStatusDto(true, b.getReason(), b.getCreatedAt()))
				.orElseGet(() -> new CommunityUserBlockStatusDto(false, null, null));
	}

	@Override
	@Transactional(readOnly = true)
	public PageResponse<AdminGlobalCommunityMessageDto> getAdminMessages(
			Boolean includeDeleted, Long userId, String query, int page, int size) {
		int safePage = Math.max(0, page);
		int safeSize = Math.max(1, Math.min(100, size > 0 ? size : 20));
		Pageable pageable = PageRequest.of(safePage, safeSize);

		String safeQuery = (query != null && !query.trim().isEmpty()) ? query.trim() : null;
		Page<GlobalCommunityMessageEntity> messagePage =
				messageRepository.findForAdmin(includeDeleted, userId, safeQuery, pageable);

		List<AdminGlobalCommunityMessageDto> dtos = messagePage.getContent().stream()
				.map(this::toAdminDto)
				.toList();

		return new PageResponse<>(
				dtos,
				messagePage.getNumber(),
				messagePage.getSize(),
				messagePage.getTotalElements(),
				messagePage.getTotalPages());
	}

	@Override
	@Transactional
	public void adminDeleteMessage(Long messageId, Long adminUserId, DeleteGlobalMessageRequest request) {
		deleteMessage(messageId, adminUserId, true, request);
	}

	@Override
	@Transactional
	public AdminCommunityBlockedUserDto blockUser(Long targetUserId, Long adminUserId, BlockCommunityUserRequest request) {
		if (targetUserId.equals(adminUserId)) {
			throw new BadRequestException("Administrators cannot block themselves from the community.");
		}

		CommunityUserProfileEntity targetUser = userProfileRepository.findById(targetUserId).orElse(null);
		if (targetUser != null && targetUser.getRole() == UserRole.ADMIN) {
			throw new BadRequestException("Cannot block administrator accounts from the community.");
		}

		if (blockRepository.isUserBlocked(targetUserId)) {
			throw new ConflictException("User is already blocked from the global community.");
		}

		CommunityUserProfileEntity adminUser = userProfileRepository.findById(adminUserId).orElse(null);
		String adminName = adminUser != null ? adminUser.getDisplayName() : "Administrator";

		String reason = request != null && request.reason() != null ? request.reason().trim() : "Violated community guidelines";
		String targetName = targetUser != null ? targetUser.getDisplayName() : "User " + targetUserId;
		String targetEmail = targetUser != null ? targetUser.getEmail() : "user" + targetUserId + "@peergeek.com";
		String targetUsername = targetUser != null ? targetUser.getUsername() : "user_" + targetUserId;
		UserRole targetRole = targetUser != null ? targetUser.getRole() : UserRole.LEARNER;

		GlobalCommunityBlockEntity block = new GlobalCommunityBlockEntity(
				targetUserId,
				targetName,
				targetEmail,
				targetUsername,
				targetRole,
				adminUserId,
				adminName,
				reason
		);
		GlobalCommunityBlockEntity saved = blockRepository.save(block);

		outboxEventWriter.writeEvent(
				"peergeek.community.user-blocked.v1",
				"CommunityUserBlocked",
				Map.of(
						"targetUserId", targetUserId,
						"targetEmail", targetEmail,
						"blockedBy", adminUserId,
						"reason", reason,
						"blockedAt", saved.getCreatedAt().toString()
				)
		);

		AdminCommunityBlockedUserDto dto = toBlockedDto(saved);
		try {
			broadcastGlobal(new CommunityRealtimeEvent<>(CommunityRealtimeEvent.USER_BLOCKED,
							Map.of("userId", targetUserId, "reason", reason)));
			messagingTemplate.convertAndSend("/topic/admin.community",
					new CommunityRealtimeEvent<>(CommunityRealtimeEvent.USER_BLOCKED, dto));
		} catch (Exception e) {
			log.warn("Failed to broadcast user block userId={}: {}", targetUserId, e.getMessage());
		}

		return dto;
	}

	@Override
	@Transactional
	public void unblockUser(Long targetUserId, Long adminUserId, BlockCommunityUserRequest request) {
		GlobalCommunityBlockEntity activeBlock = blockRepository.findActiveBlockByUserId(targetUserId)
				.orElseThrow(() -> new NotFoundException("No active community block found for user: " + targetUserId));

		CommunityUserProfileEntity adminUser = userProfileRepository.findById(adminUserId).orElse(null);
		String reason = request != null && request.reason() != null ? request.reason().trim() : "Administrative unblock";

		activeBlock.setUnblockedAt(Instant.now());
		activeBlock.setUnblockedBy(adminUserId);
		activeBlock.setUnblockReason(reason);
		blockRepository.save(activeBlock);

		outboxEventWriter.writeEvent(
				"peergeek.community.user-unblocked.v1",
				"CommunityUserUnblocked",
				Map.of(
						"targetUserId", targetUserId,
						"unblockedBy", adminUserId,
						"reason", reason,
						"unblockedAt", Instant.now().toString()
				)
		);

		try {
			broadcastGlobal(new CommunityRealtimeEvent<>(CommunityRealtimeEvent.USER_UNBLOCKED, Map.of("userId", targetUserId)));
			messagingTemplate.convertAndSend("/topic/admin.community",
					new CommunityRealtimeEvent<>(CommunityRealtimeEvent.USER_UNBLOCKED, Map.of("userId", targetUserId)));
		} catch (Exception e) {
			log.warn("Failed to broadcast user unblock userId={}: {}", targetUserId, e.getMessage());
		}
	}

	@Override
	@Transactional(readOnly = true)
	public List<AdminCommunityBlockedUserDto> getBlockedUsers() {
		return blockRepository.findAllActiveBlocks().stream()
				.map(this::toBlockedDto)
				.toList();
	}

	private GlobalCommunityMessageDto toDto(
			GlobalCommunityMessageEntity entity, Long currentUserId, boolean isAdmin) {
		boolean isOwn = currentUserId != null && currentUserId.equals(entity.getUserId());
		boolean canDelete = isOwn || isAdmin;

		return new GlobalCommunityMessageDto(
				entity.getId(),
				entity.getUserId(),
				entity.getUserDisplayName(),
				entity.getUserUsername(),
				entity.getUserRole(),
				entity.getUserMode(),
				entity.getContent(),
				entity.getCreatedAt(),
				isOwn,
				canDelete);
	}

	private AdminGlobalCommunityMessageDto toAdminDto(GlobalCommunityMessageEntity entity) {
		return new AdminGlobalCommunityMessageDto(
				entity.getId(),
				entity.getUserId(),
				entity.getUserDisplayName(),
				entity.getUserUsername() != null ? entity.getUserUsername() + "@peergeek.com" : "",
				entity.getUserUsername(),
				entity.getUserRole(),
				entity.getUserMode(),
				entity.getContent(),
				entity.getCreatedAt(),
				entity.getUpdatedAt(),
				entity.isDeleted(),
				entity.getDeletedAt(),
				entity.getDeletedBy(),
				entity.getDeletedByName(),
				entity.getDeleteReason());
	}

	private AdminCommunityBlockedUserDto toBlockedDto(GlobalCommunityBlockEntity block) {
		return new AdminCommunityBlockedUserDto(
				block.getId(),
				block.getUserId(),
				block.getUserName(),
				block.getUserEmail(),
				block.getUserUsername(),
				block.getUserRole(),
				block.getBlockedBy(),
				block.getBlockedByName(),
				block.getReason(),
				block.getCreatedAt());
	}

	private void broadcastGlobal(CommunityRealtimeEvent<?> event) {
		messagingTemplate.convertAndSend("/topic/community/global", event);
		messagingTemplate.convertAndSend("/topic/community.global", event);
	}
}

