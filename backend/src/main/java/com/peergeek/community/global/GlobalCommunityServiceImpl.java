package com.peergeek.community.global;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import com.peergeek.audit.AuditAction;
import com.peergeek.audit.AuditEvent;
import com.peergeek.audit.AuditService;
import com.peergeek.common.PageResponse;
import com.peergeek.community.global.dto.AdminCommunityBlockedUserDto;
import com.peergeek.community.global.dto.AdminGlobalCommunityMessageDto;
import com.peergeek.community.global.dto.BlockCommunityUserRequest;
import com.peergeek.community.global.dto.CommunityUserBlockStatusDto;
import com.peergeek.community.global.dto.CreateGlobalMessageRequest;
import com.peergeek.community.global.dto.DeleteGlobalMessageRequest;
import com.peergeek.community.global.dto.GlobalCommunityMessageDto;
import com.peergeek.exception.BadRequestException;
import com.peergeek.exception.ConflictException;
import com.peergeek.exception.ForbiddenException;
import com.peergeek.exception.NotFoundException;
import com.peergeek.user.UserEntity;
import com.peergeek.user.UserRepository;
import com.peergeek.user.UserRole;
import com.peergeek.community.websocket.CommunityRealtimeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GlobalCommunityServiceImpl implements GlobalCommunityService {

	private static final Logger log = LoggerFactory.getLogger(GlobalCommunityServiceImpl.class);

	private final GlobalCommunityMessageRepository messageRepository;
	private final GlobalCommunityBlockRepository blockRepository;
	private final UserRepository userRepository;
	private final AuditService auditService;
	private final SimpMessagingTemplate messagingTemplate;

	public GlobalCommunityServiceImpl(
			GlobalCommunityMessageRepository messageRepository,
			GlobalCommunityBlockRepository blockRepository,
			UserRepository userRepository,
			AuditService auditService,
			SimpMessagingTemplate messagingTemplate) {
		this.messageRepository = messageRepository;
		this.blockRepository = blockRepository;
		this.userRepository = userRepository;
		this.auditService = auditService;
		this.messagingTemplate = messagingTemplate;
	}

	@Override
	@Transactional(readOnly = true)
	public List<GlobalCommunityMessageDto> getMessages(
			Long afterId, Long beforeId, int limit, Long currentUserId, boolean isAdmin) {
		int safeLimit = Math.clamp(limit > 0 ? limit : 50, 1, 100);
		Pageable pageable = PageRequest.of(0, safeLimit);

		List<GlobalCommunityMessageEntity> entities;
		if (afterId != null && afterId > 0) {
			// Polling newer messages: returns ASC order
			entities = messageRepository.findNewerActive(afterId, pageable);
		} else if (beforeId != null && beforeId > 0) {
			// Older messages pagination: returns DESC order, reverse to chronological ASC
			List<GlobalCommunityMessageEntity> desc = messageRepository.findOlderActive(beforeId, pageable);
			entities = new ArrayList<>(desc);
			Collections.reverse(entities);
		} else {
			// Initial load: fetch latest messages, reverse to chronological ASC
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
		UserEntity user = userRepository.findById(userId)
				.orElseThrow(() -> new NotFoundException("User not found: " + userId));

		if (!user.isEnabled()) {
			throw new ForbiddenException("Your account is disabled.");
		}

		// Enforce community participation block
		if (blockRepository.isUserBlocked(userId)) {
			throw new ForbiddenException("You are blocked from participating in the global community.");
		}

		String trimmedContent = request.content() != null ? request.content().trim() : "";
		if (trimmedContent.isEmpty()) {
			throw new BadRequestException("Message content cannot be blank.");
		}

		GlobalCommunityMessageEntity message = new GlobalCommunityMessageEntity(user, trimmedContent);
		GlobalCommunityMessageEntity saved = messageRepository.save(message);

		auditService.recordBestEffort(new AuditEvent(
				userId,
				AuditAction.COMMUNITY_MESSAGE_CREATED,
				"GLOBAL_COMMUNITY_MESSAGE",
				Long.toString(saved.getId()),
				Map.of("authorId", userId, "messageLength", trimmedContent.length())));

		log.info("User userId={} posted global community message id={}", userId, saved.getId());
		GlobalCommunityMessageDto dto = toDto(saved, userId, user.getRole() == UserRole.ADMIN);
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

		boolean isAuthor = message.getUser().getId().equals(currentUserId);
		if (!isAdmin && !isAuthor) {
			throw new ForbiddenException("You do not have permission to delete this message.");
		}

		UserEntity deletedByUser = userRepository.findById(currentUserId)
				.orElseThrow(() -> new NotFoundException("User not found: " + currentUserId));

		String reason = request != null && request.reason() != null && !request.reason().isBlank()
				? request.reason().trim()
				: (isAuthor ? "Deleted by author" : "Deleted by administrator");

		message.setDeletedAt(Instant.now());
		message.setDeletedBy(deletedByUser);
		message.setDeleteReason(reason);
		messageRepository.save(message);

		auditService.record(new AuditEvent(
				currentUserId,
				AuditAction.COMMUNITY_MESSAGE_DELETED,
				"GLOBAL_COMMUNITY_MESSAGE",
				Long.toString(messageId),
				Map.of(
						"deletedBy", currentUserId,
						"authorId", message.getUser().getId(),
						"isAdmin", isAdmin,
						"reason", reason)));

		log.info("Global community message id={} deleted by userId={} isAdmin={} reason={}",
				messageId, currentUserId, isAdmin, reason);

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
		int safeSize = Math.clamp(size > 0 ? size : 20, 1, 100);
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

		UserEntity targetUser = userRepository.findById(targetUserId)
				.orElseThrow(() -> new NotFoundException("User not found: " + targetUserId));

		if (targetUser.getRole() == UserRole.ADMIN) {
			throw new BadRequestException("Cannot block administrator accounts from the community.");
		}

		if (blockRepository.isUserBlocked(targetUserId)) {
			throw new ConflictException("User is already blocked from the global community.");
		}

		UserEntity adminUser = userRepository.findById(adminUserId)
				.orElseThrow(() -> new NotFoundException("Admin user not found: " + adminUserId));

		String reason = request != null && request.reason() != null ? request.reason().trim() : "Violated community guidelines";
		GlobalCommunityBlockEntity block = new GlobalCommunityBlockEntity(targetUser, adminUser, reason);
		GlobalCommunityBlockEntity saved = blockRepository.save(block);

		auditService.record(new AuditEvent(
				adminUserId,
				AuditAction.COMMUNITY_USER_BLOCKED,
				"USER_COMMUNITY_BLOCK",
				Long.toString(targetUserId),
				Map.of(
						"targetUserId", targetUserId,
						"targetEmail", targetUser.getEmail(),
						"reason", reason)));

		log.info("Admin userId={} blocked targetUserId={} from community. reason={}",
				adminUserId, targetUserId, reason);

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

		UserEntity adminUser = userRepository.findById(adminUserId)
				.orElseThrow(() -> new NotFoundException("Admin user not found: " + adminUserId));

		String reason = request != null && request.reason() != null ? request.reason().trim() : "Administrative unblock";
		activeBlock.setUnblockedAt(Instant.now());
		activeBlock.setUnblockedBy(adminUser);
		activeBlock.setUnblockReason(reason);
		blockRepository.save(activeBlock);

		auditService.record(new AuditEvent(
				adminUserId,
				AuditAction.COMMUNITY_USER_UNBLOCKED,
				"USER_COMMUNITY_BLOCK",
				Long.toString(targetUserId),
				Map.of(
						"targetUserId", targetUserId,
						"targetEmail", activeBlock.getUser().getEmail(),
						"reason", reason)));

		log.info("Admin userId={} unblocked targetUserId={} from community. reason={}",
				adminUserId, targetUserId, reason);

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
		UserEntity author = entity.getUser();
		boolean isOwn = currentUserId != null && currentUserId.equals(author.getId());
		boolean canDelete = isOwn || isAdmin;

		return new GlobalCommunityMessageDto(
				entity.getId(),
				author.getId(),
				author.getDisplayName(),
				author.getUsername(),
				author.getRole(),
				author.getLastMode(),
				entity.getContent(),
				entity.getCreatedAt(),
				isOwn,
				canDelete);
	}

	private AdminGlobalCommunityMessageDto toAdminDto(GlobalCommunityMessageEntity entity) {
		UserEntity author = entity.getUser();
		UserEntity deletedBy = entity.getDeletedBy();

		return new AdminGlobalCommunityMessageDto(
				entity.getId(),
				author.getId(),
				author.getDisplayName(),
				author.getEmail(),
				author.getUsername(),
				author.getRole(),
				author.getLastMode(),
				entity.getContent(),
				entity.getCreatedAt(),
				entity.getUpdatedAt(),
				entity.isDeleted(),
				entity.getDeletedAt(),
				deletedBy != null ? deletedBy.getId() : null,
				deletedBy != null ? deletedBy.getDisplayName() : null,
				entity.getDeleteReason());
	}

	private AdminCommunityBlockedUserDto toBlockedDto(GlobalCommunityBlockEntity block) {
		UserEntity target = block.getUser();
		UserEntity admin = block.getBlockedBy();

		return new AdminCommunityBlockedUserDto(
				block.getId(),
				target.getId(),
				target.getDisplayName(),
				target.getEmail(),
				target.getUsername(),
				target.getRole(),
				admin.getId(),
				admin.getDisplayName(),
				block.getReason(),
				block.getCreatedAt());
	}

	private void broadcastGlobal(CommunityRealtimeEvent<?> event) {
		messagingTemplate.convertAndSend("/topic/community/global", event);
		messagingTemplate.convertAndSend("/topic/community.global", event);
	}
}
